package uk.co.motormovers.platform.school.model;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/** A plan's yearly price from a date onwards, and who set it. Rows are added, never changed after they apply. */
@Entity
public class LicencePlanPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_code")
    private LicencePlan plan;

    private int annualPricePence;

    private LocalDate effectiveFrom;

    private String changedBy;

    private Instant changedAt;

    protected LicencePlanPrice() {
    }

    public LicencePlanPrice(LicencePlan plan, int annualPricePence, LocalDate effectiveFrom, String changedBy,
            Instant changedAt) {
        this.plan = plan;
        this.annualPricePence = annualPricePence;
        this.effectiveFrom = effectiveFrom;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    /** Replaces a change that has not taken effect yet (same plan, same start date). */
    public void revise(int annualPricePence, String changedBy, Instant changedAt) {
        this.annualPricePence = annualPricePence;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    public Long getId() {
        return id;
    }

    public LicencePlan getPlan() {
        return plan;
    }

    public int getAnnualPricePence() {
        return annualPricePence;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
