package uk.co.motormovers.platform.payment.stripe;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import tools.jackson.databind.json.JsonMapper;

/**
 * A stand-in for Stripe on this server, for development, demos and the browser tests (STRIPE_FAKE=true; production
 * refuses to start with it). No card is asked for and no money moves. Its "pages" are addresses on this server
 * (FakeStripeController) which say what Stripe would say next, as the same messages, through the same code as the
 * real webhook. It holds everything in memory, so a restart forgets its subscriptions.
 *
 * <p>Its messages have the shape of Stripe's current version: an invoice names its subscription under
 * {@code parent.subscription_details}, and its period on its line.
 */
public class FakeStripe implements StripeClient {

    /** A Checkout page waiting to be "paid": a payment, or the start of a subscription. */
    private record Page(Long paymentId, Long rowId, int amount, String back) {
    }

    /** A subscription the stand-in is running. */
    private static final class Running {
        final String id;
        final String customer;
        final Long rowId;
        final int amount;
        ZonedDateTime periodStart;
        ZonedDateTime periodEnd;
        boolean cancelAtEnd;
        String status = "active";

        Running(String id, String customer, Long rowId, int amount, ZonedDateTime from) {
            this.id = id;
            this.customer = customer;
            this.rowId = rowId;
            this.amount = amount;
            this.periodStart = from;
            this.periodEnd = from.plusMonths(1);
        }
    }

    private final Map<String, Page> pages = new ConcurrentHashMap<>();
    private final Map<String, Running> byCustomer = new ConcurrentHashMap<>();
    private final AtomicLong next = new AtomicLong(1);
    private final JsonMapper json;
    private final String currency;
    private final Clock clock;

    public FakeStripe(JsonMapper json, String currency, Clock clock) {
        this.json = json;
        this.currency = currency;
        this.clock = clock;
    }

    // --- What the platform asks of Stripe -----------------------------------------------------------------------------

    @Override
    public Checkout createCheckout(Long paymentId, int amountPence, String description, String payerEmail,
            String successUrl, String cancelUrl) {
        String id = "cs_fake_" + next.getAndIncrement();
        pages.put(id, new Page(paymentId, null, amountPence, path(successUrl)));
        return new Checkout(id, "/api/dev/stripe/checkout/" + id);
    }

    @Override
    public Checkout createSubscriptionCheckout(Long subscriptionRowId, Long familyId, Long payerId, int monthlyMinor,
            String description, String payerEmail, String successUrl, String cancelUrl) {
        String id = "cs_fake_" + next.getAndIncrement();
        pages.put(id, new Page(null, subscriptionRowId, monthlyMinor, path(successUrl)));
        return new Checkout(id, "/api/dev/stripe/checkout/" + id);
    }

    @Override
    public String createPortal(String customerId, String returnUrl) {
        return "/api/dev/stripe/portal/" + customerId;
    }

    @Override
    public synchronized void cancelSubscription(String subscriptionId, boolean atPeriodEnd) {
        byCustomer.values().stream().filter(s -> s.id.equals(subscriptionId)).forEach(s -> {
            if (atPeriodEnd) {
                s.cancelAtEnd = true;
            } else {
                s.status = "canceled";
            }
        });
    }

    @Override
    public String invoicePaymentIntent(String invoiceId) {
        return "pi_fake_" + invoiceId;
    }

    @Override
    public Refund createRefund(String paymentIntent, int amountMinor, Long paymentId, String idempotencyKey) {
        return new Refund("re_fake_" + next.getAndIncrement(), "succeeded");
    }

    // --- What Stripe would then tell the platform ---------------------------------------------------------------------

    /** What Stripe would send, in order, and where it would send the person next. */
    public record Outcome(List<String> messages, String back) {
    }

    /** The person "pays" on a Checkout page. Null if there is no such page (or it was paid already). */
    public synchronized Outcome pay(String pageId) {
        Page page = pages.remove(pageId);
        if (page == null) {
            return null;
        }
        if (page.paymentId() != null) {
            Map<String, Object> session = new LinkedHashMap<>();
            session.put("id", pageId);
            session.put("object", "checkout.session");
            session.put("mode", "payment");
            session.put("payment_status", "paid");
            session.put("payment_intent", "pi_fake_" + next.getAndIncrement());
            session.put("client_reference_id", String.valueOf(page.paymentId()));
            session.put("metadata", Map.of("paymentId", String.valueOf(page.paymentId())));
            return new Outcome(List.of(event("checkout.session.completed", session)), page.back());
        }
        long n = next.getAndIncrement();
        Running sub = new Running("sub_fake_" + n, "cus_fake_" + n, page.rowId(), page.amount(),
                ZonedDateTime.now(clock));
        byCustomer.put(sub.customer, sub);
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("id", pageId);
        session.put("object", "checkout.session");
        session.put("mode", "subscription");
        session.put("payment_status", "paid");
        session.put("subscription", sub.id);
        session.put("customer", sub.customer);
        session.put("metadata", metadata(sub));
        return new Outcome(List.of(event("checkout.session.completed", session),
                event("invoice.paid", invoice(sub, "subscription_create"))), page.back());
    }

