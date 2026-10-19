package uk.co.motormovers.platform.training.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import uk.co.motormovers.platform.training.model.CourseTrack;

/** What the team sends to set up a course or one of its cohorts. Limits match the database columns. */
public final class CourseInputs {

    private CourseInputs() {
    }

    public record CourseInput(@NotNull CourseTrack track, @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 1000) String description, @Min(1) @Max(52) int liveSessions,
            @Min(5) @Max(480) int sessionMinutes, @Min(0) @Max(10_000_000) int pricePence,
            @Min(0) @Max(10_000_000) Integer renewalPence, boolean awardsCertificate) {
    }

    public record CohortInput(@NotBlank @Size(max = 100) String name, @NotNull LocalDate startsOn,
            @NotBlank @Size(max = 100) String timeSlot, @Min(1) @Max(1000) int capacity,
            @NotBlank @Size(max = 200) String trainerName, boolean recordingAgreed) {
    }
}
