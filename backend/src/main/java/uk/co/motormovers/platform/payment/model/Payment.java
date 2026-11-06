package uk.co.motormovers.platform.payment.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

import uk.co.motormovers.platform.school.model.School;
import uk.co.motormovers.platform.user.model.AppUser;

/** One invoice or payment the team has recorded. Paid by a school or by a person, never both. */
@Entity
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private School school;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser payer;

    @Enumerated(EnumType.STRING)
    private PaymentPurpose purpose;

    private String description;

    private int amountPence;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private LocalDate invoicedOn;

    private LocalDate paidOn;

    private String reference;

    private String recordedBy;

    private Instant recordedAt;

    /** Stripe's id for a payment made by card online; null when marked paid by hand. */
    private String paidOnlineRef;

    /** The tax rate included in the amount, in basis points (2000 = 20%); null on payments from before it was kept. */
    private Integer taxRateBp;

    /** Money returned on this payment, oldest first. The payment itself stays paid. */
    @OneToMany(mappedBy = "payment")
    @OrderBy("id")
    private List<PaymentRefund> refunds = new ArrayList<>();

    protected Payment() {
    }

    private Payment(School school, AppUser payer, PaymentPurpose purpose, String description, int amountPence,
            LocalDate invoicedOn, String reference, String recordedBy, Instant recordedAt) {
        this.school = school;
        this.payer = payer;
        this.purpose = purpose;
        this.description = description;
        this.amountPence = amountPence;
        this.status = PaymentStatus.DUE;
        this.invoicedOn = invoicedOn;
        this.reference = reference;
        this.recordedBy = recordedBy;
        this.recordedAt = recordedAt;
    }

    public static Payment bySchool(School school, PaymentPurpose purpose, String description, int amountPence,
            LocalDate invoicedOn, String reference, String recordedBy, Instant recordedAt) {
        return new Payment(school, null, purpose, description, amountPence, invoicedOn, reference, recordedBy,
                recordedAt);
    }

    public static Payment byPerson(AppUser payer, PaymentPurpose purpose, String description, int amountPence,
            LocalDate invoicedOn, String reference, String recordedBy, Instant recordedAt) {
        return new Payment(null, payer, purpose, description, amountPence, invoicedOn, reference, recordedBy,
                recordedAt);
    }

    /** The tax rate in force when this payment was created, which stays with it. */
    public void includesTaxAt(int rateBp) {
        this.taxRateBp = rateBp;
    }

    public Integer getTaxRateBp() {
        return taxRateBp;
    }

    /**
     * The tax included in the amount, rounded to the minor unit: amount x rate / (1 + rate), since prices are
     * tax-inclusive. Null when the payment has no rate.
     */
    public Integer taxPence() {
        if (taxRateBp == null) {
            return null;
        }
        return taxIn(amountPence);
    }

    private int taxIn(int amount) {
        return BigDecimal.valueOf((long) amount * taxRateBp)
                .divide(BigDecimal.valueOf(10_000L + taxRateBp), 0, RoundingMode.HALF_UP).intValueExact();
    }

    public List<PaymentRefund> getRefunds() {
        return refunds;
    }

    /** What has gone back, not counting refunds Stripe could not complete. */
    public int refundedPence() {
        return refunds.stream().filter(PaymentRefund::counts).mapToInt(PaymentRefund::getAmountPence).sum();
    }

    public int refundablePence() {
        return amountPence - refundedPence();
    }

    /** What the platform has kept of a paid payment; totals count this. */
    public int netPaidPence() {
        return refundablePence();
    }

    /** "Refunded" or "Part refunded"; null when nothing has gone back. */
    public String refundLabel() {
        int refunded = refundedPence();
        if (refunded == 0) {
            return null;
        }
        return refunded >= amountPence ? "Refunded" : "Part refunded";
    }

    /** Stripe holds a payment intent to refund against; paidOnlineRef can also be a session id, which cannot be. */
    public boolean paidWithPaymentIntent() {
        return paidOnlineRef != null && paidOnlineRef.startsWith("pi_");
    }

    /**
     * The tax in a refund of {@code amount}, by the same sum as the payment. The last refund takes what is left of the
     * tax, so part refunds always add up to the tax on the payment. Null when the payment has no rate.
     */
    public Integer taxInRefund(int amount) {
        if (taxRateBp == null) {
            return null;
        }
        if (amount == refundablePence()) {
            int taxRefunded = refunds.stream().filter(PaymentRefund::counts).filter(r -> r.getTaxPence() != null)
                    .mapToInt(PaymentRefund::getTaxPence).sum();
            return taxPence() - taxRefunded;
        }
        return taxIn(amount);
    }

    public void markPaid(LocalDate on) {
        if (status != PaymentStatus.DUE) {
            throw new IllegalStateException("Only a payment that is due can be marked as paid");
        }
        this.status = PaymentStatus.PAID;
        this.paidOn = on;
    }

    /** Stripe confirmed a card payment. A payment no longer due (e.g. marked paid by hand meanwhile) stays as it is. */
    public boolean paidOnline(LocalDate on, String stripeRef) {
        if (status != PaymentStatus.DUE) {
            return false;
        }
        markPaid(on);
        this.paidOnlineRef = stripeRef;
        return true;
    }

    public String getPaidOnlineRef() {
        return paidOnlineRef;
    }

    public void cancel() {
        if (status != PaymentStatus.DUE) {
            throw new IllegalStateException("Only a payment that is due can be cancelled");
        }
        this.status = PaymentStatus.CANCELLED;
    }

    public PayerType payerType() {
        return school != null ? PayerType.SCHOOL : PayerType.of(payer.getRole());
    }

    public Long getId() {
        return id;
    }

    public School getSchool() {
        return school;
    }

    public AppUser getPayer() {
        return payer;
    }

    public PaymentPurpose getPurpose() {
        return purpose;
    }

    public String getDescription() {
        return description;
    }

    public int getAmountPence() {
        return amountPence;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public LocalDate getInvoicedOn() {
        return invoicedOn;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public String getReference() {
        return reference;
    }

    public String getRecordedBy() {
        return recordedBy;
    }
}
