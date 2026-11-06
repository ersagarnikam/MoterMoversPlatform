package uk.co.motormovers.platform.payment.stripe;

/** Creates Stripe Checkout pages, where the payer enters their card on Stripe's own site, never ours, and refunds. */
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
}
