package uk.co.motormovers.platform.home.service;

import uk.co.motormovers.platform.common.Brand;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.child.model.Child;
import uk.co.motormovers.platform.common.config.CountrySettings;
import uk.co.motormovers.platform.common.config.Mailer;
import uk.co.motormovers.platform.common.exception.NotFoundException;
import uk.co.motormovers.platform.common.util.Money;
import uk.co.motormovers.platform.home.dto.HomePassViews;
import uk.co.motormovers.platform.home.model.FamilyMember;
import uk.co.motormovers.platform.home.model.HomePass;
import uk.co.motormovers.platform.home.model.HomePassPrice;
import uk.co.motormovers.platform.home.model.HomeMonthlyPrice;
import uk.co.motormovers.platform.home.model.HomePassReminder;
import uk.co.motormovers.platform.home.model.HomeSubscription;
import uk.co.motormovers.platform.home.repository.FamilyMemberRepository;
import uk.co.motormovers.platform.home.repository.HomeMonthlyPriceRepository;
import uk.co.motormovers.platform.home.repository.HomePassPriceRepository;
import uk.co.motormovers.platform.home.repository.HomePassReminderRepository;
import uk.co.motormovers.platform.home.repository.HomePassRepository;
import uk.co.motormovers.platform.home.repository.HomeSubscriptionRepository;
import uk.co.motormovers.platform.notification.service.PushService;
import uk.co.motormovers.platform.payment.model.Payment;
import uk.co.motormovers.platform.payment.model.PaymentEvents;
import uk.co.motormovers.platform.payment.model.PaymentPurpose;
import uk.co.motormovers.platform.payment.repository.PaymentRepository;
import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.security.service.AccessPolicy;
import uk.co.motormovers.platform.training.service.InductionService;
import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.repository.AppUserRepository;

/**
 * A family's cover on the Programme at Home, and the one place that decides whether it may do sessions: cover today
 * and, for each carer, the parent induction. Cover is the family's {@link HomePass} rows: a month for each payment of
 * its subscription (go-live task 68, {@link HomeSubscriptionService}), a 12-month pass bought before monthly billing
 * (task 26; no longer sold, but a pass the team records by hand still runs twelve months), or an extension by the
 * team.
 */
@Service
public class HomePassService {

    private static final Logger log = LoggerFactory.getLogger(HomePassService.class);

    public static final String DESCRIPTION = "The " + Brand.HOME + ": 12-month family pass";

