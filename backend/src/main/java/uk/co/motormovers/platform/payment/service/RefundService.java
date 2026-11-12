package uk.co.motormovers.platform.payment.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.co.motormovers.platform.common.config.CountrySettings;
import uk.co.motormovers.platform.common.config.Mailer;
import uk.co.motormovers.platform.common.exception.NotFoundException;
import uk.co.motormovers.platform.common.util.Money;
import uk.co.motormovers.platform.payment.dto.PaymentViews;
import uk.co.motormovers.platform.payment.dto.PaymentViews.PaymentView;
import uk.co.motormovers.platform.payment.model.Payment;
import uk.co.motormovers.platform.payment.model.PaymentEvents;
import uk.co.motormovers.platform.payment.model.PaymentRefund;
import uk.co.motormovers.platform.payment.model.PaymentStatus;
import uk.co.motormovers.platform.payment.repository.CreditNoteCounterRepository;
import uk.co.motormovers.platform.payment.repository.PaymentRefundRepository;
import uk.co.motormovers.platform.payment.repository.PaymentRepository;
import uk.co.motormovers.platform.payment.stripe.StripeClient;
import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.security.service.AccessPolicy;
import uk.co.motormovers.platform.security.service.CurrentUserResolver;
import uk.co.motormovers.platform.user.model.Role;
import uk.co.motormovers.platform.user.repository.AppUserRepository;

