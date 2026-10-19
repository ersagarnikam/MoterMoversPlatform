package uk.co.motormovers.platform.payment.model;

public enum PaymentStatus {
    /** Invoiced, not paid yet. Counts as outstanding. */
    DUE,
    /** Money received. Counts as spent. */
    PAID,
    /** Invoiced by mistake or written off. Counts towards nothing. */
    CANCELLED
}
