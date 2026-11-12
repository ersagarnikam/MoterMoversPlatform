package uk.co.motormovers.platform.home.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The Motor Movers at Home pass, as families and the team see it. */
public final class HomePassViews {

    private HomePassViews() {
    }

    /**
     * A family's pass. {@code onSale}: a price is set today ({@code pricePence}, tax included). {@code running}: it
     * covers today, to {@code endsOn}; {@code ended}: it ended on {@code endsOn}. {@code cardPayments}: Stripe is on, so
     * it can be bought here. {@code hasFamily} is false for a parent with no children at home yet. {@code inductionDone}:
     * this parent has finished the parent induction, so with a running pass sessions and the check are open.
     * {@code contactEmail} and {@code contactPhone}: whom to ask about the pass, each null until the country's legal
     * entity gives one.
     */
    public record Pass(boolean hasFamily, boolean onSale, Integer pricePence, boolean cardPayments, boolean running,
            LocalDate endsOn, boolean ended, boolean inductionDone, String contactEmail, String contactPhone) {
    }

    public record Checkout(String url) {
    }

    public record Member(String name, String email, String role) {
    }

    /** One part of a family's pass: bought ({@code paymentId}) or extended by the team. */
    public record Entry(LocalDate startsOn, LocalDate endsOn, Long paymentId, String how, String addedBy,
            Instant addedAt, String note) {
    }

    /** What the team sees for a family, found by any member's email. */
    public record FamilyPass(Long familyId, List<Member> members, boolean running, LocalDate endsOn,
            List<Entry> history) {
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
