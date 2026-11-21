package uk.co.motormovers.platform.home.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;

import uk.co.motormovers.platform.common.Brand;
import uk.co.motormovers.platform.common.config.CountrySettings;
import uk.co.motormovers.platform.common.config.Mailer;
import uk.co.motormovers.platform.common.exception.SafeErrors;
import uk.co.motormovers.platform.common.util.Money;
import uk.co.motormovers.platform.home.dto.HomePassViews;
import uk.co.motormovers.platform.home.model.FamilyMember;
import uk.co.motormovers.platform.home.model.HomeMonthlyPrice;
import uk.co.motormovers.platform.home.model.HomePass;
import uk.co.motormovers.platform.home.model.HomeSubscription;
import uk.co.motormovers.platform.home.model.HomeSubscription.Status;
import uk.co.motormovers.platform.home.repository.FamilyMemberRepository;
import uk.co.motormovers.platform.home.repository.HomeMonthlyPriceRepository;
import uk.co.motormovers.platform.home.repository.HomePassRepository;
import uk.co.motormovers.platform.home.repository.HomeSubscriptionRepository;
import uk.co.motormovers.platform.notification.service.PushService;
import uk.co.motormovers.platform.payment.model.Payment;
import uk.co.motormovers.platform.payment.model.PaymentPurpose;
import uk.co.motormovers.platform.payment.model.StripeEvents;
import uk.co.motormovers.platform.payment.repository.PaymentRepository;
import uk.co.motormovers.platform.payment.stripe.StripeClient;
import uk.co.motormovers.platform.payment.stripe.StripeProperties;
import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.security.service.AccessPolicy;
import uk.co.motormovers.platform.security.service.CurrentUserResolver;
import uk.co.motormovers.platform.training.service.InductionService;
import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.repository.AppUserRepository;

/**
 * The Programme at Home, billed monthly (go-live task 68). A family subscribes on Stripe's own page; Stripe takes
 * each month and tells the platform through the signed webhook. Each month paid becomes a payment (with its tax) and
 * a {@link HomePass} row for the days it covers, so the gate on sessions ({@link HomePassService#requireReady}) is
 * the same rule as before. The payer cancels or changes the card on Stripe's own page; the platform never sees a
 * card.
 *
 * <p>A month's cover runs from the day Stripe's period starts to the day it ends, both included: Stripe takes the
 * next month about an hour after the period ends, so without that last day a family would be locked out on the
 * morning of every renewal.
 */
