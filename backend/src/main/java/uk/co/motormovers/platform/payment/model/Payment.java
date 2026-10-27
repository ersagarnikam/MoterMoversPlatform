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
import jakarta.persistence.ManyToOne;

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
