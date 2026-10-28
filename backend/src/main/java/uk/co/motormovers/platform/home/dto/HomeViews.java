package uk.co.motormovers.platform.home.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import uk.co.motormovers.platform.assessment.dto.AssessmentViews;
import uk.co.motormovers.platform.assessment.model.Chart;
import uk.co.motormovers.platform.common.model.ColourStage;

/** Motor Movers at Home: what the parent app receives and sends. */
public final class HomeViews {

    /** Shown wherever a home child's check or progress is. */
    public static final String NOT_A_DIAGNOSIS =
            "Not a diagnosis. This shows how your child is getting on with the programme's movements, not a test.";

    /**
     * The gentle prompt in place of the school's extra-support flag. Pat to approve the wording; it names no cause and
     * points to people who know the child.
     */
    public static final String SUPPORT_PROMPT = "Some of these movements are taking a little longer. That is common: "
            + "every child moves at their own pace, and little and often helps. If you would like to talk it through, "
            + "your child's nursery, school or health visitor can help.";

    /** The parent's words for Pat's four levels (1 Not yet, 2 Developing, 3 Nearly there, 4 Confident and accurate). */
    public static final List<String> LEVELS = List.of("Not yet", "Getting there", "Nearly", "Got it");

    private HomeViews() {
    }

    public static String level(Integer score) {
        return score == null ? null : LEVELS.get(score - 1);
    }

    // --- The family -------------------------------------------------------------------------------------------------

    public record HomeChild(Long id, String name, int ageMonths, ColourStage stage, String stageName,
            LocalDate checkedOn, boolean checkDue) {
    }

    public record Member(Long userId, String name, boolean owner, boolean you) {
    }

    /** {@code enabled}: the home programme is switched on for this deployment. */
    public record Overview(boolean enabled, List<HomeChild> children, List<Member> members, boolean owner,
            int maxChildren) {
    }

    public record ChildIn(String name, Integer ageMonths) {
    }

    /** sentTo is the address the code was emailed to, or null when the parent will pass it on themselves. */
    public record Invite(String code, LocalDate expiresOn, String sentTo) {
    }

    /** Optional: an address to email the code to. */
    public record InviteIn(@Email @Size(max = 200) String email) {
    }

    public record CodeIn(String code) {
    }

    // --- The starting check -----------------------------------------------------------------------------------------

    public record Question(Long milestoneId, Chart chart, String chartLabel, String band, String description,
            boolean secure) {
    }

    public record Check(Long childId, String name, int ageMonths, String banner, List<Question> questions) {
    }

    /** {@code answer}: YES, NOT_YET or NOT_SURE. Only YES is kept (as a secure movement, with the age). */
    public record Answer(Long milestoneId, String answer) {
    }

    public record CheckIn(List<Answer> answers) {
    }

    public record CheckResult(ColourStage suggested, String suggestedName, ColourStage forAge, String forAgeName,
            boolean earlier, int yes, int notYet, int notSure, String explanation) {
    }

    public record StageIn(ColourStage stage) {
    }

    // --- The daily session ------------------------------------------------------------------------------------------

    public record Level(int score, String label) {
    }

    public record Review(String point, String label, List<AssessmentViews.ExerciseView> exercises) {
    }

    public record Support(boolean show, String message) {
    }

    /**
     * Today for one child. With Pat's exercises for the colour ({@code hasExercises}), the session is
     * {@code exercises}; without them, the app shows the colour's home activities instead. {@code firstTime}: the
     * exercises to rate after doing them for the first time. {@code canRate} is false for a child linked to a school,
     * whose teacher does the assessing.
     */
    public record Today(Long childId, String name, ColourStage stage, String stageName, boolean hasExercises,
            int module, int modules, int sessionInModule, int sessionsPerModule, boolean colourComplete,
            List<AssessmentViews.ExerciseView> exercises, List<Long> firstTime, boolean canRate, boolean doneToday,
            List<Boolean> counterGrid, int daysDone, int counterDays, long sessionsDone, Review review,
            boolean checkDue, Support support, List<Level> levels) {
    }

    public record SessionIn(String clientId, Long childId, LocalDate doneOn) {
    }

    public record SubmitResult(int saved, int duplicates) {
    }

    // --- Progress ---------------------------------------------------------------------------------------------------

    public record ChartProgress(Chart chart, String label, int secure, int total, List<String> recentlySecure) {
    }

    /** The first and latest rating of an exercise, in the parent's words. Never compared with other children. */
    public record ExerciseProgress(Long exerciseId, int number, String title, String first, String latest,
            boolean improved) {
    }

    public record Progress(HomeChild child, String banner, List<ChartProgress> charts, ColourStage stage,
            String stageName, List<ExerciseProgress> exercises, long sessionsDone, Support support) {
    }
}
