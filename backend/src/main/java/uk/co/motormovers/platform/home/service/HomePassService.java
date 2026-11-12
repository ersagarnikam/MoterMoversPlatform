package uk.co.motormovers.platform.home.service;

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
import uk.co.motormovers.platform.home.model.HomePassReminder;
import uk.co.motormovers.platform.home.repository.FamilyMemberRepository;
import uk.co.motormovers.platform.home.repository.HomePassPriceRepository;
import uk.co.motormovers.platform.home.repository.HomePassReminderRepository;
import uk.co.motormovers.platform.home.repository.HomePassRepository;
import uk.co.motormovers.platform.notification.service.PushService;
import uk.co.motormovers.platform.payment.model.Payment;
import uk.co.motormovers.platform.payment.model.PaymentEvents;
import uk.co.motormovers.platform.payment.model.PaymentPurpose;
import uk.co.motormovers.platform.payment.model.PaymentStatus;
import uk.co.motormovers.platform.payment.repository.PaymentRepository;
import uk.co.motormovers.platform.payment.service.OnlinePaymentService;
import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.security.service.AccessPolicy;
import uk.co.motormovers.platform.security.service.CurrentUserResolver;
import uk.co.motormovers.platform.training.service.InductionService;
import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.repository.AppUserRepository;

/**
 * The Motor Movers at Home pass (go-live task 26): twelve months for the whole family, bought by card. It runs from
 * the day it is paid; bought again while it runs, it carries on from its end. The one place that decides whether a
 * family may do sessions: a running pass and, for each carer, the parent induction.
 */
@Service
public class HomePassService {

    private static final Logger log = LoggerFactory.getLogger(HomePassService.class);

    public static final String DESCRIPTION = "Motor Movers at Home: 12-month family pass";

