package uk.co.motormovers.platform.home.model;

import java.time.Instant;

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

    /** Whether it will end with the paid month. Carrying on again lets the family be told afresh next time. */
    public void cancelling(boolean cancelling) {
        this.cancelling = cancelling;
        if (!cancelling && status != Status.ENDED) {
            this.endNoticeSent = false;
        }
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

    /** Marks the family as told the last day. Returns whether it had not been told yet. */
    public boolean tellOfEnd() {
        boolean first = !endNoticeSent;
        this.endNoticeSent = true;
        return first;
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
