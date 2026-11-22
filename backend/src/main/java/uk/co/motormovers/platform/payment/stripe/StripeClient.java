package uk.co.motormovers.platform.payment.stripe;

/**
 * Stripe, as the platform uses it: Checkout pages, where the payer enters their card on Stripe's own site, never
 * ours; refunds; and a family's monthly subscription (its Checkout page, Stripe's own page for cancelling or changing
 * the card, and cancelling it).
 */
public interface StripeClient {

    /** A Checkout page: its id, and the address to send the payer to. */
    record Checkout(String id, String url) {
    }

    /**
     * One payment of {@code amountPence} in pounds for {@code description}. {@code paymentId} comes back with Stripe's
     * confirmation, so the platform knows which payment was made.
     */
    Checkout createCheckout(Long paymentId, int amountPence, String description, String payerEmail, String successUrl,
            String cancelUrl);

    /** A refund Stripe has accepted. */
    record Refund(String id, String status) {
    }

    /**
     * Refunds {@code amountMinor} of a payment. {@code idempotencyKey} makes a repeated request return the first
     * refund instead of a second one. Throws if Stripe does not accept it.
     */
    Refund createRefund(String paymentIntent, int amountMinor, Long paymentId, String idempotencyKey);

    /** The name, in a Stripe subscription's metadata, of the platform's row for it (home_subscription.id). */
    String SUBSCRIPTION_ROW = "homeSubscriptionId";

    /**
     * A Checkout page that starts a subscription of {@code monthlyMinor} a month, in the deployment's currency.
     * {@code subscriptionRowId} (with the family and the payer, as ids only) goes into the subscription's metadata, so
     * each of Stripe's messages about it names the platform's row.
     */
    default Checkout createSubscriptionCheckout(Long subscriptionRowId, Long familyId, Long payerId, int monthlyMinor,
            String description, String payerEmail, String successUrl, String cancelUrl) {
        throw new IllegalStateException("Card payments are not switched on");
    }

    /** The address of Stripe's own page where this customer cancels the subscription or changes the card. */
    default String createPortal(String customerId, String returnUrl) {
        throw new IllegalStateException("Card payments are not switched on");
    }

    /**
     * Cancels a subscription: when the month already paid for ends ({@code atPeriodEnd}), or at once. Throws if
     * Stripe does not accept it.
     */
    default void cancelSubscription(String subscriptionId, boolean atPeriodEnd) {
        throw new IllegalStateException("Card payments are not switched on, so Stripe cannot cancel this");
    }

    /**
     * The payment intent that paid an invoice, to refund against, or null if Stripe names none. Stripe's newer
     * versions no longer put it on the invoice itself.
     */
    default String invoicePaymentIntent(String invoiceId) {
        return null;
    }
}
