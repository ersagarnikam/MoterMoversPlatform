package uk.co.motormovers.platform.payment.stripe;

/** Creates Stripe Checkout pages, where the payer enters their card on Stripe's own site, never ours. */
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
}