/**
 * Refunds on paid payments, whole or in part, each with a credit note. The team refunds on the platform: a card payment
 * through Stripe, anything else recorded for the money to be returned by hand. A refund made directly in Stripe arrives
 * by webhook and is recorded the same way, once. The rules on when a refund applies are the team's to apply. Every
 * route locks the payment first, so a webhook cannot record a refund the team's request is still saving.
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    /** Stripe's refund statuses that mean the money is, or is on its way, back with the payer. */
    private static final Set<String> GOING_BACK = Set.of("succeeded", "pending");

    private static final Set<String> NOT_GOING_BACK = Set.of("failed", "canceled");

    private final PaymentRepository payments;
    private final PaymentRefundRepository refunds;
    private final CreditNoteCounterRepository counter;
    private final AppUserRepository users;
    private final StripeClient stripe;
    private final AccessPolicy access;
    private final CurrentUserResolver currentUser;
    private final Mailer mailer;
    private final Money money;
    private final CountrySettings country;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public RefundService(PaymentRepository payments, PaymentRefundRepository refunds,
            CreditNoteCounterRepository counter, AppUserRepository users, StripeClient stripe, AccessPolicy access,
            CurrentUserResolver currentUser, Mailer mailer, Money money, CountrySettings country, Clock clock,
            ApplicationEventPublisher events) {
        this.payments = payments;
        this.refunds = refunds;
        this.counter = counter;
        this.users = users;
        this.stripe = stripe;
        this.access = access;
        this.currentUser = currentUser;
        this.mailer = mailer;
        this.money = money;
        this.country = country;
        this.clock = clock;
        this.events = events;
    }

    /**
     * The team refunds all or part of a paid payment. A payment taken by card goes back through Stripe, and nothing is
     * saved unless Stripe accepts; otherwise the refund is recorded and the money has to be returned by hand. Sending
     * the same {@code clientId} again does nothing more.
     */
    @Transactional
    public PaymentView refund(Long paymentId, PaymentViews.RefundIn in) {
        CurrentUser me = access.requireAdmin();
        Payment payment = payments.findLockedById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment", paymentId));
        Optional<PaymentRefund> repeat = refunds.findByClientId(in.clientId());
        if (repeat.isPresent()) {
            if (!repeat.get().getPayment().getId().equals(paymentId)) {
                throw new IllegalArgumentException("This refund request was already used on another payment");
            }
            return PaymentView.of(payment);
        }
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new IllegalStateException("Only a paid payment can be refunded");
        }
        if (in.amountPence() > payment.refundablePence()) {
            throw new IllegalArgumentException("That is more than is left to refund (" + money.format(
                    payment.refundablePence()) + ")");
        }
        String stripeRefundId = null;
        if (payment.paidWithPaymentIntent()) {
            stripeRefundId = stripe.createRefund(payment.getPaidOnlineRef(), in.amountPence(), payment.getId(),
                    "refund-" + payment.getId() + "-" + in.clientId()).id();
        }
        record(payment, in.amountPence(), in.reason().strip(), me.email(), stripeRefundId, in.clientId());
        return PaymentView.of(payment);
    }

    /**
     * Stripe reports a refund: one the team asked for here, already recorded, or one made directly in Stripe, recorded
     * now so both routes agree. Stripe may send the same refund more than once. Returns whether anything changed.
     */
    @Transactional
    public boolean fromStripe(String refundId, String paymentIntent, String paymentId, Integer amount,
            String status) {
        if (refundId == null || status == null) {
            return false;
        }
        Optional<Payment> found = paymentFor(paymentIntent, paymentId);
        if (found.isEmpty()) {
            log.warn("Stripe refund {} is for no platform payment", refundId);
            return false;
        }
        Payment payment = found.get();
        Optional<PaymentRefund> known = refunds.findByStripeRefundId(refundId);
        if (NOT_GOING_BACK.contains(status)) {
            return known.filter(PaymentRefund::counts).map(r -> {
                r.failed();
                log.warn("Stripe could not return refund {} ({}): the team must return the money another way",
                        refundId, r.creditNoteLabel());
                return true;
            }).orElse(false);
        }
        if (known.isPresent() || !GOING_BACK.contains(status) || amount == null) {
            return false;
        }
        if (amount > payment.refundablePence()) {
            // Already refunded here by hand, say: the money went back twice, which the team has to look at.
            log.error("Stripe refund {} of {} is more than is left to refund on payment {}: not recorded", refundId,
                    amount, payment.getId());
            return false;
        }
        record(payment, amount, "Refunded in Stripe", "stripe", refundId, null);
        return true;
    }

    /** The credit note, for the team, the school's head or the person who paid. */
    @Transactional(readOnly = true)
    public CreditNote creditNote(Long refundId) {
        PaymentRefund refund = visible(refundId);
        return new CreditNote(refund.creditNoteLabel() + ".pdf", pdf(refund));
    }

    /** Sends the credit note to the signed-in person, for the parent app, where it cannot be saved directly. */
    @Transactional(readOnly = true)
    public void emailCreditNote(Long refundId) {
        PaymentRefund refund = visible(refundId);
        send(currentUser.current().email(), refund);
    }

    public record CreditNote(String fileName, byte[] pdf) {
    }

    private PaymentRefund visible(Long refundId) {
        PaymentRefund refund = refunds.findById(refundId)
                .filter(PaymentRefund::counts)
                .orElseThrow(() -> new NotFoundException("Credit note", refundId));
        Payment payment = refund.getPayment();
        CurrentUser me = currentUser.current();
        if (payment.getSchool() != null) {
            access.requireSchoolAccess(payment.getSchool());
        } else if (!me.is(Role.ADMIN) && !payment.getPayer().getId().equals(me.id())) {
            throw new AccessDeniedException("You can see only your own payments");
        }
        return refund;
    }

    private Optional<Payment> paymentFor(String paymentIntent, String paymentId) {
        if (paymentId != null) {
            try {
                return payments.findLockedById(Long.valueOf(paymentId));
            } catch (NumberFormatException e) {
                log.warn("Stripe refund names payment {}, which is not a number", paymentId);
            }
        }
        return paymentIntent == null ? Optional.empty() : payments.findLockedByPaidOnlineRef(paymentIntent);
    }

    private void record(Payment payment, int amount, String reason, String recordedBy, String stripeRefundId,
            String clientId) {
        int number = counter.locked().next();
        PaymentRefund refund = refunds.save(new PaymentRefund(payment, amount, payment.taxInRefund(amount), reason,
                LocalDate.now(clock), recordedBy, clock.instant(), stripeRefundId, clientId, number));
        payment.getRefunds().add(refund);
        events.publishEvent(new PaymentEvents.Refunded(payment.getId(), refund.getRefundedOn()));
        recipients(payment).forEach(to -> send(to, refund));
    }

    private List<String> recipients(Payment payment) {
        if (payment.getPayer() != null) {
            return List.of(payment.getPayer().getEmail());
        }
        return users.findBySchoolIdOrderByDisplayName(payment.getSchool().getId()).stream()
                .filter(u -> u.getRole() == Role.HEAD && u.isEnabled())
                .map(u -> u.getEmail())
                .toList();
    }

    private void send(String to, PaymentRefund refund) {
        Payment payment = refund.getPayment();
        String title = country.creditNoteTitle();
        mailer.send(to, title + " " + refund.creditNoteLabel(),
                "Hello,\n\n" + money.format(refund.getAmountPence()) + " of your payment for "
                        + payment.getDescription() + " has been refunded. Your " + title.toLowerCase(Locale.UK)
                        + " is attached.\n\nQuestions about it: reply to this email.",
                refund.creditNoteLabel() + ".pdf", pdf(refund), "application/pdf");
    }

    private byte[] pdf(PaymentRefund refund) {
        return CreditNotePdf.render(refund, country.creditNoteTitle(), country.taxLabel(), country.legalEntity().line(),
                money);
    }

    /** Shapes a Stripe refund's metadata for {@link #fromStripe}. */
    static String paymentIdIn(Map<String, String> metadata) {
        return metadata == null ? null : metadata.get("paymentId");
    }
}