    /** Reminders before the end: 30 days, 7 days and on the day. */
    static final List<Integer> REMIND_DAYS_BEFORE = List.of(30, 7, 0);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.UK);

    private final HomePassRepository passes;
    private final HomePassPriceRepository prices;
    private final HomePassReminderRepository reminders;
    private final FamilyMemberRepository members;
    private final AppUserRepository users;
    private final PaymentRepository payments;
    private final OnlinePaymentService onlinePayments;
    private final InductionService induction;
    private final CurrentUserResolver currentUser;
    private final AccessPolicy access;
    private final CountrySettings country;
    private final Mailer mailer;
    private final PushService push;
    private final Money money;
    private final Clock clock;

    public HomePassService(HomePassRepository passes, HomePassPriceRepository prices,
            HomePassReminderRepository reminders, FamilyMemberRepository members, AppUserRepository users,
            PaymentRepository payments, OnlinePaymentService onlinePayments, InductionService induction,
            CurrentUserResolver currentUser, AccessPolicy access, CountrySettings country, Mailer mailer,
            PushService push, Money money, Clock clock) {
        this.passes = passes;
        this.prices = prices;
        this.reminders = reminders;
        this.members = members;
        this.users = users;
        this.payments = payments;
        this.onlinePayments = onlinePayments;
        this.induction = induction;
        this.currentUser = currentUser;
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
        if (!induction.isComplete(parent)) {
            throw new IllegalStateException("Please finish the short parent induction before your first session");
        }
        if (child != null && child.isAtHome() && !running(child.getFamilyId())) {
            LocalDate ended = endsOn(child.getFamilyId());
            throw new IllegalStateException(ended == null
                    ? "Sessions need a Motor Movers at Home pass. Please buy one for your family first."
                    : "Your family's pass ended on " + DAY.format(ended) + ". Please renew it to carry on.");
        }
    }

    /** Whether an uploaded home film may be shown to a parent in a family at home. */
    public boolean familyRunning(CurrentUser parent) {
        return members.findByUserId(parent.id()).map(m -> running(m.getFamilyId())).orElse(false);
    }

    // --- The family's pass, and buying it ------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public HomePassViews.Pass mine() {
        CurrentUser me = currentUser.current();
        Optional<Long> family = members.findByUserId(me.id()).map(FamilyMember::getFamilyId);
        Integer price = priceToday();
        LocalDate end = family.map(this::endsOn).orElse(null);
        boolean running = family.map(this::running).orElse(false);
        return new HomePassViews.Pass(family.isPresent(), price != null, price, onlinePayments.enabled(), running, end,
                !running && end != null && end.isBefore(today()));
    }

    /**
     * Opens a Stripe page for a 12-month pass for the parent's family, at today's price. A pass started but not paid
     * is reused, so pressing Buy twice never makes two payments.
     */
    @Transactional
    public HomePassViews.Checkout checkout() {
        CurrentUser me = currentUser.current();
        FamilyMember member = members.findByUserId(me.id())
                .orElseThrow(() -> new IllegalStateException("Add a child first, then buy the pass for your family"));
        Integer price = priceToday();
        if (price == null) {
            throw new IllegalStateException("The Motor Movers at Home pass is not on sale yet");
        }
        Payment payment = passes.findByFamilyIdOrderById(member.getFamilyId()).stream()
                .filter(p -> !p.started() && p.getPaymentId() != null)
                .map(p -> payments.findById(p.getPaymentId()).orElse(null))
                .filter(p -> p != null && p.getStatus() == PaymentStatus.DUE && p.getPayer() != null
                        && p.getPayer().getId().equals(me.id()) && p.getAmountPence() == price)
                .findFirst()
                .orElseGet(() -> newPayment(me, member.getFamilyId(), price));
        return new HomePassViews.Checkout(onlinePayments.checkout(payment.getId()));
    }

    private Payment newPayment(CurrentUser me, Long familyId, int price) {
        AppUser payer = users.findById(me.id()).orElseThrow();
        Payment payment = Payment.byPerson(payer, PaymentPurpose.HOME_PASS, DESCRIPTION, price, today(), null,
                me.email(), clock.instant());
        payment.includesTaxAt(country.taxRateBp());
        payment = payments.save(payment);
        passes.save(new HomePass(familyId, payment.getId(), me.email(), clock.instant(), null));
        return payment;
    }

    /**
     * A pass payment is paid (Stripe's webhook, or the team by hand): twelve months from the day it was paid, or from
     * the end of the pass running then. A pass payment the team recorded for a parent counts for their family.
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
        LocalDate end = latestEnd(passes.findByFamilyIdOrderById(pass.getFamilyId()));
        LocalDate from = end != null && !end.isBefore(paidOn) ? end.plusDays(1) : paidOn;
        passes.save(pass.runs(from, from.plusYears(1).minusDays(1)));
        log.info("Home pass for family {} runs from {} to {}", pass.getFamilyId(), from, pass.getEndsOn());
    }

    /** A pass refunded in full ends on the day of the refund; a partial refund leaves it running. */
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
        });
    }

    // --- The team -----------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public HomePassViews.FamilyPass family(String email) {
        access.requireAdmin();
        return view(familyOf(email));
    }

    /** Extends a family's pass to a later date by hand (support), recorded with who did it. */
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
        LocalDate from = end != null && !end.isBefore(today) ? end.plusDays(1) : today;
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
     * Daily: each family whose pass ends within 30 days, within 7 days, or today hears once for each, by email and
     * (when push is on) on their phones. A pass renewed meanwhile has a new end date, so the reminders start again.
     * Returns the number of families reminded.
     */
    @Scheduled(cron = "0 15 8 * * *")
    @Transactional
    public int remindEnding() {
        LocalDate today = today();
        Map<Long, LocalDate> ends = passes.findByStartsOnIsNotNull().stream()
                .collect(Collectors.groupingBy(HomePass::getFamilyId,
                        Collectors.collectingAndThen(Collectors.toList(), HomePassService::latestEnd)));
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
        String subject = "Your Motor Movers at Home pass " + when;
        Integer price = priceToday();
        String body = "Your family's Motor Movers at Home pass " + when + ". After that, your children's records stay "
                + "safe, but sessions stop until the pass is renewed.\n\n"
                + (price == null ? "" : "Renewing costs " + money.format(price) + " for another 12 months. ")
                + "Renew from your children's page: " + mailer.link("/home") + "\n";
        List<FamilyMember> family = members.findByFamilyIdOrderByJoinedAt(familyId);
        for (FamilyMember m : family) {
            users.findById(m.getUserId()).ifPresent(u -> mailer.send(u.getEmail(), subject, body));
        }
        push.notify(family.stream().map(FamilyMember::getUserId).toList(), subject,
                "Renew it to carry on with your sessions.", "/");
    }

    // --- Helpers ------------------------------------------------------------------------------------------------------

    /** The pass price today, or null while it is not on sale. */
    public Integer priceToday() {
        return prices.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(today())
                .map(HomePassPrice::getPricePence).orElse(null);
    }

    private static LocalDate latestEnd(List<HomePass> list) {
        return list.stream().filter(HomePass::started).map(HomePass::getEndsOn).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
    }

    private Long familyOf(String email) {
        AppUser user = users.findByEmailIgnoreCase(email == null ? "" : email.strip())
                .orElseThrow(() -> new NotFoundException("Account", email));
        return members.findByUserId(user.getId()).map(FamilyMember::getFamilyId)
                .orElseThrow(() -> new IllegalArgumentException("That account has no family on Motor Movers at Home"));
    }

    private HomePassViews.FamilyPass view(Long familyId) {
        List<HomePassViews.Member> people = members.findByFamilyIdOrderByJoinedAt(familyId).stream()
                .map(m -> users.findById(m.getUserId())
                        .map(u -> new HomePassViews.Member(u.getDisplayName(), u.getEmail(),
                                m.getRole() == FamilyMember.Role.OWNER ? "Owner" : "Carer"))
                        .orElse(null))
                .filter(Objects::nonNull).toList();
        List<HomePassViews.Entry> history = passes.findByFamilyIdOrderById(familyId).stream()
                .filter(HomePass::started)
                .map(p -> new HomePassViews.Entry(p.getStartsOn(), p.getEndsOn(), p.getPaymentId(),
                        p.getPaymentId() == null ? "Extended by the team" : "Paid", p.getAddedBy(), p.getAddedAt(),
                        p.getNote()))
                .toList();
        return new HomePassViews.FamilyPass(familyId, people, running(familyId), endsOn(familyId), history);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
