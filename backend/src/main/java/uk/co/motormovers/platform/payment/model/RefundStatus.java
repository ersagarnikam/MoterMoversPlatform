package uk.co.motormovers.platform.payment.model;

/** FAILED: Stripe could not return the money after all, so the refund no longer counts. */
public enum RefundStatus {
    REFUNDED,
    FAILED
}
