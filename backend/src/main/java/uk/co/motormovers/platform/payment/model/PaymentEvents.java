package uk.co.motormovers.platform.payment.model;

import java.time.LocalDate;

/**
 * Published in the same transaction when a payment is marked paid (by Stripe's webhook or by the team) and when a
 * refund is recorded, so what was bought (a Motor Movers at Home pass) can follow without the payment code knowing it.
 */
public final class PaymentEvents {

    private PaymentEvents() {
    }

    public record Paid(Long paymentId) {
    }

    public record Refunded(Long paymentId, LocalDate refundedOn) {
    }
}
