package uk.co.motormovers.platform.school.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Licence plans and their dated prices, as the team manages them. Amounts are in pence a year. */
public final class PlanViews {

    private PlanViews() {
    }

    public record PriceRow(LocalDate effectiveFrom, int pricePence, String changedBy, Instant changedAt) {
    }

    /** nextPrice is a change scheduled for a later date, if any. history is newest first. */
    public record PlanView(String code, String label, boolean freeTrial, boolean active, int currentPricePence,
            PriceRow nextPrice, long licences, List<PriceRow> history) {
    }

    public record NewPlan(@NotBlank @Size(max = 100) String label, boolean freeTrial,
            @Min(0) @Max(10_000_000) int pricePence) {
    }

    public record PlanUpdate(@NotBlank @Size(max = 100) String label, boolean active) {
    }

    public record PriceChange(@Min(0) @Max(10_000_000) int pricePence, @NotNull LocalDate effectiveFrom) {
    }
}