    public boolean knows(String customer) {
        return byCustomer.containsKey(customer);
    }

    /** One line saying where a subscription stands, for the stand-in's page. */
    public synchronized String describe(String customer) {
        Running sub = byCustomer.get(customer);
        if (sub == null) {
            return "No such subscription.";
        }
        return "Status: " + sub.status + (sub.cancelAtEnd ? ", cancels at the end of the month" : "")
                + ". Paid to " + sub.periodEnd.toLocalDate() + ".";
    }

    /** The payer cancels at the end of the paid month on Stripe's page, or changes their mind. */
    public synchronized List<String> cancelAtEnd(String customer, boolean cancel) {
        Running sub = byCustomer.get(customer);
        if (sub == null || "canceled".equals(sub.status)) {
            return List.of();
        }
        sub.cancelAtEnd = cancel;
        return List.of(event("customer.subscription.updated", subscription(sub)));
    }

    /** The month ends: Stripe takes the next one, or ends a subscription that was cancelled. */
    public synchronized List<String> nextMonth(String customer) {
        Running sub = byCustomer.get(customer);
        if (sub == null || "canceled".equals(sub.status)) {
            return List.of();
        }
        if (sub.cancelAtEnd) {
            return end(customer);
        }
        sub.periodStart = sub.periodEnd;
        sub.periodEnd = sub.periodStart.plusMonths(1);
        sub.status = "active";
        return List.of(event("invoice.paid", invoice(sub, "subscription_cycle")),
                event("customer.subscription.updated", subscription(sub)));
    }

    /** The month ends and the card is refused. */
    public synchronized List<String> nextMonthFails(String customer) {
        Running sub = byCustomer.get(customer);
        if (sub == null || "canceled".equals(sub.status)) {
            return List.of();
        }
        sub.status = "past_due";
        Map<String, Object> invoice = invoice(sub, "subscription_cycle");
        invoice.put("amount_paid", 0);
        invoice.put("status", "open");
        return List.of(event("invoice.payment_failed", invoice),
                event("customer.subscription.updated", subscription(sub)));
    }

    /** Stripe ends the subscription: it was cancelled, or its retries ran out. */
    public synchronized List<String> end(String customer) {
        Running sub = byCustomer.get(customer);
        if (sub == null) {
            return List.of();
        }
        sub.status = "canceled";
        return List.of(event("customer.subscription.deleted", subscription(sub)));
    }

    // --- The messages -------------------------------------------------------------------------------------------------

    private Map<String, Object> invoice(Running sub, String reason) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("subscription", sub.id);
        details.put("metadata", metadata(sub));
        Map<String, Object> invoice = new LinkedHashMap<>();
        invoice.put("id", "in_fake_" + next.getAndIncrement());
        invoice.put("object", "invoice");
        invoice.put("status", "paid");
        invoice.put("billing_reason", reason);
        invoice.put("currency", currency);
        invoice.put("customer", sub.customer);
        invoice.put("amount_paid", sub.amount);
        invoice.put("parent", Map.of("type", "subscription_details", "subscription_details", details));
        invoice.put("lines", Map.of("data", List.of(Map.of("period",
                Map.of("start", sub.periodStart.toEpochSecond(), "end", sub.periodEnd.toEpochSecond())))));
        return invoice;
    }

    private Map<String, Object> subscription(Running sub) {
        Map<String, Object> subscription = new LinkedHashMap<>();
        subscription.put("id", sub.id);
        subscription.put("object", "subscription");
        subscription.put("status", sub.status);
        subscription.put("customer", sub.customer);
        subscription.put("cancel_at_period_end", sub.cancelAtEnd);
        subscription.put("metadata", metadata(sub));
        return subscription;
    }

    private static Map<String, String> metadata(Running sub) {
        return Map.of(SUBSCRIPTION_ROW, String.valueOf(sub.rowId));
    }

    private String event(String type, Map<String, Object> object) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", "evt_fake_" + next.getAndIncrement());
        event.put("type", type);
        event.put("data", Map.of("object", object));
        return json.writeValueAsString(event);
    }

    /**
     * The path of an address on the web app, without its host: the stand-in sends the browser back to whichever
     * address it came from (the browser tests' is not the one in the settings).
     */
    static String path(String url) {
        if (url == null) {
            return "/";
        }
        int scheme = url.indexOf("://");
        int slash = scheme < 0 ? url.indexOf('/') : url.indexOf('/', scheme + 3);
        String path = slash < 0 ? "/" : url.substring(slash);
        return path.startsWith("//") ? "/" : path;
    }
}
