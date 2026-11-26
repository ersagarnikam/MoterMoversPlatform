package uk.co.motormovers.platform.home.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A family's monthly subscription to the Programme at Home (go-live task 68), which Stripe bills. The platform holds
 * no card: only Stripe's ids, what Stripe last said about it, and who pays. The days it covers are the family's
 * {@link HomePass} rows, one for each month paid.
 */
@Entity
@Table(name = "home_subscription")
public class HomeSubscription {

    public enum Status {
        /** Stripe's page was opened; the first month is not paid yet. */
        PENDING,
        /** Paid up, and Stripe will take the next month. */
        ACTIVE,
        /** A month's payment failed; Stripe is trying again. */
        PAST_DUE,
        /** Over: cancelled, or Stripe gave up on a failed payment. */
        ENDED;

        /** Running at Stripe, so the family cannot start another. */
        public boolean live() {
            return this == ACTIVE || this == PAST_DUE;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long familyId;

    private Long payerId;

    private int pricePence;

    @Enumerated(EnumType.STRING)
    private Status status;

    private String stripeSessionId;

    private String checkoutUrl;

    private Instant checkoutExpiresAt;

    private String stripeSubscriptionId;

    private String stripeCustomerId;

    private boolean cancelling;

    private boolean endNoticeSent;

    /** The last day the family was told about, so it is told once for each. */
    private LocalDate endNoticeDay;

    /** The last day paid for, kept here because the days themselves go when the family is deleted. */
    private LocalDate paidTo;

    private String failedInvoiceId;

    private Instant createdAt;

    private Instant endedAt;

    private String cancelledBy;

    private String cancelNote;

    protected HomeSubscription() {
    }

    public HomeSubscription(Long familyId, Long payerId, int pricePence, Instant createdAt) {
        this.familyId = familyId;
        this.payerId = payerId;
        this.pricePence = pricePence;
        this.status = Status.PENDING;
        this.createdAt = createdAt;
    }

    /** Stripe's page for the first month, kept so that pressing Subscribe again opens the same one. */
    public void checkoutOpened(String sessionId, String url, Instant expiresAt) {
        this.stripeSessionId = sessionId;
        this.checkoutUrl = url;
        this.checkoutExpiresAt = expiresAt;
    }

    public boolean checkoutOpenAt(Instant now) {
        return status == Status.PENDING && checkoutUrl != null && checkoutExpiresAt != null
                && now.isBefore(checkoutExpiresAt);
    }

    /** Stripe's ids, from whichever of its messages arrives first. */
    public void knownToStripeAs(String subscriptionId, String customerId) {
        if (subscriptionId != null) {
            this.stripeSubscriptionId = subscriptionId;
        }
        if (customerId != null) {
            this.stripeCustomerId = customerId;
        }
    }

    /** A month was paid. An ended subscription stays ended. */
    public void paid() {
        if (status != Status.ENDED) {
            this.status = Status.ACTIVE;
        }
        this.failedInvoiceId = null;
        this.checkoutUrl = null;
    }

    /** A month's payment failed. Returns whether the payer has yet to be told about this invoice. */
    public boolean paymentFailed(String invoiceId) {
        if (status == Status.ACTIVE) {
            this.status = Status.PAST_DUE;
        }
        boolean news = invoiceId == null || !invoiceId.equals(failedInvoiceId);
        this.failedInvoiceId = invoiceId;
        return news;
    }

    public void pastDue() {
        if (status == Status.ACTIVE) {
            this.status = Status.PAST_DUE;
        }
    }

    /** Whether it will end with the paid month. */
    public void cancelling(boolean cancelling) {
        this.cancelling = cancelling;
    }

    /** A month was paid for, to this day. */
    public void paidTo(LocalDate day) {
        if (day != null && (paidTo == null || day.isAfter(paidTo))) {
            this.paidTo = day;
        }
    }

    /** Its family has been deleted (the last member left): the row stays, for the team. */
    public void familyGone() {
        this.familyId = null;
    }

    public void ended(Instant at) {
        if (status != Status.ENDED) {
            this.status = Status.ENDED;
            this.endedAt = at;
        }
        this.checkoutUrl = null;
    }

    /** The team cancelled it, with who and why. */
    public void cancelledBy(String who, String note) {
        this.cancelledBy = who;
        this.cancelNote = note;
    }

    /** Nobody is to be told that this one ended (a duplicate, or a family that has gone). */
    public void endsUnannounced() {
        this.endNoticeSent = true;
        this.endNoticeDay = null;
    }

    /**
     * Marks the family as told that {@code lastDay} is the last day. Returns whether it is news: it is told once for
     * a given last day, so cancelling, carrying on and cancelling again in the same month sends one email, while a
     * cancellation in a later month, with a later last day, sends another.
     */
    public boolean tellOfEnd(LocalDate lastDay) {
        boolean told = endNoticeSent && (endNoticeDay == null || Objects.equals(endNoticeDay, lastDay));
        this.endNoticeSent = true;
        if (!told) {
            this.endNoticeDay = lastDay;
        }
        return !told;
    }

    public LocalDate getPaidTo() {
        return paidTo;
    }

    public Long getId() {
        return id;
    }

    public Long getFamilyId() {
        return familyId;
    }

    public Long getPayerId() {
        return payerId;
    }

    public int getPricePence() {
        return pricePence;
    }

    public Status getStatus() {
        return status;
    }

    public String getStripeSessionId() {
        return stripeSessionId;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public String getStripeSubscriptionId() {
        return stripeSubscriptionId;
    }

    public String getStripeCustomerId() {
        return stripeCustomerId;
    }

    public boolean isCancelling() {
        return cancelling;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public String getCancelledBy() {
        return cancelledBy;
    }

    public String getCancelNote() {
        return cancelNote;
    }
}
