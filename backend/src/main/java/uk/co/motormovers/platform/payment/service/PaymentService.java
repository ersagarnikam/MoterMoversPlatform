package uk.co.motormovers.platform.payment.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.common.exception.NotFoundException;
import uk.co.motormovers.platform.payment.dto.PaymentViews;
import uk.co.motormovers.platform.payment.dto.PaymentViews.GroupTotal;
import uk.co.motormovers.platform.payment.dto.PaymentViews.PaymentView;
import uk.co.motormovers.platform.payment.dto.PaymentViews.SchoolTotal;
import uk.co.motormovers.platform.payment.model.PayerType;
import uk.co.motormovers.platform.payment.model.Payment;
import uk.co.motormovers.platform.payment.model.PaymentPurpose;
import uk.co.motormovers.platform.payment.model.PaymentStatus;
import uk.co.motormovers.platform.notification.service.PushService;
import uk.co.motormovers.platform.payment.repository.PaymentRepository;
import uk.co.motormovers.platform.school.model.School;
import uk.co.motormovers.platform.school.model.Trust;
import uk.co.motormovers.platform.school.repository.SchoolRepository;
import uk.co.motormovers.platform.school.repository.TrustRepository;
import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.security.service.AccessPolicy;
import uk.co.motormovers.platform.security.service.CurrentUserResolver;
import uk.co.motormovers.platform.user.model.AppUser;
import uk.co.motormovers.platform.user.model.Role;
import uk.co.motormovers.platform.user.repository.AppUserRepository;

/**
 * Payments the team records, and the spending summaries built from them: everything for the team, a
 * school's own payments for its head, school-by-school totals for a trust lead, and a person's own payments.
 * Online payment is not integrated; a provider could later create these same records.
 */
