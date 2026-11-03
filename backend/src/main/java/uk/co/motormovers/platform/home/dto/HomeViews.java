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
     * The gentle prompt in place of the school's extra-support flag, in Pat's words (2026-11-03): it names no cause
     * and points to people who know the child.
     */
    public static final String SUPPORT_PROMPT = "Some of these movements are taking a little longer. Children move "
            + "and develop at their own pace. You may wish to talk it through with your child's nursery or health "
            + "visitor.";

    /** Pat's safety note (2026-11-03), shown before the first session and kept on the session screen. */
    public static final String SAFETY_NOTE = "Before starting, make sure there is enough clear space for your child "
            + "to move safely and that an adult is present at all times to model the movements and supervise. "
            + "Encourage your child to listen to their body and complete each movement at a comfortable pace. If a "
            + "movement hurts or feels uncomfortable, stop and do not continue with that activity.";

    /** The parent's words for Pat's four levels. */
    public static final List<String> LEVELS = AssessmentViews.PARENT_LEVELS;

    private HomeViews() {
    }

    public static String level(Integer score) {
        return AssessmentViews.parentLevel(score);
    }

    // --- The family -------------------------------------------------------------------------------------------------

    public record HomeChild(Long id, String name, int ageMonths, ColourStage stage, String stageName,
            LocalDate checkedOn, boolean checkDue) {
    }

    public record Member(Long userId, String name, boolean owner, boolean you) {
    }

    /** {@code enabled}: the home programme is switched on for this deployment. */
    public record Availability(boolean enabled) {
    }

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

    /** {@code suggested}: the colour to start at, which comes from the child's age, not from the answers. */
    public record CheckResult(ColourStage suggested, String suggestedName, int yes, int notYet, int notSure,
            String explanation) {
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
     * {@code exercises}; without them, the app shows the colour's home activities instead. In a rhyme colour
     * ({@code rhymes}) {@code module} is the rhyme, done for a week; otherwise a module builds up over {@code weeks},
     * and {@code newThisWeek} are the exercises added this week. {@code progressLabel} and {@code sessionTitle} say
     * where the child is ("Module 2 of 6 · week 3 of 5"). {@code firstTime}: the exercises to rate after doing them for
     * the first time. {@code canRate} is false for a child linked to a school, whose teacher does the assessing.
     */
    public record Today(Long childId, String name, ColourStage stage, String stageName, boolean hasExercises,
            boolean rhymes, int module, int modules, int week, int weeks, int sessionInModule, int sessionsPerModule,
            boolean colourComplete, String progressLabel, String sessionTitle,
            List<AssessmentViews.ExerciseView> exercises, List<Long> newThisWeek, List<Long> firstTime,
            boolean canRate, boolean doneToday, List<Boolean> counterGrid, int daysDone, int counterDays,
            long sessionsDone, Review review, boolean checkDue, Support support, List<Level> levels,
            String safetyNote) {
    }

    public record SessionIn(String clientId, Long childId, LocalDate doneOn) {
    }

    public record SubmitResult(int saved, int duplicates) {
    }

    // --- Progress ---------------------------------------------------------------------------------------------------

    public record Progress(HomeChild child, String banner, List<AssessmentViews.ChartProgress> charts,
            ColourStage stage, String stageName, List<AssessmentViews.ExerciseProgress> exercises, long sessionsDone,
            Support support) {
    }
}
