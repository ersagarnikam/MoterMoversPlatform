package uk.co.motormovers.platform.payment.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import uk.co.motormovers.platform.common.config.PlatformProperties;
import uk.co.motormovers.platform.common.exception.NotFoundException;
import uk.co.motormovers.platform.payment.model.Payment;
import uk.co.motormovers.platform.payment.model.PaymentEvents;
import uk.co.motormovers.platform.payment.model.PaymentPurpose;
import uk.co.motormovers.platform.payment.model.PaymentStatus;
import uk.co.motormovers.platform.payment.model.StripeEvents;
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

    /** Checked against Stripe's documentation, 2026-11-06. Each carries the refund itself, with its status. */
    private static final Set<String> REFUND_EVENTS = Set.of("refund.created", "refund.updated", "refund.failed");

    /**
     * About a family's monthly subscription (task 68; names checked against Stripe's documentation, 2026-11-21). A
     * Checkout page finished in subscription mode is one too. The Programme at Home acts on them (StripeEvents).
     */
    private static final Set<String> SUBSCRIPTION_EVENTS = Set.of("invoice.paid", "invoice.payment_failed",
            "customer.subscription.updated", "customer.subscription.deleted");

    /** The parts of a Stripe event the platform reads. */
    record Event(String type, EventData data) {
    }

    record EventData(EventObject object) {
    }

    /** A Checkout session, or a refund (with its amount and status). */
    record EventObject(String id, String payment_status, String payment_intent, String client_reference_id,
            Map<String, String> metadata, Integer amount, String status) {
    }

    private final PaymentRepository payments;
    private final StripeClient stripe;
    private final StripeProperties properties;
    private final PlatformProperties platform;
    private final AccessPolicy access;
    private final CurrentUserResolver currentUser;
    private final JsonMapper json;
    private final Clock clock;
    private final RefundService refunds;
    private final ApplicationEventPublisher events;

    public OnlinePaymentService(PaymentRepository payments, StripeClient stripe, StripeProperties properties,
            PlatformProperties platform, AccessPolicy access, CurrentUserResolver currentUser, JsonMapper json,
            Clock clock, RefundService refunds, ApplicationEventPublisher events) {
        this.payments = payments;
        this.stripe = stripe;
        this.properties = properties;
        this.platform = platform;
        this.access = access;
        this.currentUser = currentUser;
        this.json = json;
        this.clock = clock;
        this.refunds = refunds;
        this.events = events;
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
        String page = link(school ? "/school/payments"
                : p.getPurpose() == PaymentPurpose.HOME_PASS ? "/home" : "/payments");
        return stripe.createCheckout(p.getId(), p.getAmountPence(), p.getDescription(),
                school ? me.email() : p.getPayer().getEmail(), page + "?paid=1", page + "?cancelled=1").url();
    }

    /**
     * Stripe says a Checkout page was paid, and the payment is marked paid today with Stripe's reference; or it reports
     * a refund, which RefundService records; or it is about a family's subscription. A call that is not signed by
     * Stripe is refused. Returns whether anything changed.
     */
    @Transactional
    public boolean webhook(String payload, String signature) {
        if (!enabled() || !StripeSignature.valid(signature, payload, properties.webhookSecret(), clock.instant())) {
            throw new IllegalArgumentException("This call is not signed by Stripe");
        }
        return receive(payload);
    }

    /**
     * Acts on one of Stripe's messages. Only for a caller that knows where it came from: the webhook above, once the
     * signature is checked, and the development stand-in for Stripe (FakeStripe).
     */
    @Transactional
    public boolean receive(String payload) {
        Event event = json.readValue(payload, Event.class);
        JsonNode object = json.readTree(payload).path("data").path("object");
        boolean subscriptionCheckout = "checkout.session.completed".equals(event.type())
                && "subscription".equals(object.path("mode").asString(""));
        if (event.type() != null && (SUBSCRIPTION_EVENTS.contains(event.type()) || subscriptionCheckout)) {
            StripeEvents.Received received = new StripeEvents.Received(event.type(), object);
            events.publishEvent(received);
            return received.wasChanged();
        }
        if (REFUND_EVENTS.contains(event.type()) && event.data() != null && event.data().object() != null) {
            EventObject refund = event.data().object();
            return refunds.fromStripe(refund.id(), refund.payment_intent(), RefundService.paymentIdIn(refund.metadata()),
                    refund.amount(), refund.status());
        }
        boolean paidEvent = "checkout.session.completed".equals(event.type())
                || "checkout.session.async_payment_succeeded".equals(event.type());
        if (!paidEvent || event.data() == null || event.data().object() == null) {
            return false;
        }
        EventObject session = event.data().object();
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
                .map(p -> {
                    boolean paid = p.paidOnline(LocalDate.now(clock),
                            session.payment_intent() != null ? session.payment_intent() : session.id());
                    if (paid) {
                        events.publishEvent(new PaymentEvents.Paid(p.getId()));
                    }
                    return paid;
                })
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
