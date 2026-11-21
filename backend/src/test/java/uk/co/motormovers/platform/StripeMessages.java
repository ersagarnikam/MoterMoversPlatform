package uk.co.motormovers.platform;

import java.time.Instant;

/**
 * Stripe's messages about a family's monthly subscription (task 68), as test payloads. Invoices come in both of
 * Stripe's shapes: the current one (since its 2025-03-31 version), where the subscription and its metadata are under
 * {@code parent.subscription_details} and the invoice names no payment intent, and the older one, with both at the top.
 */
final class StripeMessages {

    private StripeMessages() {
    }

    /** The payer finished Stripe's page in subscription mode. */
    static String checkoutCompleted(String session, long row, String subscription, String customer) {
        return event("checkout.session.completed", "{\"id\":\"" + session + "\",\"object\":\"checkout.session\","
                + "\"mode\":\"subscription\",\"payment_status\":\"paid\",\"subscription\":\"" + subscription
                + "\",\"customer\":\"" + customer + "\",\"metadata\":{\"homeSubscriptionId\":\"" + row + "\"}}");
    }

    /** A month paid, in Stripe's current shape. */
    static String invoicePaid(String invoice, long row, String subscription, String customer, int amount,
            String currency, Instant from, Instant to) {
        return event("invoice.paid", invoice(invoice, row, subscription, customer, amount, currency, from, to,
                "paid"));
    }

    /** A month paid, as Stripe's versions before 2025-03-31 say it. */
    static String invoicePaidOldShape(String invoice, long row, String subscription, String customer, int amount,
            String currency, Instant from, Instant to, String paymentIntent) {
        return event("invoice.paid", "{\"id\":\"" + invoice + "\",\"object\":\"invoice\",\"status\":\"paid\","
                + "\"billing_reason\":\"subscription_cycle\",\"currency\":\"" + currency + "\",\"customer\":\""
                + customer + "\",\"amount_paid\":" + amount + ",\"subscription\":\"" + subscription
                + "\",\"payment_intent\":\"" + paymentIntent + "\",\"subscription_details\":{\"metadata\":"
                + "{\"homeSubscriptionId\":\"" + row + "\"}},\"period_start\":1,\"period_end\":2,"
                + lines(from, to) + "}");
    }

    static String paymentFailed(String invoice, long row, String subscription, String customer, int amount,
            String currency, Instant from, Instant to) {
        return event("invoice.payment_failed", invoice(invoice, row, subscription, customer, 0, currency, from, to,
                "open"));
    }

    /** {@code extra}: more of the subscription's fields, each starting with a comma, e.g. {@code ,"cancel_at":123}. */
    static String subscriptionUpdated(String subscription, long row, String customer, String status,
            boolean cancelAtPeriodEnd, String extra) {
        return event("customer.subscription.updated", subscription(subscription, row, customer, status,
                cancelAtPeriodEnd, extra));
    }

    static String subscriptionDeleted(String subscription, long row, String customer) {
        return event("customer.subscription.deleted", subscription(subscription, row, customer, "canceled", false,
                ""));
    }

    private static String invoice(String invoice, long row, String subscription, String customer, int amount,
            String currency, Instant from, Instant to, String status) {
        // period_start and period_end at the top are not the month (Stripe's documentation): they must be ignored.
        return "{\"id\":\"" + invoice + "\",\"object\":\"invoice\",\"status\":\"" + status + "\","
                + "\"billing_reason\":\"subscription_cycle\",\"currency\":\"" + currency + "\",\"customer\":\""
                + customer + "\",\"amount_paid\":" + amount + ",\"period_start\":1,\"period_end\":2,"
                + "\"parent\":{\"type\":\"subscription_details\",\"subscription_details\":{\"subscription\":\""
                + subscription + "\",\"metadata\":{\"homeSubscriptionId\":\"" + row + "\"}}}," + lines(from, to)
                + "}";
    }

    private static String lines(Instant from, Instant to) {
        return "\"lines\":{\"data\":[{\"period\":{\"start\":" + from.getEpochSecond() + ",\"end\":"
                + to.getEpochSecond() + "}}]}";
    }

    private static String subscription(String subscription, long row, String customer, String status,
            boolean cancelAtPeriodEnd, String extra) {
        return "{\"id\":\"" + subscription + "\",\"object\":\"subscription\",\"status\":\"" + status
                + "\",\"customer\":\"" + customer + "\",\"cancel_at_period_end\":" + cancelAtPeriodEnd
                + ",\"metadata\":{\"homeSubscriptionId\":\"" + row + "\"}" + extra + "}";
    }

    private static String event(String type, String object) {
        return "{\"id\":\"evt_" + Math.abs(object.hashCode()) + "\",\"type\":\"" + type + "\",\"data\":{\"object\":"
                + object + "}}";
    }
}
