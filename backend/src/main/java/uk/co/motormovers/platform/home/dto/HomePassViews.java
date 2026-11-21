package uk.co.motormovers.platform.home.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A family's cover on the Programme at Home (its monthly subscription, or a pass), as families and the team see it. */
public final class HomePassViews {

    private HomePassViews() {
    }

    /**
     * A family's cover. {@code onSale}: a monthly price is set today ({@code pricePence} a month, tax included; for a
     * family that subscribes, the price it subscribed at). {@code running}: it is covered today, to {@code endsOn};
     * {@code ended}: its cover ended on {@code endsOn}. {@code cardPayments}: Stripe is on, so a family can subscribe
     * here. {@code subscription}: NONE, ACTIVE (Stripe takes the next month on {@code renewsOn}), CANCELLING (it ends
     * with the paid month, on {@code endsOn}) or PAST_DUE (a month's payment failed). {@code canSubscribe}: Subscribe
     * can be offered (on sale, no subscription, and no cover beyond today). {@code canManage}: this parent pays for
     * it, so may open Stripe's page to cancel or change the card. {@code hasFamily} is false for a parent with no children at home yet. {@code inductionDone}:
     * this parent has finished the parent induction, so with a running pass sessions and the check are open.
     * {@code contactEmail} and {@code contactPhone}: whom to ask about the pass, each null until the country's legal
     * entity gives one.
     */
    public record Pass(boolean hasFamily, boolean onSale, Integer pricePence, boolean cardPayments, boolean running,
            LocalDate endsOn, boolean ended, boolean inductionDone, String contactEmail, String contactPhone,
            String subscription, LocalDate renewsOn, boolean canSubscribe, boolean canManage) {
    }

    public record Checkout(String url) {
    }

    public record Member(String name, String email, String role) {
    }

    /** One part of a family's pass: bought ({@code paymentId}) or extended by the team. */
    public record Entry(LocalDate startsOn, LocalDate endsOn, Long paymentId, String how, String addedBy,
            Instant addedAt, String note) {
    }

    /**
     * A family's subscription as the team sees it: {@code state} ACTIVE, CANCELLING, PAST_DUE or ENDED;
     * {@code paidTo} the last day paid for; who cancelled it for the team and why, if anyone did.
     */
    public record Subscription(String state, int pricePence, String payerEmail, LocalDate paidTo, Instant startedAt,
            Instant endedAt, String cancelledBy, String cancelNote) {
    }

    /** What the team sees for a family, found by any member's email. {@code subscription} is null with none. */
    public record FamilyPass(Long familyId, List<Member> members, boolean running, LocalDate endsOn,
            List<Entry> history, Subscription subscription) {
    }

    /** The team cancels a family's subscription: whose, and why. */
    public record CancelSubscription(@NotBlank String email, @NotBlank @Size(max = 300) String note) {
    }

    public record Extend(@NotBlank String email, @NotNull LocalDate endsOn, @Size(max = 300) String note) {
    }

    public record Price(int pricePence, LocalDate effectiveFrom, String changedBy, Instant changedAt) {
    }

    /** {@code currentPence} is the price today, or null while the pass is not on sale. */
    public record Prices(Integer currentPence, List<Price> prices) {
    }

    public record PriceChange(@Min(1) int pricePence, @NotNull LocalDate effectiveFrom) {
    }
}
