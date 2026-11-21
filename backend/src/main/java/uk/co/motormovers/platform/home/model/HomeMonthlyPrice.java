package uk.co.motormovers.platform.home.model;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The Programme at Home monthly price from a date, tax included, in the deployment's currency (task 68). */
@Entity
@Table(name = "home_monthly_price")
public class HomeMonthlyPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int pricePence;

    private LocalDate effectiveFrom;

    private String changedBy;

    private Instant changedAt;

    protected HomeMonthlyPrice() {
    }

    public HomeMonthlyPrice(int pricePence, LocalDate effectiveFrom, String changedBy, Instant changedAt) {
        this.pricePence = pricePence;
        this.effectiveFrom = effectiveFrom;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    /** Only for a change that has not started yet. */
    public void revise(int pricePence, String changedBy, Instant changedAt) {
        this.pricePence = pricePence;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    public Long getId() {
        return id;
    }

    public int getPricePence() {
        return pricePence;
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
