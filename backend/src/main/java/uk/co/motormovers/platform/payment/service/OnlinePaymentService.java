package uk.co.motormovers.platform.payment.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

import uk.co.motormovers.platform.common.config.PlatformProperties;
import uk.co.motormovers.platform.common.exception.NotFoundException;
import uk.co.motormovers.platform.payment.model.Payment;
import uk.co.motormovers.platform.payment.model.PaymentStatus;
import uk.co.motormovers.platform.payment.repository.PaymentRepository;
import uk.co.motormovers.platform.payment.stripe.StripeClient;
import uk.co.motormovers.platform.payment.stripe.StripeProperties;
import uk.co.motormovers.platform.payment.stripe.StripeSignature;
import uk.co.motormovers.platform.security.model.CurrentUser;
import uk.co.motormovers.platform.security.service.AccessPolicy;
import uk.co.motormovers.platform.security.service.CurrentUserResolver;

/**
 * Paying a payment that is due by card, on a Stripe Checkout page: a person their own, a head their school's.
 * Stripe then calls the webhook, which marks the payment paid; the return to the platform alone proves nothing.
 */
@Service
public class OnlinePaymentService {

    private static final Logger log = LoggerFactory.getLogger(OnlinePaymentService.class);

    /** The parts of a Stripe event the platform reads. */
    record Event(String type, EventData data) {
    }

    record EventData(CheckoutSession object) {
    }

    record CheckoutSession(String id, String payment_status, String payment_intent, String client_reference_id,
            Map<String, String> metadata) {
    }

    private final PaymentRepository payments;
    private final StripeClient stripe;
    private final StripeProperties properties;
    private final PlatformProperties platform;
    private final AccessPolicy access;
    private final CurrentUserResolver currentUser;
    private final JsonMapper json;
    private final Clock clock;

    public OnlinePaymentService(PaymentRepository payments, StripeClient stripe, StripeProperties properties,
            PlatformProperties platform, AccessPolicy access, CurrentUserResolver currentUser, JsonMapper json,
            Clock clock) {
        this.payments = payments;
        this.stripe = stripe;
        this.properties = properties;
        this.platform = platform;
        this.access = access;
        this.currentUser = currentUser;
        this.json = json;
        this.clock = clock;
    }

    public boolean enabled() {
        return properties.enabled();
    }

    /** The address of a Stripe page to pay this payment by card. */
    @Transactional(readOnly = true)
    public String checkout(Long paymentId) {
        if (!enabled()) {
            throw new IllegalStateException("Card payments are not switched on");
        }
        Payment p = payments.findById(paymentId).orElseThrow(() -> new NotFoundException("Payment", paymentId));
        CurrentUser me = currentUser.current();
        boolean school = p.getSchool() != null;
        if (school) {
            access.requireSchoolAccess(p.getSchool());
        } else if (!p.getPayer().getId().equals(me.id())) {
            throw new AccessDeniedException("You can pay only your own payments");
        }
        if (p.getStatus() != PaymentStatus.DUE) {
            throw new IllegalStateException("This payment is not due");
        }
        String page = link(school ? "/school/payments" : "/payments");
        return stripe.createCheckout(p.getId(), p.getAmountPence(), p.getDescription(),
                school ? me.email() : p.getPayer().getEmail(), page + "?paid=1", page + "?cancelled=1").url();
    }

    /**
     * Stripe says a Checkout page was paid: the payment is marked paid today with Stripe's reference. A call that is
     * not signed by Stripe is refused. Returns whether a payment was marked paid.
     */
    @Transactional
    public boolean webhook(String payload, String signature) {
        if (!enabled() || !StripeSignature.valid(signature, payload, properties.webhookSecret(), clock.instant())) {
            throw new IllegalArgumentException("This call is not signed by Stripe");
        }
        Event event = json.readValue(payload, Event.class);
        boolean paidEvent = "checkout.session.completed".equals(event.type())
                || "checkout.session.async_payment_succeeded".equals(event.type());
        if (!paidEvent || event.data() == null || event.data().object() == null) {
            return false;
        }
        CheckoutSession session = event.data().object();
        if (!"paid".equals(session.payment_status())) {
            // E.g. a bank debit still on its way: Stripe sends async_payment_succeeded when it arrives.
            return false;
        }
        String id = session.metadata() != null && session.metadata().get("paymentId") != null
                ? session.metadata().get("paymentId") : session.client_reference_id();
        Long paymentId;
        try {
            paymentId = Long.valueOf(id);
        } catch (NumberFormatException | NullPointerException e) {
            log.warn("Stripe payment {} names no platform payment", session.id());
            return false;
        }
        return payments.findById(paymentId)
                .map(p -> p.paidOnline(LocalDate.now(clock),
                        session.payment_intent() != null ? session.payment_intent() : session.id()))
                .orElseGet(() -> {
                    log.warn("Stripe payment {} is for payment {}, which does not exist", session.id(), paymentId);
                    return false;
                });
    }

    private String link(String path) {
        String base = platform.mail().webUrl();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path;
    }
}
