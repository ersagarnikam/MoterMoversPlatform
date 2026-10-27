package uk.co.motormovers.platform.seed;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.springframework.stereotype.Component;

import uk.co.motormovers.platform.assessment.model.Exercise;
import uk.co.motormovers.platform.assessment.model.ExerciseScore;
import uk.co.motormovers.platform.assessment.model.Milestone;
import uk.co.motormovers.platform.assessment.model.MilestoneAchievement;
import uk.co.motormovers.platform.assessment.model.YearPoint;
import uk.co.motormovers.platform.assessment.repository.ExerciseRepository;
import uk.co.motormovers.platform.assessment.repository.ExerciseScoreRepository;
import uk.co.motormovers.platform.assessment.repository.MilestoneAchievementRepository;
import uk.co.motormovers.platform.assessment.repository.MilestoneRepository;
import uk.co.motormovers.platform.assessment.service.AssessmentContent;
import uk.co.motormovers.platform.assessment.util.ExercisePoints;
import uk.co.motormovers.platform.child.model.Child;
import uk.co.motormovers.platform.child.repository.ChildRepository;
import uk.co.motormovers.platform.school.model.SchoolClass;

/**
 * Demo records for Pat's assessments. A Yellow class (Ms Hardy's) has its start-of-colour exercise scores and its
 * start-of-year developmental assessment; a Green class is part way through the colour, with two children whose
 * scores have not moved on, so they show as "may benefit from extra support". The last three children of each
 * class are left unassessed so the demo has someone to start with.
 */
@Component
class DemoAssessments {

    /** Children in the Green class whose scores stay low, for the extra-support flag. */
    static final int LOW_SCORERS = 2;

    private final AssessmentContent content;
    private final ExerciseRepository exercises;
    private final ExerciseScoreRepository scores;
    private final MilestoneRepository milestones;
    private final MilestoneAchievementRepository achievements;
    private final ChildRepository children;
    private final Random random = new Random(7);

    DemoAssessments(AssessmentContent content, ExerciseRepository exercises, ExerciseScoreRepository scores,
            MilestoneRepository milestones, MilestoneAchievementRepository achievements, ChildRepository children) {
        this.content = content;
        this.exercises = exercises;
        this.scores = scores;
        this.milestones = milestones;
        this.achievements = achievements;
        this.children = children;
    }

    void seed(SchoolClass yellow, SchoolClass green, LocalDate today) {
        content.loadMissing();
        List<Child> yellowPupils = assessable(yellow);
        exerciseScores(yellowPupils, yellow, 0, today.minusDays(25), today);
        developmental(yellowPupils, 76, today.minusDays(20));
        exerciseScores(assessable(green), green, LOW_SCORERS, today.minusDays(28), today);
    }

    private List<Child> assessable(SchoolClass schoolClass) {
        List<Child> pupils = children.findBySchoolClassIdAndLeftOnIsNullOrderByInitials(schoolClass.getId());
        return pupils.subList(0, Math.max(0, pupils.size() - 3));
    }

    /**
     * Scores at every point the class has reached (in the demo a module takes a week from {@code started}). Most
     * children improve by a level or more at each point; the first {@code low} children stay at 1 or 2.
     */
    private void exerciseScores(List<Child> pupils, SchoolClass schoolClass, int low, LocalDate started,
            LocalDate today) {
        List<Exercise> list = exercises.findByStageOrderByNumber(schoolClass.getStage());
        for (int i = 0; i < pupils.size(); i++) {
            Child child = pupils.get(i);
            boolean lowScorer = i < low;
            for (Exercise e : list) {
                int score = lowScorer ? 1 : 2 + random.nextInt(2);
                for (String point : e.getPoints()) {
                    if (ExercisePoints.END.equals(point)) {
                        break;
                    }
                    LocalDate on = dateOf(point, started);
                    if (on.isAfter(today)) {
                        break;
                    }
                    scores.save(new ExerciseScore(UUID.randomUUID().toString(), child, e, point, score, on, "demo"));
                    score = lowScorer ? 2 : Math.min(Exercise.MAX_SCORE, score + 1 + random.nextInt(2));
                }
            }
        }
    }

    private static LocalDate dateOf(String point, LocalDate started) {
        return ExercisePoints.START.equals(point) ? started : started.plusDays(7L * moduleOf(point));
    }

    private static int moduleOf(String point) {
        return Integer.parseInt(point.substring(point.indexOf('_') + 1));
    }

    /** Movements up to the child's age band are secure, with a few of those just above it. */
    private void developmental(List<Child> pupils, int ageMonths, LocalDate on) {
        List<Milestone> all = milestones.findAllByOrderByChartAscPositionAsc();
        YearPoint point = YearPoint.on(on);
        for (Child child : pupils) {
            int age = ageMonths + random.nextInt(8);
            for (Milestone m : all) {
                boolean secure = m.getAgeToMonths() <= 60 || (m.getAgeFromMonths() <= age && random.nextInt(3) > 0);
                if (secure) {
                    achievements.save(new MilestoneAchievement(UUID.randomUUID().toString(), child, m, age, point,
                            on, "demo"));
                }
            }
        }
    }
}
