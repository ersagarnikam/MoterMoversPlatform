package uk.co.motormovers.platform.training.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Quizzes between live sessions: as the team writes them, as learners take them, and their results. */
public final class QuizViews {

    private QuizViews() {
    }

    /** {@code correctOption} counts from 0. */
    public record QuestionInput(@NotBlank @Size(max = 500) String text,
            @NotNull @Size(min = 2, max = 6) List<@NotBlank @Size(max = 300) String> options, int correctOption) {
    }

    public record QuizInput(@Min(1) int afterSession, @NotBlank @Size(max = 200) String title,
            @Min(1) @Max(100) int passPercent, @NotEmpty @Size(max = 30) List<@Valid QuestionInput> questions) {
    }

    /** A quiz as the team edits it, with the answers, and how many attempts learners have made. */
    public record QuizAdminView(Long id, int afterSession, String title, int passPercent,
            List<QuestionInput> questions, long attempts) {
    }

    /**
     * A quiz as a learner sees it in their course: {@code open} once its live session has started; {@code bestScore}
     * null until they have taken it.
     */
    public record QuizSummary(Long id, int afterSession, String title, int questions, int passPercent, boolean open,
            Integer bestScore, boolean passed) {
    }

    /** The questions without their answers. */
    public record QuizPaper(Long id, String title, int passPercent, List<PaperQuestion> questions) {
    }

    public record PaperQuestion(int number, String text, List<String> options) {
    }

    /** One answer per question, in order: the chosen option, counting from 0. */
    public record Answers(@NotNull List<Integer> answers) {
    }

    /**
     * The result of an attempt. {@code wrong} lists the numbers of the questions answered wrongly, so the learner
     * can look again without being given the answers. {@code certificateIssued} is true when this pass completed
     * the course.
     */
    public record AttemptResult(int scorePercent, boolean passed, int correct, int questions, List<Integer> wrong,
            boolean certificateIssued) {
    }
}