@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final SchoolRepository schools;
    private final TrustRepository trusts;
    private final AppUserRepository users;
    private final AccessPolicy access;
    private final CurrentUserResolver currentUser;
    private final PushService push;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, SchoolRepository schools, TrustRepository trusts,
            AppUserRepository users, AccessPolicy access, CurrentUserResolver currentUser, PushService push,
            Clock clock) {
        this.payments = payments;
        this.schools = schools;
        this.trusts = trusts;
        this.users = users;
        this.access = access;
        this.currentUser = currentUser;
        this.push = push;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PaymentView> all() {
        return payments.findAllByOrderByInvoicedOnDescIdDesc().stream().map(PaymentView::of).toList();
    }

    @Transactional(readOnly = true)
    public PaymentViews.AdminSummary summary() {
        List<Payment> all = payments.findAllByOrderByInvoicedOnDescIdDesc();
        YearMonth thisMonth = YearMonth.now(clock);
        YearMonth firstMonth = thisMonth.minusMonths(11);

        List<GroupTotal> byPayerType = Arrays.stream(PayerType.values())
                .map(t -> group(t.name(), t.label(), all, p -> p.payerType() == t))
                .toList();
        List<GroupTotal> byPurpose = Arrays.stream(PaymentPurpose.values())
                .map(t -> group(t.name(), t.label(), all, p -> p.getPurpose() == t))
                .toList();

        Map<YearMonth, Long> paidByMonth = new LinkedHashMap<>();
        for (YearMonth m = firstMonth; !m.isAfter(thisMonth); m = m.plusMonths(1)) {
            paidByMonth.put(m, 0L);
        }
        all.stream().filter(p -> p.getStatus() == PaymentStatus.PAID)
                .forEach(p -> paidByMonth.computeIfPresent(YearMonth.from(p.getPaidOn()),
                        (m, total) -> total + p.getAmountPence()));
        List<PaymentViews.MonthTotal> byMonth = paidByMonth.entrySet().stream()
                .map(e -> new PaymentViews.MonthTotal(e.getKey().toString(), e.getValue())).toList();

        List<School> allSchools = schools.findAll().stream().sorted(Comparator.comparing(School::getName)).toList();
        List<SchoolTotal> bySchool = allSchools.stream().map(s -> schoolTotal(s, all)).toList();
        List<PaymentViews.TrustTotal> byTrust = trusts.findAll().stream()
                .sorted(Comparator.comparing(Trust::getName))
                .map(t -> {
                    List<Payment> trustPaid = all.stream().filter(p -> p.getSchool() != null
                            && p.getSchool().getTrust() != null
                            && Objects.equals(p.getSchool().getTrust().getId(), t.getId())).toList();
                    int schoolCount = (int) allSchools.stream()
                            .filter(s -> s.getTrust() != null && Objects.equals(s.getTrust().getId(), t.getId()))
                            .count();
                    return new PaymentViews.TrustTotal(t.getId(), t.getName(), schoolCount, paid(trustPaid),
                            outstanding(trustPaid));
                })
                .toList();

        Map<Long, List<Payment>> byPayer = all.stream().filter(p -> p.getPayer() != null)
                .collect(Collectors.groupingBy(p -> p.getPayer().getId()));
        List<PaymentViews.PersonTotal> byPerson = byPayer.values().stream()
                .map(list -> {
                    AppUser person = list.getFirst().getPayer();
                    return new PaymentViews.PersonTotal(person.getId(), person.getDisplayName(), person.getEmail(),
                            PayerType.of(person.getRole()), paid(list), outstanding(list));
                })
                .sorted(Comparator.comparingLong(PaymentViews.PersonTotal::paidPence).reversed()
                        .thenComparing(PaymentViews.PersonTotal::name))
                .toList();

        long paidLast12Months = byMonth.stream().mapToLong(PaymentViews.MonthTotal::paidPence).sum();
        return new PaymentViews.AdminSummary(paid(all), outstanding(all), paidLast12Months, byPayerType, byPurpose,
                byMonth, bySchool, byTrust, byPerson);
    }

    @Transactional
    public PaymentView record(PaymentViews.NewPayment r) {
        boolean bySchool = r.schoolId() != null;
        boolean byPerson = r.payerEmail() != null && !r.payerEmail().isBlank();
        if (bySchool == byPerson) {
            throw new IllegalArgumentException("Choose either a school or a person as the payer");
        }
        LocalDate today = LocalDate.now(clock);
        if (r.paidOn() != null && r.paidOn().isAfter(today)) {
            throw new IllegalArgumentException("The payment date cannot be in the future");
        }
        String reference = r.reference() == null || r.reference().isBlank() ? null : r.reference().strip();
        String recordedBy = currentUser.current().email();
        Payment payment;
        if (bySchool) {
            School school = schools.findById(r.schoolId())
                    .orElseThrow(() -> new NotFoundException("School", r.schoolId()));
            payment = Payment.bySchool(school, r.purpose(), r.description().strip(), r.amountPence(), r.invoicedOn(),
                    reference, recordedBy, clock.instant());
        } else {
            AppUser payer = users.findByEmailIgnoreCase(r.payerEmail().strip())
                    .orElseThrow(() -> new IllegalArgumentException("There is no account with that email"));
            if (payer.getRole() == Role.ADMIN) {
                throw new IllegalArgumentException("Motor Movers team accounts do not pay");
            }
            payment = Payment.byPerson(payer, r.purpose(), r.description().strip(), r.amountPence(), r.invoicedOn(),
                    reference, recordedBy, clock.instant());
        }
        if (r.paidOn() != null) {
            payment.markPaid(r.paidOn());
        }
        payment = payments.save(payment);
        if (payment.getPayer() != null && payment.getStatus() == PaymentStatus.DUE) {
            // A parent with the app hears about it there too; a tap opens their Payments screen.
            push.notify(List.of(payment.getPayer().getId()), "A payment is due",
                    PaymentViews.pounds(payment.getAmountPence()) + " for " + payment.getDescription()
                            + ". Tap to see how to pay.", "/payments");
        }
        return PaymentView.of(payment);
    }

    @Transactional
    public PaymentView markPaid(Long paymentId, LocalDate paidOn) {
        if (paidOn.isAfter(LocalDate.now(clock))) {
            throw new IllegalArgumentException("The payment date cannot be in the future");
        }
        Payment payment = find(paymentId);
        payment.markPaid(paidOn);
        return PaymentView.of(payment);
    }

    @Transactional
    public PaymentView cancel(Long paymentId) {
        Payment payment = find(paymentId);
        payment.cancel();
        return PaymentView.of(payment);
    }

    @Transactional(readOnly = true)
    public PaymentViews.SchoolPayments forSchool(Long schoolId) {
        School school = schools.findById(schoolId).orElseThrow(() -> new NotFoundException("School", schoolId));
        access.requireSchoolAccess(school);
        List<Payment> list = payments.findBySchoolIdOrderByInvoicedOnDescIdDesc(schoolId);
        return new PaymentViews.SchoolPayments(school.getId(), school.getName(), paid(list), outstanding(list),
                list.stream().map(PaymentView::of).toList());
    }

    @Transactional(readOnly = true)
    public PaymentViews.TrustPayments forTrust(Long trustId) {
        access.requireTrustAccess(trustId);
        Trust trust = trusts.findById(trustId).orElseThrow(() -> new NotFoundException("Trust", trustId));
        List<School> inTrust = schools.findByTrustIdOrderByName(trustId);
        List<Payment> list = payments.findBySchoolIdIn(inTrust.stream().map(School::getId).toList());
        return new PaymentViews.TrustPayments(trust.getId(), trust.getName(), paid(list), outstanding(list),
                inTrust.stream().map(s -> schoolTotal(s, list)).toList());
    }

    @Transactional(readOnly = true)
    public PaymentViews.MyPayments mine() {
        CurrentUser me = currentUser.current();
        List<Payment> list = payments.findByPayerIdOrderByInvoicedOnDescIdDesc(me.id());
        return new PaymentViews.MyPayments(paid(list), outstanding(list), list.stream().map(PaymentView::of).toList());
    }

    private Payment find(Long id) {
        return payments.findById(id).orElseThrow(() -> new NotFoundException("Payment", id));
    }

    private static SchoolTotal schoolTotal(School s, List<Payment> from) {
        List<Payment> own = from.stream()
                .filter(p -> p.getSchool() != null && Objects.equals(p.getSchool().getId(), s.getId())).toList();
        return new SchoolTotal(s.getId(), s.getName(), s.getTrust() == null ? null : s.getTrust().getName(),
                paid(own), outstanding(own));
    }

    private static GroupTotal group(String key, String label, List<Payment> all, Predicate<Payment> filter) {
        List<Payment> matching = all.stream().filter(filter.and(p -> p.getStatus() != PaymentStatus.CANCELLED))
                .toList();
        return new GroupTotal(key, label, paid(matching), outstanding(matching), matching.size());
    }

    private static long paid(List<Payment> list) {
        return total(list, PaymentStatus.PAID);
    }

    private static long outstanding(List<Payment> list) {
        return total(list, PaymentStatus.DUE);
    }

    private static long total(List<Payment> list, PaymentStatus status) {
        return list.stream().filter(p -> p.getStatus() == status).mapToLong(Payment::getAmountPence).sum();
    }
}