    /** Reminders before the end: 30 days, 7 days and on the day. */
    static final List<Integer> REMIND_DAYS_BEFORE = List.of(30, 7, 0);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.UK);

    private final HomePassRepository passes;
    private final HomePassPriceRepository prices;
    private final HomePassReminderRepository reminders;
    private final FamilyMemberRepository members;
    private final AppUserRepository users;
    private final PaymentRepository payments;
    private final HomeSubscriptionRepository subscriptions;
    private final HomeMonthlyPriceRepository monthlyPrices;
    private final InductionService induction;
    private final AccessPolicy access;
    private final CountrySettings country;
    private final Mailer mailer;
    private final PushService push;
    private final Money money;
    private final Clock clock;

    public HomePassService(HomePassRepository passes, HomePassPriceRepository prices,
            HomePassReminderRepository reminders, FamilyMemberRepository members, AppUserRepository users,
            PaymentRepository payments, HomeSubscriptionRepository subscriptions,
            HomeMonthlyPriceRepository monthlyPrices, InductionService induction, AccessPolicy access,
            CountrySettings country, Mailer mailer, PushService push, Money money, Clock clock) {
        this.passes = passes;
        this.prices = prices;
        this.reminders = reminders;
        this.members = members;
        this.users = users;
        this.payments = payments;
        this.subscriptions = subscriptions;
        this.monthlyPrices = monthlyPrices;
        this.induction = induction;
        this.access = access;
        this.country = country;
        this.mailer = mailer;
        this.push = push;
        this.money = money;
        this.clock = clock;
    }

    // --- Whether a family may do sessions -----------------------------------------------------------------------------

    public boolean running(Long familyId) {
        LocalDate today = today();
        return familyId != null && passes.findByFamilyIdOrderById(familyId).stream().anyMatch(p -> p.covers(today));
    }

    /** Whether a pass covered the family on that day. */
    public boolean coveredOn(Long familyId, LocalDate day) {
        return familyId != null && passes.findByFamilyIdOrderById(familyId).stream().anyMatch(p -> p.covers(day));
    }

    /** The last day of the family's pass, or null if it never had one. */
    public LocalDate endsOn(Long familyId) {
        return familyId == null ? null : latestEnd(passes.findByFamilyIdOrderById(familyId));
    }

    /**
     * Before a session, the starting check or a rating: the parent induction (each carer does their own), then, for a
     * child at home, the family's running pass. A child linked to a school needs no pass: the school's licence covers
     * the programme.
     */
    public void requireReady(CurrentUser parent, Child child) {
        requireReady(parent, child, null);
    }

    /**
     * As {@link #requireReady(CurrentUser, Child)}, for something done on {@code doneOn}, typically saved on a phone
     * while offline and sent later: accepted if a pass covered the family that day, even if it has since ended. Today,
     * a day after today, or null needs a pass running today, as before. The induction is always needed.
     */
    public void requireReady(CurrentUser parent, Child child, LocalDate doneOn) {
        if (!induction.isComplete(parent)) {
            throw new IllegalStateException("Please finish the short parent induction before your first session");
        }
        if (child != null && child.isAtHome() && doneOn != null && doneOn.isBefore(today())
                && coveredOn(child.getFamilyId(), doneOn)) {
            return;
        }
        if (child != null && child.isAtHome() && !running(child.getFamilyId())) {
            LocalDate ended = endsOn(child.getFamilyId());
            throw new IllegalStateException(ended == null
                    ? "Sessions need your family's subscription to the " + Brand.HOME
                            + ". Please subscribe on the website first."
                    : "Your family's subscription or pass ended on " + DAY.format(ended)
                            + ". Please subscribe on the website to carry on.");
        }
    }

    /** Whether an uploaded home film may be shown to a parent in a family at home. */
    public boolean familyRunning(CurrentUser parent) {
        return members.findByUserId(parent.id()).map(m -> running(m.getFamilyId())).orElse(false);
    }

    // --- Twelve-month passes (no longer sold, task 68) and refunds ---------------------------------------------------

    /**
     * A pass payment is paid (the team by hand, or Stripe's webhook for a pass bought before monthly billing): twelve
     * months from the day it was paid, or from the end of the cover running then. A pass payment the team recorded
     * for a parent counts for their family. A month of a subscription never comes this way: it is recorded already
     * paid, with its own dates (HomeSubscriptionService).
     */
    @EventListener
    public void paid(PaymentEvents.Paid event) {
        Payment payment = payments.findById(event.paymentId()).orElse(null);
        if (payment == null || payment.getPurpose() != PaymentPurpose.HOME_PASS || payment.getPayer() == null) {
            return;
        }
        HomePass pass = passes.findByPaymentId(payment.getId()).orElseGet(() -> members
                .findByUserId(payment.getPayer().getId())
                .map(m -> new HomePass(m.getFamilyId(), payment.getId(), payment.getRecordedBy(), clock.instant(),
                        null))
                .orElse(null));
        if (pass == null) {
            log.warn("Pass payment {} is paid, but its payer has no family at home", payment.getId());
            return;
        }
        if (pass.started()) {
            return;
        }
        LocalDate paidOn = payment.getPaidOn();
        LocalDate runsTo = runsUntil(passes.findByFamilyIdOrderById(pass.getFamilyId()), paidOn);
        LocalDate from = runsTo != null ? runsTo.plusDays(1) : paidOn;
        passes.save(pass.runs(from, from.plusYears(1).minusDays(1)));
        log.info("Home pass for family {} runs from {} to {}", pass.getFamilyId(), from, pass.getEndsOn());
    }

    /**
     * A pass, or a month of a subscription, refunded in full ends on the day of the refund (the subscription itself
     * is not cancelled by a refund); a partial refund leaves it running. Parts of the family's
     * pass queued after it (a renewal bought early, or an extension) move forward to follow on with no gap, each
     * keeping its length (task 82), so the family is never left without a pass between the refund and a renewal.
     */
    @EventListener
    public void refunded(PaymentEvents.Refunded event) {
        Payment payment = payments.findById(event.paymentId()).orElse(null);
        if (payment == null || payment.getPurpose() != PaymentPurpose.HOME_PASS || payment.netPaidPence() > 0) {
            return;
        }
        passes.findByPaymentId(payment.getId()).filter(HomePass::started).ifPresent(pass -> {
            LocalDate on = event.refundedOn();
            if (pass.getStartsOn().isAfter(on)) {
                pass.runs(pass.getStartsOn(), pass.getStartsOn().minusDays(1));
            } else if (pass.getEndsOn().isAfter(on)) {
                pass.runs(pass.getStartsOn(), on);
            }
            passes.save(pass);
            closeGaps(pass.getFamilyId(), on);
        });
    }

    /**
     * Moves the family's parts that start after {@code from} forward, in order, so each starts the day after the one
     * before ends (or on {@code from} when nothing covers the days before), keeping its length. Parts begun by
     * {@code from} are left as they are: the past is never rewritten.
     */
    private void closeGaps(Long familyId, LocalDate from) {
        List<HomePass> all = passes.findByFamilyIdOrderById(familyId).stream()
                .filter(p -> p.started() && !p.getEndsOn().isBefore(p.getStartsOn()))
                .sorted(Comparator.comparing(HomePass::getStartsOn).thenComparing(HomePass::getId))
                .toList();
        LocalDate covered = null;
        for (HomePass p : all) {
            if (p.getStartsOn().isAfter(from)) {
                LocalDate start = covered == null || covered.isBefore(from) ? from : covered.plusDays(1);
                if (start.isBefore(p.getStartsOn())) {
                    long length = ChronoUnit.DAYS.between(p.getStartsOn(), p.getEndsOn());
                    passes.save(p.runs(start, start.plusDays(length)));
                    log.info("Home pass part {} of family {} moved forward to {} after a refund", p.getId(), familyId,
                            start);
                }
            }
            covered = covered == null || p.getEndsOn().isAfter(covered) ? p.getEndsOn() : covered;
        }
    }

    // --- The team -----------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public HomePassViews.FamilyPass family(String email) {
        access.requireAdmin();
        AppUser user = users.findByEmailIgnoreCase(email == null ? "" : email.strip())
                .orElseThrow(() -> new NotFoundException("Account", email));
        if (members.findByUserId(user.getId()).isEmpty()) {
            // Their family was deleted when its last member left: what they paid for is kept (task 88).
            List<HomeSubscription> kept = subscriptions.findByPayerIdAndFamilyIdIsNullOrderById(user.getId()).stream()
                    .filter(s -> s.getStatus() != HomeSubscription.Status.PENDING).toList();
            if (!kept.isEmpty()) {
                HomeSubscription last = kept.getLast();
                return new HomePassViews.FamilyPass(null, List.of(), false, last.getPaidTo(), List.of(),
                        new HomePassViews.Subscription("ENDED", last.getPricePence(), user.getEmail(),
                                last.getPaidTo(), last.getCreatedAt(), last.getEndedAt(), last.getCancelledBy(),
                                last.getCancelNote()));
            }
        }
        return view(familyOf(email));
    }

    /** Extends a family's cover to a later date by hand (support), recorded with who did it. */
    @Transactional
    public HomePassViews.FamilyPass extend(HomePassViews.Extend request) {
        CurrentUser me = access.requireAdmin();
        Long familyId = familyOf(request.email());
        LocalDate today = today();
        LocalDate end = endsOn(familyId);
        if (request.endsOn().isBefore(today)) {
            throw new IllegalArgumentException("Choose an end date from today on");
        }
        if (end != null && !request.endsOn().isAfter(end)) {
            throw new IllegalArgumentException("The pass already runs to " + DAY.format(end));
        }
        LocalDate runsTo = runsUntil(passes.findByFamilyIdOrderById(familyId), today);
        LocalDate from = runsTo != null ? runsTo.plusDays(1) : today;
        String note = request.note() == null || request.note().isBlank() ? null : request.note().strip();
        passes.save(new HomePass(familyId, null, me.email(), clock.instant(), note).runs(from, request.endsOn()));
        return view(familyId);
    }

    @Transactional(readOnly = true)
    public HomePassViews.Prices prices() {
        access.requireAdmin();
        return new HomePassViews.Prices(priceToday(), prices.findAllByOrderByEffectiveFromDesc().stream()
                .map(p -> new HomePassViews.Price(p.getPricePence(), p.getEffectiveFrom(), p.getChangedBy(),
                        p.getChangedAt()))
                .toList());
    }

    /** As for licence plans: from today or later, and a price already in force is never rewritten. */
    @Transactional
    public HomePassViews.Prices changePrice(HomePassViews.PriceChange change) {
        CurrentUser me = access.requireAdmin();
        LocalDate today = today();
        if (change.effectiveFrom().isBefore(today)) {
            throw new IllegalArgumentException("A price change cannot start in the past");
        }
        Optional<HomePassPrice> existing = prices.findByEffectiveFrom(change.effectiveFrom());
        if (existing.isPresent()) {
            if (!change.effectiveFrom().isAfter(today)) {
                throw new IllegalArgumentException("A price from that date is already in force. "
                        + "Set the change from tomorrow or later.");
            }
            existing.get().revise(change.pricePence(), me.email(), clock.instant());
        } else {
            prices.save(new HomePassPrice(change.pricePence(), change.effectiveFrom(), me.email(), clock.instant()));
        }
        return prices();
    }

    // --- Reminders ----------------------------------------------------------------------------------------------------

    /**
     * Daily: each family whose pass or extension ends within 30 days, within 7 days, or today hears once for each, by
     * email and (when push is on) on their phones. Cover that ends later has a new end date, so the reminders start
     * again. A family whose last cover is a month of a subscription hears nothing here: the subscription renews by
     * itself, and when it is cancelled the family is told the last day at once (task 68). Returns the number of
     * families reminded.
     */
    @Scheduled(cron = "0 15 8 * * *")
    @Transactional
    public int remindEnding() {
        LocalDate today = today();
        Map<Long, LocalDate> ends = passes.findByStartsOnIsNotNull().stream()
                .collect(Collectors.groupingBy(HomePass::getFamilyId)).entrySet().stream()
                .filter(family -> !lastIsSubscription(family.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, family -> latestEnd(family.getValue())));
        int reminded = 0;
        for (Map.Entry<Long, LocalDate> family : ends.entrySet()) {
            LocalDate end = family.getValue();
            long left = ChronoUnit.DAYS.between(today, end);
            Integer due = REMIND_DAYS_BEFORE.stream().filter(d -> left <= d).min(Comparator.naturalOrder())
                    .orElse(null);
            if (due == null || left < 0
                    || reminders.existsByFamilyIdAndEndsOnAndReminderDays(family.getKey(), end, due)) {
                continue;
            }
            remind(family.getKey(), end, left);
            reminders.save(new HomePassReminder(family.getKey(), end, due, today));
            reminded++;
        }
        return reminded;
    }

    private void remind(Long familyId, LocalDate end, long left) {
        String when = left == 0 ? "ends today" : "ends on " + DAY.format(end);
        String subject = "Your " + Brand.HOME + " pass " + when;
        Integer price = monthlyPrices.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(today())
                .map(HomeMonthlyPrice::getPricePence).orElse(null);
        String body = "Your family's " + Brand.HOME + " pass " + when + ". After that, your children's records stay "
                + "safe, but sessions stop until your family subscribes.\n\n"
                + (price == null ? "" : "The " + Brand.HOME + " is now paid monthly: " + money.format(price)
                        + " a month, and you can cancel at any time. ")
                + "Subscribe from your children's page, from the day your pass ends: " + mailer.link("/home") + "\n";
        List<FamilyMember> family = members.findByFamilyIdOrderByJoinedAt(familyId);
        for (FamilyMember m : family) {
            users.findById(m.getUserId()).ifPresent(u -> mailer.send(u.getEmail(), subject, body));
        }
        push.notify(family.stream().map(FamilyMember::getUserId).toList(), subject,
                "Sign in on the website to carry on with your sessions.", "/");
    }

    // --- Helpers ------------------------------------------------------------------------------------------------------

    /** The 12-month pass price today (what a pass the team records by hand costs), or null with none. */
    public Integer priceToday() {
        return prices.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(today())
                .map(HomePassPrice::getPricePence).orElse(null);
    }

    /**
     * The last day of the unbroken run of pass days from {@code day} (parts that follow on with no gap count as one),
     * or null if no part covers that day. A new part starts the day after it, or on the day itself when null.
     */
    static LocalDate runsUntil(List<HomePass> list, LocalDate day) {
        List<HomePass> parts = list.stream().filter(HomePass::started)
                .filter(p -> !p.getEndsOn().isBefore(p.getStartsOn()))
                .sorted(Comparator.comparing(HomePass::getStartsOn)).toList();
        if (parts.stream().noneMatch(p -> p.covers(day))) {
            return null;
        }
        LocalDate to = day.minusDays(1);
        for (HomePass p : parts) {
            if (!p.getStartsOn().isAfter(to.plusDays(1)) && p.getEndsOn().isAfter(to)) {
                to = p.getEndsOn();
            }
        }
        return to;
    }

    /** Whether the part of a family's cover that ends last is a month of a subscription. */
    private static boolean lastIsSubscription(List<HomePass> list) {
        return list.stream().filter(HomePass::started).max(Comparator.comparing(HomePass::getEndsOn))
                .map(p -> p.getSubscriptionId() != null).orElse(false);
    }

    private static LocalDate latestEnd(List<HomePass> list) {
        return list.stream().filter(HomePass::started).map(HomePass::getEndsOn).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
    }

    Long familyOf(String email) {
        AppUser user = users.findByEmailIgnoreCase(email == null ? "" : email.strip())
                .orElseThrow(() -> new NotFoundException("Account", email));
        return members.findByUserId(user.getId()).map(FamilyMember::getFamilyId)
                .orElseThrow(() -> new IllegalArgumentException("That account has no family on the " + Brand.HOME));
    }

    HomePassViews.FamilyPass view(Long familyId) {
        List<HomePassViews.Member> people = members.findByFamilyIdOrderByJoinedAt(familyId).stream()
                .map(m -> users.findById(m.getUserId())
                        .map(u -> new HomePassViews.Member(u.getDisplayName(), u.getEmail(),
                                m.getRole() == FamilyMember.Role.OWNER ? "Owner" : "Carer"))
                        .orElse(null))
                .filter(Objects::nonNull).toList();
        List<HomePassViews.Entry> history = passes.findByFamilyIdOrderById(familyId).stream()
                .filter(HomePass::started)
                .map(p -> {
                    LocalDate refundedOn = refundedInFullOn(p.getPaymentId());
                    // A part refunded before it started is kept as ending the day before it starts: never shown so.
                    LocalDate end = p.getEndsOn().isBefore(p.getStartsOn()) ? null : p.getEndsOn();
                    return new HomePassViews.Entry(p.getStartsOn(), end, p.getPaymentId(),
                            p.getSubscriptionId() != null ? "Monthly payment"
                                    : p.getPaymentId() == null ? "Extended by the team"
                                            : refundedOn != null ? "Pass" : "Paid",
                            p.getAddedBy(), p.getAddedAt(), p.getNote(), refundedOn);
                })
                .toList();
        // The subscription the team would act on: the one running at Stripe, else the last that ran.
        List<HomeSubscription> started = subscriptions.findByFamilyIdOrderById(familyId).stream()
                .filter(s -> s.getStatus() != HomeSubscription.Status.PENDING).toList();
        HomeSubscription shown = started.stream().filter(s -> s.getStatus().live()).reduce((first, second) -> second)
                .orElse(started.isEmpty() ? null : started.getLast());
        HomePassViews.Subscription subscription = shown == null ? null
                : new HomePassViews.Subscription(
                        shown.getStatus().live() ? HomeSubscriptionService.state(shown) : "ENDED",
                        shown.getPricePence(),
                        users.findById(shown.getPayerId()).map(AppUser::getEmail).orElse(null),
                        passes.findByFamilyIdOrderById(familyId).stream()
                                .filter(p -> shown.getId().equals(p.getSubscriptionId()) && p.started())
                                .map(HomePass::getEndsOn).max(Comparator.naturalOrder()).orElse(null),
                        shown.getCreatedAt(), shown.getEndedAt(), shown.getCancelledBy(), shown.getCancelNote());
        return new HomePassViews.FamilyPass(familyId, people, running(familyId), endsOn(familyId), history,
                subscription);
    }

    /** The day a payment was refunded in full (its last refund), or null if it was not. */
    private LocalDate refundedInFullOn(Long paymentId) {
        if (paymentId == null) {
            return null;
        }
        return payments.findById(paymentId).filter(p -> p.netPaidPence() <= 0)
                .flatMap(p -> p.getRefunds().stream().map(r -> r.getRefundedOn()).filter(Objects::nonNull)
                        .max(Comparator.naturalOrder()))
                .orElse(null);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
