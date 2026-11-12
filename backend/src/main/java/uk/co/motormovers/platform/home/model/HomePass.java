package uk.co.motormovers.platform.home.model;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Part of a family's Motor Movers at Home pass: twelve months bought by card ({@code paymentId}), or an extension by
 * the team (no payment). A row bought by card has no dates until the payment is paid. The family's pass runs on a day
 * one of its rows covers.
 */
@Entity
@Table(name = "home_pass")
public class HomePass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long familyId;

    private Long paymentId;

    private LocalDate startsOn;

    private LocalDate endsOn;

    private String addedBy;

    private Instant addedAt;

    private String note;

    protected HomePass() {
    }

    public HomePass(Long familyId, Long paymentId, String addedBy, Instant addedAt, String note) {
        this.familyId = familyId;
        this.paymentId = paymentId;
        this.addedBy = addedBy;
        this.addedAt = addedAt;
        this.note = note;
    }

    /** From and to, both included. An end before the start covers no day (a pass refunded before it began). */
    public HomePass runs(LocalDate from, LocalDate to) {
        this.startsOn = from;
        this.endsOn = to;
        return this;
    }

    public boolean started() {
        return startsOn != null && endsOn != null;
    }

    public boolean covers(LocalDate day) {
        return started() && !day.isBefore(startsOn) && !day.isAfter(endsOn);
    }

    public Long getId() {
        return id;
    }

    public Long getFamilyId() {
        return familyId;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public String getAddedBy() {
        return addedBy;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public String getNote() {
        return note;
    }
}
