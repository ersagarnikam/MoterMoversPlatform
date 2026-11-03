package uk.co.motormovers.platform.assessment.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import uk.co.motormovers.platform.assessment.dto.AssessmentViews;
import uk.co.motormovers.platform.assessment.model.Chart;
import uk.co.motormovers.platform.assessment.model.ExerciseScore;
import uk.co.motormovers.platform.assessment.model.Milestone;
import uk.co.motormovers.platform.assessment.model.MilestoneAchievement;
import uk.co.motormovers.platform.assessment.repository.ExerciseRepository;
import uk.co.motormovers.platform.assessment.repository.MilestoneAchievementRepository;
import uk.co.motormovers.platform.assessment.repository.MilestoneRepository;
import uk.co.motormovers.platform.assessment.util.ExercisePoints;
import uk.co.motormovers.platform.common.model.ColourStage;

/**
 * A child's assessments in a parent's words, for a home child and for a school child their parent is linked to:
 * movements secure on each chart, and each exercise's first and latest rating. Never a score, a total or a comparison
 * with other children. Callers check access first.
 */
@Component
public class ParentProgress {

    /** "New in the last few months": secure within this many days. */
    public static final int RECENT_DAYS = 91;

    private final MilestoneRepository milestones;
    private final MilestoneAchievementRepository achievements;
    private final ExerciseRepository exercises;

    public ParentProgress(MilestoneRepository milestones, MilestoneAchievementRepository achievements,
            ExerciseRepository exercises) {
        this.milestones = milestones;
        this.achievements = achievements;
        this.exercises = exercises;
    }

    /**
     * Movements secure on each chart. The recent list leaves out what the first record found (it was already there
     * when the child started), so it shows what has come since.
     */
    public List<AssessmentViews.ChartProgress> charts(Long childId, LocalDate today) {
        List<MilestoneAchievement> mine = achievements.findByChildId(childId);
        Map<Chart, Long> totals = milestones.findAll().stream()
                .collect(Collectors.groupingBy(Milestone::getChart, Collectors.counting()));
        LocalDate first = mine.stream().map(MilestoneAchievement::getAchievedOn).min(Comparator.naturalOrder())
                .orElse(today);
        LocalDate recent = today.minusDays(RECENT_DAYS);
        List<AssessmentViews.ChartProgress> charts = new ArrayList<>();
        for (Chart chart : Chart.values()) {
            List<MilestoneAchievement> on = mine.stream().filter(a -> a.getMilestone().getChart() == chart).toList();
            charts.add(new AssessmentViews.ChartProgress(chart, chart.label(), on.size(),
                    totals.getOrDefault(chart, 0L).intValue(),
                    on.stream()
                            .filter(a -> a.getAchievedOn().isAfter(first) && !a.getAchievedOn().isBefore(recent))
                            .sorted(Comparator.comparing((MilestoneAchievement a) -> a.getMilestone().getPosition()))
                            .map(a -> a.getMilestone().getDescription()).toList()));
        }
        return charts;
    }

    /** Each exercise of the stage the child has been rated on, with its first and latest rating. */
    public List<AssessmentViews.ExerciseProgress> exercises(ColourStage stage, List<ExerciseScore> theirs) {
        Map<Long, List<ExerciseScore>> byExercise = theirs.stream()
                .collect(Collectors.groupingBy(s -> s.getExercise().getId()));
        return exercises.findByStageOrderByNumber(stage).stream().filter(e -> byExercise.containsKey(e.getId()))
                .map(e -> {
                    List<ExerciseScore> list = byExercise.get(e.getId()).stream()
                            .sorted(Comparator.comparing(ExerciseScore::getPoint, ExercisePoints.ORDER)).toList();
                    int first = list.getFirst().getScore();
                    int latest = list.getLast().getScore();
                    return new AssessmentViews.ExerciseProgress(e.getId(), e.getNumber(), e.getTitle(),
                            AssessmentViews.parentLevel(first), AssessmentViews.parentLevel(latest), latest > first);
                }).toList();
    }
}
