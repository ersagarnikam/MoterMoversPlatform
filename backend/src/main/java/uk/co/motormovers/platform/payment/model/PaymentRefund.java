package uk.co.motormovers.platform.payment.model;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Money returned on a paid payment, in whole or in part, with its credit note. Amounts are in the currency's minor
 * unit. {@code stripeRefundId} is null when the money was returned another way, by hand.
 */
@Entity
@Table(name = "payment_refund")
public class PaymentRefund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    private int amountPence;

    /** Null when the payment has no saved tax rate: then no tax line is shown. */
    private Integer taxPence;

    private String reason;

    private LocalDate refundedOn;

    @Enumerated(EnumType.STRING)
    private RefundStatus status = RefundStatus.REFUNDED;

    private String recordedBy;

    private Instant recordedAt;

    private String stripeRefundId;

    private String clientId;

    private int creditNoteNumber;

    protected PaymentRefund() {
    }

    public PaymentRefund(Payment payment, int amountPence, Integer taxPence, String reason, LocalDate refundedOn,
            String recordedBy, Instant recordedAt, String stripeRefundId, String clientId, int creditNoteNumber) {
        this.payment = payment;
        this.amountPence = amountPence;
        this.taxPence = taxPence;
        this.reason = reason;
        this.refundedOn = refundedOn;
        this.recordedBy = recordedBy;
        this.recordedAt = recordedAt;
        this.stripeRefundId = stripeRefundId;
        this.clientId = clientId;
        this.creditNoteNumber = creditNoteNumber;
    }

    public void failed() {
        this.status = RefundStatus.FAILED;
    }

    public boolean counts() {
        return status == RefundStatus.REFUNDED;
    }

    public boolean byHand() {
        return stripeRefundId == null;
    }

    /** "CN-000012". */
    public String creditNoteLabel() {
        return String.format("CN-%06d", creditNoteNumber);
    }

    public Long getId() {
        return id;
    }

    public Payment getPayment() {
        return payment;
    }

    public int getAmountPence() {
        return amountPence;
    }

    public Integer getTaxPence() {
        return taxPence;
    }

    public String getReason() {
        return reason;
    }

    public LocalDate getRefundedOn() {
        return refundedOn;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public String getStripeRefundId() {
        return stripeRefundId;
    }

    public int getCreditNoteNumber() {
        return creditNoteNumber;
    }
}