@Service
public class HomeSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(HomeSubscriptionService.class);

    public static final String DESCRIPTION = "The " + Brand.HOME + ": monthly subscription";

    /** How long Stripe's page for the first month is reused. Stripe closes it after 24 hours. */
    static final Duration CHECKOUT_OPEN = Duration.ofHours(23);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.UK);
    private static final DateTimeFormatter SHORT_DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK);

    private final HomeSubscriptionRepository subscriptions;
    private final HomeMonthlyPriceRepository prices;
    private final HomePassRepository passes;
    private final HomePassService passService;
    private final FamilyMemberRepository members;
    private final AppUserRepository users;
    private final PaymentRepository payments;
    private final StripeClient stripe;
    private final StripeProperties stripeProperties;
    private final InductionService induction;
    private final CurrentUserResolver currentUser;
    private final AccessPolicy access;
    private final CountrySettings country;
    private final Mailer mailer;
    private final PushService push;
    private final Money money;
    private final Clock clock;

    public HomeSubscriptionService(HomeSubscriptionRepository subscriptions, HomeMonthlyPriceRepository prices,
            HomePassRepository passes, HomePassService passService, FamilyMemberRepository members,
            AppUserRepository users, PaymentRepository payments, StripeClient stripe,
            StripeProperties stripeProperties, InductionService induction, CurrentUserResolver currentUser,
            AccessPolicy access, CountrySettings country, Mailer mailer, PushService push, Money money, Clock clock) {
        this.subscriptions = subscriptions;
        this.prices = prices;
        this.passes = passes;
        this.passService = passService;
        this.members = members;
        this.users = users;
        this.payments = payments;
        this.stripe = stripe;
        this.stripeProperties = stripeProperties;
        this.induction = induction;
        this.currentUser = currentUser;
        this.access = access;
        this.country = country;
        this.mailer = mailer;
        this.push = push;
        this.money = money;
        this.clock = clock;
    }

    // --- What a family sees, subscribing and managing ----------------------------------------------------------------

    @Transactional(readOnly = true)
    public HomePassViews.Pass mine() {
        CurrentUser me = currentUser.current();
        Optional<Long> family = members.findByUserId(me.id()).map(FamilyMember::getFamilyId);
        Integer price = priceToday();
        LocalDate end = family.map(passService::endsOn).orElse(null);
        boolean running = family.map(passService::running).orElse(false);
        HomeSubscription live = family.flatMap(this::live).orElse(null);
        String state = state(live);
        Integer shown = live != null ? Integer.valueOf(live.getPricePence()) : price;
        return new HomePassViews.Pass(family.isPresent(), price != null, shown,
                stripeProperties.enabled(), running, end, !running && end != null, induction.isComplete(me),
                country.legalEntity().contactEmail(), country.legalEntity().contactPhone(), state,
                "ACTIVE".equals(state) ? paidTo(live) : null,
                family.isPresent() && price != null && live == null && !coveredAfterToday(end),
                live != null && live.getPayerId().equals(me.id()) && live.getStripeCustomerId() != null);
    }

    /**
     * Opens Stripe's page to subscribe the parent's family at today's monthly price. One subscription per family:
     * pressing Subscribe twice opens the same page, and a family that already subscribes is told so.
     */
    @Transactional
    public HomePassViews.Checkout checkout() {
        CurrentUser me = currentUser.current();
        FamilyMember member = members.findByUserId(me.id())
                .orElseThrow(() -> new IllegalStateException("Add a child first, then subscribe for your family"));
        if (!stripeProperties.enabled()) {
            throw new IllegalStateException("Card payments are not switched on");
        }
        Long familyId = member.getFamilyId();
        // Two carers pressing Subscribe at once take turns.
        subscriptions.lockFamily(familyId);
        Integer price = priceToday();
        if (price == null) {
            throw new IllegalStateException("The " + Brand.HOME + " is not on sale yet");
        }
        Optional<HomeSubscription> live = live(familyId);
        if (live.isPresent()) {
            throw new IllegalStateException(live.get().getPayerId().equals(me.id())
                    ? "Your family already subscribes. Choose Manage to change it."
                    : "Your family already subscribes: another parent or carer pays for it.");
        }
        LocalDate end = passService.endsOn(familyId);
        if (coveredAfterToday(end)) {
            throw new IllegalStateException("Your family is covered until " + DAY.format(end)
                    + ". You can subscribe from that day.");
        }
        Instant now = clock.instant();
        for (HomeSubscription pending : subscriptions.findByFamilyIdOrderById(familyId)) {
            if (!pending.checkoutOpenAt(now)) {
                continue;
            }
            if (!pending.getPayerId().equals(me.id())) {
                throw new IllegalStateException("Another parent or carer has started subscribing for your family. "
                        + "Ask them to finish, or try again tomorrow.");
            }
            if (pending.getPricePence() == price) {
                return new HomePassViews.Checkout(pending.getCheckoutUrl());
            }
        }
        HomeSubscription row = subscriptions.save(new HomeSubscription(familyId, me.id(), price, now));
        StripeClient.Checkout page = stripe.createSubscriptionCheckout(row.getId(), familyId, me.id(), price,
                DESCRIPTION, me.email(), mailer.link("/home?subscribed=1"), mailer.link("/home?cancelled=1"));
        row.checkoutOpened(page.id(), page.url(), now.plus(CHECKOUT_OPEN));
        return new HomePassViews.Checkout(page.url());
    }

    /** Stripe's own page where the payer cancels the subscription or changes the card. */
    @Transactional(readOnly = true)
    public HomePassViews.Checkout portal() {
        CurrentUser me = currentUser.current();
        HomeSubscription live = members.findByUserId(me.id()).map(FamilyMember::getFamilyId).flatMap(this::live)
                .orElseThrow(() -> new IllegalStateException("Your family has no subscription to manage"));
        if (!live.getPayerId().equals(me.id())) {
            throw new IllegalStateException("The parent or carer who subscribed manages the subscription");
        }
        if (live.getStripeCustomerId() == null) {
            throw new IllegalStateException("The subscription is still being set up. Please try again in a minute.");
        }
        return new HomePassViews.Checkout(stripe.createPortal(live.getStripeCustomerId(), mailer.link("/home")));
    }

    // --- Stripe's messages --------------------------------------------------------------------------------------------

    /** In the webhook's transaction, once the signature is checked (OnlinePaymentService). */
    @EventListener
    public void on(StripeEvents.Received event) {
        JsonNode object = event.object();
        boolean changed = switch (event.type()) {
            case "checkout.session.completed" -> checkoutCompleted(object);
            case "invoice.paid" -> invoicePaid(object);
            case "invoice.payment_failed" -> paymentFailed(object);
            case "customer.subscription.updated" -> subscriptionUpdated(object);
            case "customer.subscription.deleted" -> subscriptionDeleted(object);
            default -> false;
        };
        if (changed) {
            event.changed();
        }
    }

    /** The payer finished Stripe's page: keep Stripe's ids. The month itself arrives as invoice.paid. */
    private boolean checkoutCompleted(JsonNode session) {
        String stripeId = id(session.path("subscription"));
        Optional<HomeSubscription> found = find(stripeId, metadataRow(session.path("metadata")))
                .or(() -> Optional.ofNullable(text(session.path("id"))).flatMap(subscriptions::findByStripeSessionId)
                        .flatMap(s -> subscriptions.findLockedById(s.getId())));
        if (found.isEmpty()) {
            return false;
        }
        found.get().knownToStripeAs(stripeId, id(session.path("customer")));
        return true;
    }

    /**
     * Stripe took a month: a payment with its tax, and cover for the days of Stripe's period. Each invoice is
     * recorded once, however often Stripe sends it.
     */
    private boolean invoicePaid(JsonNode invoice) {
        String invoiceId = text(invoice.path("id"));
        String stripeId = subscriptionOf(invoice);
        Long rowId = rowOf(invoice);
        Optional<HomeSubscription> found = find(stripeId, rowId);
        if (invoiceId == null || found.isEmpty()) {
            if (rowId != null && stripeId != null) {
                orphan(stripeId, rowId);
            }
            return false;
        }
        HomeSubscription row = found.get();
        row.knownToStripeAs(stripeId, id(invoice.path("customer")));
        if (payments.existsByStripeInvoiceId(invoiceId)) {
            return false;
        }
        String currency = text(invoice.path("currency"));
        if (currency != null && !currency.equalsIgnoreCase(money.stripeCurrency())) {
            log.warn("An invoice of home subscription {} is not in this deployment's currency: not recorded",
                    row.getId());
            return false;
        }
        LocalDate[] period = periodOf(invoice);
        if (period == null) {
            log.warn("An invoice of home subscription {} names no period: not recorded", row.getId());
            return false;
        }
        AppUser payer = users.findById(row.getPayerId()).orElseThrow();
        int amount = invoice.path("amount_paid").asInt(0);
        Long paymentId = null;
        if (amount > 0) {
            Payment payment = Payment.byPerson(payer, PaymentPurpose.HOME_PASS,
                    DESCRIPTION + ", " + SHORT_DAY.format(period[0]) + " to " + SHORT_DAY.format(period[1]), amount,
                    today(), null, payer.getEmail(), clock.instant());
            payment.includesTaxAt(country.taxRateBp());
            payment.paidByStripeInvoice(invoiceId, today(), paymentIntentOf(invoice, invoiceId));
            paymentId = payments.save(payment).getId();
        }
        passes.save(HomePass.monthOf(row.getId(), row.getFamilyId(), paymentId, payer.getEmail(), clock.instant())
                .runs(period[0], period[1]));
        boolean first = row.getStatus() == Status.PENDING;
        row.paid();
        log.info("Home subscription {} of family {}: a month paid, covered from {} to {}", row.getId(),
                row.getFamilyId(), period[0], period[1]);
        if (first) {
            endDuplicate(row);
        }
        return true;
    }

    /** A month's payment failed: the payer is told once for that invoice. Stripe tries again by itself. */
    private boolean paymentFailed(JsonNode invoice) {
        Optional<HomeSubscription> found = find(subscriptionOf(invoice), rowOf(invoice));
        if (found.isEmpty() || !found.get().getStatus().live()) {
            // The first month failing is shown on Stripe's own page, where the payer still is.
            return false;
        }
        HomeSubscription row = found.get();
        if (row.paymentFailed(text(invoice.path("id")))) {
            LocalDate paidTo = paidTo(row);
            String subject = "We could not take this month's payment for the " + Brand.HOME;
            String body = "We could not take this month's payment of " + money.format(row.getPricePence())
                    + " for your family's " + Brand.HOME + " subscription. Your bank or card may have refused it. "
                    + "We will try again over the next few days.\n\n"
                    + "To pay with another card, sign in on the website, open At home and choose Manage: "
                    + mailer.link("/home") + "\n\n"
                    + (paidTo != null && !paidTo.isBefore(today())
                            ? "Sessions stay open until " + DAY.format(paidTo) + ". After that they stop until a "
                                    + "payment goes through. "
                            : "Sessions stop until a payment goes through. ")
                    + "Your children's records are kept.\n";
            users.findById(row.getPayerId()).ifPresent(u -> mailer.send(u.getEmail(), subject, body));
            push.notify(List.of(row.getPayerId()), "This month's payment did not go through",
                    "Sign in on the website to pay with another card.", "/");
        }
        return true;
    }

    /** Stripe says the subscription changed: paid up or past due, and whether it will end with the paid month. */
    private boolean subscriptionUpdated(JsonNode subscription) {
        String stripeId = text(subscription.path("id"));
        Optional<HomeSubscription> found = find(stripeId, metadataRow(subscription.path("metadata")));
        if (found.isEmpty()) {
            return false;
        }
        HomeSubscription row = found.get();
        row.knownToStripeAs(stripeId, id(subscription.path("customer")));
        String status = subscription.path("status").asString("");
        switch (status) {
            case "canceled", "incomplete_expired" -> {
                return ended(row);
            }
            case "past_due", "unpaid", "paused" -> row.pastDue();
            case "active", "trialing" -> {
                if (row.getStatus() == Status.PAST_DUE) {
                    row.paid();
                }
            }
            default -> {
                // "incomplete": the first month is not paid yet.
            }
        }
        // Stripe's page marks a cancellation either way, depending on its version.
        boolean cancelling = subscription.path("cancel_at_period_end").asBoolean(false)
                || present(subscription.path("cancel_at"));
        if (row.getStatus().live()) {
            boolean was = row.isCancelling();
            row.cancelling(cancelling);
            if (cancelling && !was) {
                tellOfEnd(row);
            }
        }
        return true;
    }

    private boolean subscriptionDeleted(JsonNode subscription) {
        return find(text(subscription.path("id")), metadataRow(subscription.path("metadata"))).map(this::ended)
                .orElse(false);
    }

    /** Over at Stripe. What the family paid for stays; it is told the last day, once. */
    private boolean ended(HomeSubscription row) {
        if (row.getStatus() == Status.ENDED) {
            return false;
        }
        boolean wasLive = row.getStatus().live();
        row.ended(clock.instant());
        if (wasLive) {
            tellOfEnd(row);
        }
        return true;
    }

    /**
     * A page left open was paid after the family had subscribed another way: the second subscription is cancelled at
     * once. Its month is recorded like any other, for the team to refund.
     */
    private void endDuplicate(HomeSubscription row) {
        boolean another = subscriptions.findByFamilyIdOrderById(row.getFamilyId()).stream()
                .anyMatch(s -> !s.getId().equals(row.getId()) && s.getStatus().live());
        if (!another) {
            return;
        }
        log.warn("Family {} paid for a second home subscription ({}): cancelled at once, its month is to refund",
                row.getFamilyId(), row.getId());
        try {
            stripe.cancelSubscription(row.getStripeSubscriptionId(), false);
        } catch (RuntimeException e) {
            log.warn("Could not cancel the second home subscription {}: {}", row.getId(), SafeErrors.describe(e));
        }
        row.tellOfEnd();
        row.ended(clock.instant());
    }

    /** Stripe billed a subscription whose row is gone (the family was deleted with its page open): stop it. */
    private void orphan(String stripeId, Long rowId) {
        log.warn("Stripe billed home subscription {}, which no longer exists: cancelling it, its month is to refund",
                rowId);
        try {
            stripe.cancelSubscription(stripeId, false);
        } catch (RuntimeException e) {
            log.warn("Could not cancel the home subscription {} at Stripe: {}", rowId, SafeErrors.describe(e));
        }
    }

    // --- The team, and a family changing ------------------------------------------------------------------------------

    /**
     * The team cancels a family's subscription (support), recorded with who and why. The family keeps the month it
     * has paid for; a subscription whose payment has failed ends at once.
     */
    @Transactional
    public HomePassViews.FamilyPass cancel(HomePassViews.CancelSubscription request) {
        CurrentUser me = access.requireAdmin();
        Long familyId = passService.familyOf(request.email());
        HomeSubscription row = live(familyId).flatMap(s -> subscriptions.findLockedById(s.getId()))
                .orElseThrow(() -> new IllegalArgumentException("That family has no running subscription"));
        if (row.getStripeSubscriptionId() == null) {
            throw new IllegalArgumentException("Stripe has not confirmed that subscription yet. Try again in a minute.");
        }
        if (row.isCancelling()) {
            throw new IllegalArgumentException("That subscription is already cancelled: it ends with the paid month");
        }
        cancelAtStripe(row, me.email(), request.note().strip());
        return passService.view(familyId);
    }

    /**
     * Someone has left a family. If they paid for its subscription it is cancelled, so they are not charged for a
     * family they are no longer in; the others keep the month paid for. When the family itself goes (its last member
     * left), the subscription ends at once. Call it before the family's rows are deleted.
     */
    @Transactional
    public void memberLeft(Long familyId, Long userId, boolean familyGoes) {
        for (HomeSubscription s : subscriptions.findByFamilyIdOrderById(familyId)) {
            if (!s.getStatus().live() || (!familyGoes && !s.getPayerId().equals(userId))) {
                continue;
            }
            HomeSubscription row = subscriptions.findLockedById(s.getId()).orElseThrow();
            if (row.getStripeSubscriptionId() == null) {
                continue;
            }
            if (familyGoes) {
                stripe.cancelSubscription(row.getStripeSubscriptionId(), false);
                row.tellOfEnd();
                row.ended(clock.instant());
            } else if (!row.isCancelling()) {
                cancelAtStripe(row, "left the family", null);
            }
        }
    }

    private void cancelAtStripe(HomeSubscription row, String who, String note) {
        boolean atPeriodEnd = row.getStatus() == Status.ACTIVE;
        stripe.cancelSubscription(row.getStripeSubscriptionId(), atPeriodEnd);
        row.cancelledBy(who, note);
        if (atPeriodEnd) {
            row.cancelling(true);
            tellOfEnd(row);
        } else {
            ended(row);
        }
    }

    /** One email to the family, the first time it is known that the subscription will end. */
    private void tellOfEnd(HomeSubscription row) {
        if (!row.tellOfEnd()) {
            return;
        }
        LocalDate last = paidTo(row);
        boolean stillOpen = last != null && !last.isBefore(today());
        String subject = "Your " + Brand.HOME + " subscription has been cancelled";
        String body = "Your family's monthly subscription to the " + Brand.HOME + " has been cancelled. No more "
                + "payments will be taken.\n\n"
                + (stillOpen ? "Sessions stay open until " + DAY.format(last) + ", the last day already paid for. "
                        : "Sessions have stopped. ")
                + "Your children's records are kept.\n\n"
                + "To carry on after that, subscribe again from your children's page: " + mailer.link("/home") + "\n";
        for (FamilyMember m : members.findByFamilyIdOrderByJoinedAt(row.getFamilyId())) {
            users.findById(m.getUserId()).ifPresent(u -> mailer.send(u.getEmail(), subject, body));
        }
    }

    // --- The monthly price --------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public HomePassViews.Prices prices() {
        access.requireAdmin();
        return new HomePassViews.Prices(priceToday(), prices.findAllByOrderByEffectiveFromDesc().stream()
                .map(p -> new HomePassViews.Price(p.getPricePence(), p.getEffectiveFrom(), p.getChangedBy(),
                        p.getChangedAt()))
                .toList());
    }

    /**
     * As for licence plans: from today or later, and a price already in force is never rewritten. It applies to new
     * subscriptions; a family that already subscribes keeps its price.
     */
    @Transactional
    public HomePassViews.Prices changePrice(HomePassViews.PriceChange change) {
        CurrentUser me = access.requireAdmin();
        LocalDate today = today();
        if (change.effectiveFrom().isBefore(today)) {
            throw new IllegalArgumentException("A price change cannot start in the past");
        }
        Optional<HomeMonthlyPrice> existing = prices.findByEffectiveFrom(change.effectiveFrom());
        if (existing.isPresent()) {
            if (!change.effectiveFrom().isAfter(today)) {
                throw new IllegalArgumentException("A price from that date is already in force. "
                        + "Set the change from tomorrow or later.");
            }
            existing.get().revise(change.pricePence(), me.email(), clock.instant());
        } else {
            prices.save(new HomeMonthlyPrice(change.pricePence(), change.effectiveFrom(), me.email(),
                    clock.instant()));
        }
        return prices();
    }

    /** The monthly price today, or null while monthly is not on sale. */
    public Integer priceToday() {
        return prices.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(today())
                .map(HomeMonthlyPrice::getPricePence).orElse(null);
    }

    // --- Helpers ------------------------------------------------------------------------------------------------------

    /** The family's subscription running at Stripe, if any. */
    private Optional<HomeSubscription> live(Long familyId) {
        return subscriptions.findByFamilyIdOrderById(familyId).stream().filter(s -> s.getStatus().live())
                .max(Comparator.comparing(HomeSubscription::getId));
    }

    /** NONE, ACTIVE (renews), CANCELLING (ends with the paid month) or PAST_DUE (a payment failed). */
    static String state(HomeSubscription live) {
        if (live == null) {
            return "NONE";
        }
        if (live.getStatus() == Status.PAST_DUE) {
            return "PAST_DUE";
        }
        return live.isCancelling() ? "CANCELLING" : "ACTIVE";
    }

    /** The last day the subscription has paid for (the day Stripe takes the next month), or null with none paid. */
    private LocalDate paidTo(HomeSubscription row) {
        return passes.findByFamilyIdOrderById(row.getFamilyId()).stream()
                .filter(p -> row.getId().equals(p.getSubscriptionId()) && p.started()).map(HomePass::getEndsOn)
                .max(Comparator.naturalOrder()).orElse(null);
    }

    private boolean coveredAfterToday(LocalDate end) {
        return end != null && end.isAfter(today());
    }

    /** The row for one of Stripe's messages, locked: by Stripe's id, or by the row id in the metadata. */
    private Optional<HomeSubscription> find(String stripeId, Long rowId) {
        Optional<HomeSubscription> byStripe = stripeId == null ? Optional.empty()
                : subscriptions.findByStripeSubscriptionId(stripeId);
        Long id = byStripe.map(HomeSubscription::getId).orElse(rowId);
        return id == null ? Optional.empty() : subscriptions.findLockedById(id);
    }

    /** An invoice's subscription: under {@code parent} since Stripe's 2025-03-31 version, at the top before. */
    private static String subscriptionOf(JsonNode invoice) {
        String now = id(invoice.path("parent").path("subscription_details").path("subscription"));
        return now != null ? now : id(invoice.path("subscription"));
    }

    /** The platform's row named in the subscription's metadata, which Stripe copies onto each invoice. */
    private static Long rowOf(JsonNode invoice) {
        Long row = metadataRow(invoice.path("parent").path("subscription_details").path("metadata"));
        if (row == null) {
            row = metadataRow(invoice.path("subscription_details").path("metadata"));
        }
        if (row == null) {
            for (JsonNode line : invoice.path("lines").path("data")) {
                row = metadataRow(line.path("metadata"));
                if (row != null) {
                    break;
                }
            }
        }
        return row;
    }

    private static Long metadataRow(JsonNode metadata) {
        String value = text(metadata.path(StripeClient.SUBSCRIPTION_ROW));
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The days an invoice pays for: the period of its line that ends last (Stripe: "for subscription line items,
     * this is the subscription period"; the invoice's own period_start and period_end are something else). Dates
     * are the deployment's.
     */
    private LocalDate[] periodOf(JsonNode invoice) {
        long start = 0;
        long end = 0;
        for (JsonNode line : invoice.path("lines").path("data")) {
            long lineEnd = line.path("period").path("end").asLong(0);
            if (lineEnd > end) {
                end = lineEnd;
                start = line.path("period").path("start").asLong(0);
            }
        }
        if (start <= 0 || end < start) {
            return null;
        }
        return new LocalDate[] { date(start), date(end) };
    }

    private LocalDate date(long epochSecond) {
        return Instant.ofEpochSecond(epochSecond).atZone(clock.getZone()).toLocalDate();
    }

    /** What to refund against: on the invoice in Stripe's older versions, asked for in the newer ones. */
    private String paymentIntentOf(JsonNode invoice, String invoiceId) {
        String onInvoice = id(invoice.path("payment_intent"));
        if (onInvoice != null) {
            return onInvoice;
        }
        String asked = null;
        try {
            asked = stripe.invoicePaymentIntent(invoiceId);
        } catch (RuntimeException e) {
            log.warn("Could not ask Stripe which payment paid an invoice: {}", SafeErrors.describe(e));
        }
        // With neither, the invoice id: the month shows as paid by card, and a refund of it is made by hand.
        return Objects.requireNonNullElse(asked, invoiceId);
    }

    /** A value Stripe gives as an id, or as the whole object when expanded. */
    private static String id(JsonNode node) {
        return node.isObject() ? text(node.path("id")) : text(node);
    }

    private static String text(JsonNode node) {
        if (!present(node)) {
            return null;
        }
        String value = node.asString("");
        return value.isBlank() ? null : value;
    }

    private static boolean present(JsonNode node) {
        return node != null && !node.isMissingNode() && !node.isNull();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
