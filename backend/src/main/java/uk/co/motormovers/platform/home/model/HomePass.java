package uk.co.motormovers.platform.home.model;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Part of a family's cover on the Programme at Home: a month of its subscription ({@code subscriptionId}, with the
 * payment Stripe took), twelve months bought by card before monthly billing ({@code paymentId} alone), or an extension
 * by the team (neither). A 12-month row has no dates until its payment is paid. The family is covered on a day one of
 * its rows covers.
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

    /** The subscription this month belongs to; null for a 12-month pass or an extension. */
    private Long subscriptionId;

    protected HomePass() {
    }

    public HomePass(Long familyId, Long paymentId, String addedBy, Instant addedAt, String note) {
        this.familyId = familyId;
        this.paymentId = paymentId;
        this.addedBy = addedBy;
        this.addedAt = addedAt;
        this.note = note;
    }

    /** A month of a subscription, paid by {@code paymentId} (null when Stripe charged nothing for it). */
    public static HomePass monthOf(Long subscriptionId, Long familyId, Long paymentId, String addedBy, Instant addedAt) {
        HomePass month = new HomePass(familyId, paymentId, addedBy, addedAt, null);
        month.subscriptionId = subscriptionId;
        return month;
    }

    public Long getSubscriptionId() {
        return subscriptionId;
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
