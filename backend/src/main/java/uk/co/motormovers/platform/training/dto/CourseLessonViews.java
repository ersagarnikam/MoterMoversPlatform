package uk.co.motormovers.platform.training.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import uk.co.motormovers.platform.content.model.ContentStatus;
import uk.co.motormovers.platform.content.util.WordingGuard;

/** A recorded course's lessons, as the team writes them and as a learner takes them (go-live task 89). */
public final class CourseLessonViews {

    private CourseLessonViews() {
    }

    /**
     * What the team sends for a lesson. The film and the slides are each a pasted {@code https} link or an uploaded
     * file's key, never both; a lesson may have neither.
     */
    public record LessonInput(@Min(1) @Max(200) int position, @NotBlank @Size(max = 200) String title,
            @Size(max = 20_000) String body, @Min(1) @Max(600) int minutes, @Size(max = 500) String videoUrl,
            @Size(max = 300) String videoKey, @Size(max = 500) String slidesUrl, @Size(max = 300) String slidesKey) {
    }

    /** A lesson on the team's course page: drafts too, with what the wording check found and links to check it. */
    public record AdminLesson(Long id, int position, String title, String body, List<String> paragraphs,
            int minutes, ContentStatus status, String reviewedBy, Instant reviewedAt,
            List<WordingGuard.Violation> violations, String videoUrl, String pastedVideoUrl, boolean videoUploaded,
            String slidesUrl, String pastedSlidesUrl, boolean slidesUploaded, long learnersDone) {
    }

    /**
     * A lesson as its learner sees it. {@code open}: it can be watched now (every lesson before it is done).
     * {@code videoUrl} and {@code slidesUrl} are sent only for an open lesson; {@code hasFilm} says whether there is a
     * film to play to the end before the lesson can be marked done.
     */
    public record Lesson(Long id, int position, String title, List<String> paragraphs, int minutes, boolean hasFilm,
            String videoUrl, String slidesUrl, boolean open, boolean done) {
    }

    /**
     * A learner's place on a recorded course: its lessons in order and where the learner has got to.
     * {@code quizzesOpen}: every lesson is done, so the course's quizzes can be taken.
     */
    public record Player(Long enrolmentId, Long courseId, String courseTitle, String learnerName,
            List<Lesson> lessons, int lessonsDone, int totalMinutes, boolean quizzesOpen, int quizzes,
            int quizzesPassed, LocalDate certificateIssuedOn) {
    }
}
