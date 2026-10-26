package uk.co.motormovers.platform.assessment.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import uk.co.motormovers.platform.assessment.model.Chart;
import uk.co.motormovers.platform.assessment.model.Exercise;
import uk.co.motormovers.platform.assessment.model.Milestone;
import uk.co.motormovers.platform.assessment.model.YearPoint;
import uk.co.motormovers.platform.assessment.util.ExercisePoints;
import uk.co.motormovers.platform.common.model.ColourStage;

public final class AssessmentViews {

    /** Shown on every assessment profile, permanently. */
    public static final String NOT_A_DIAGNOSIS =
            "Not a diagnosis. Progress with the programme's movements, not a clinical test.";

    /** The only wording the flag uses: it never names a cause. */
    public static final String EXTRA_SUPPORT = "May benefit from extra support";

    private AssessmentViews() {
    }

    // --- Programme content ---------------------------------------------------------------------------------------

    public record MilestoneView(Long id, Chart chart, String chartLabel, int position, String band,
            int ageFromMonths, int ageToMonths, String description) {

        public static MilestoneView of(Milestone m) {
            return new MilestoneView(m.getId(), m.getChart(), m.getChart().label(), m.getPosition(), m.getBand(),
                    m.getAgeFromMonths(), m.getAgeToMonths(), m.getDescription());
        }
    }

    public record PointView(String point, String label) {
    }

    public record ExerciseView(Long id, ColourStage stage, int number, String title, String instructions,
            Map<Integer, String> levels, List<PointView> points) {

        public static ExerciseView of(Exercise e) {
            return new ExerciseView(e.getId(), e.getStage(), e.getNumber(), e.getTitle(), e.getInstructions(),
                    e.getLevels(), e.getPoints().stream()
                            .map(p -> new PointView(p, ExercisePoints.label(p, e.getStage()))).toList());
        }
    }

    // --- Recording (both are sent through the device's offline queue) -------------------------------------------

    public record ExerciseScoreIn(String clientId, Long childId, Long exerciseId, String point, int score,
            LocalDate assessedOn) {
    }

    public record AchievementIn(String clientId, Long childId, Long milestoneId, int ageMonths, YearPoint yearPoint,
            LocalDate achievedOn) {
    }

    public record SubmitResult(int saved, int duplicates) {
    }

    public record CommentIn(Chart chart, YearPoint yearPoint, String note) {
    }

    public record SummaryIn(ColourStage stage, String personalComment, String academicComment) {
    }

    // --- The class sheet: one exercise at a time, the whole class -------------------------------------------------

    public record ScoreCell(Long exerciseId, String point, int score, LocalDate assessedOn) {
    }

    public record SheetChild(Long id, String name, List<ScoreCell> scores, boolean extraSupport) {
    }

    public record ClassSheet(Long classId, String className, ColourStage stage, String stageName,
            List<ExerciseView> exercises, List<SheetChild> children) {
    }

    // --- A child's developmental charts --------------------------------------------------------------------------

    public record Achieved(int ageMonths, YearPoint yearPoint, String yearPointLabel, int schoolYear,
            LocalDate achievedOn) {
    }

    public record ChartMilestone(Long id, int position, String band, int ageFromMonths, int ageToMonths,
            String description, Achieved achieved) {
    }

    public record CommentView(YearPoint yearPoint, String yearPointLabel, int schoolYear, String note,
            LocalDate writtenOn) {
    }

    public record ChartView(Chart chart, String label, int secure, int total, List<ChartMilestone> milestones,
            List<CommentView> comments) {
    }

    public record Development(Long childId, String name, String className, String banner, int schoolYear,
            YearPoint suggestedYearPoint, List<ChartView> charts) {
    }

    // --- The assessment profile: both assessments ----------------------------------------------------------------

    public record ChartSummary(Chart chart, String label, int secure, int total, Map<YearPoint, Integer> thisYear) {
    }

    public record PointScore(String point, String label, Integer score, LocalDate assessedOn) {
    }

    public record ExerciseRow(Long exerciseId, int number, String title, List<PointScore> points) {
    }

    /** Pat's summary sheet: the start and end totals out of 4 per exercise. */
    public record Totals(int start, int startAssessed, int end, int endAssessed, int max) {
    }

    public record ExtraSupport(boolean flagged, String label, List<String> reasons) {
    }

    public record Summary(String personalComment, String academicComment, LocalDate writtenOn) {
    }

    public record Profile(Long childId, String reference, String name, String className, String banner,
            ColourStage currentStage, ColourStage stage, String stageName, List<ColourStage> stagesWithExercises,
            List<ChartSummary> development, List<ExerciseRow> exercises, Totals totals, ExtraSupport extraSupport,
            Summary summary) {
    }

    // --- The head's overview -------------------------------------------------------------------------------------

    /**
     * One class: how many children have a developmental assessment at each point this school year, movements
     * secured this year, and the latest exercise scores as the number of children's scores at each level.
     */
    public record ClassOverview(Long classId, String name, ColourStage stage, String stageName, int children,
            Map<YearPoint, Integer> developmental, int movementsSecured, Map<Integer, Integer> scoreLevels,
            int extraSupport) {
    }

    public record FlaggedChild(Long childId, String name, String className, List<String> reasons) {
    }

    public record SchoolOverview(Long schoolId, String schoolName, int schoolYear, List<ClassOverview> classes,
            List<FlaggedChild> extraSupport) {
    }
}
