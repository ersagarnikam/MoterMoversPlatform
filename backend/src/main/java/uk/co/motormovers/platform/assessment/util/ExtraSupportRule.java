package uk.co.motormovers.platform.assessment.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import uk.co.motormovers.platform.common.model.ColourStage;

/**
 * Pat: "If the child has a pattern of low scores this would flag when additional intervention is needed." Until
 * Pat agrees the exact rule, the suggested default in docs/assessment-design.md applies. A child may benefit from
 * extra support when either
 * <ul>
 * <li>they score 1 or 2 on the same exercise at two points in a row, or</li>
 * <li>they score 1 or 2 on half or more of the exercises assessed at the end of a module or of the colour (at least
 * {@value #MIN_EXERCISES_AT_A_POINT} exercises).</li>
 * </ul>
 * The flag never names a cause: it says only that the child may benefit from extra support, and why.
 */
public final class ExtraSupportRule {

    /** Scores at or below this count as low: 1 Not yet and 2 Developing. */
    public static final int LOW = 2;

    static final int MIN_EXERCISES_AT_A_POINT = 2;

    public record Mark(String exercise, String point, int score) {
    }

    public record Result(boolean flagged, List<String> reasons) {
    }

    private ExtraSupportRule() {
    }

    public static Result apply(ColourStage stage, List<Mark> marks) {
        List<String> reasons = new ArrayList<>();

        Map<String, List<Mark>> byExercise = marks.stream()
                .collect(Collectors.groupingBy(Mark::exercise, LinkedHashMap::new, Collectors.toList()));
        byExercise.forEach((exercise, list) -> {
            List<Mark> inOrder = list.stream().sorted((a, b) -> ExercisePoints.ORDER.compare(a.point(), b.point()))
                    .toList();
            for (int i = 1; i < inOrder.size(); i++) {
                Mark before = inOrder.get(i - 1);
                Mark now = inOrder.get(i);
                if (before.score() <= LOW && now.score() <= LOW) {
                    reasons.add(exercise + ": " + before.score() + " at " + ExercisePoints.label(before.point(), stage)
                            + ", then " + now.score() + " at " + ExercisePoints.label(now.point(), stage));
                    break;
                }
            }
        });

        Map<String, List<Mark>> byPoint = marks.stream().filter(m -> !ExercisePoints.START.equals(m.point()))
                .collect(Collectors.groupingBy(Mark::point, () -> new TreeMap<>(ExercisePoints.ORDER),
                        Collectors.toList()));
        byPoint.forEach((point, list) -> {
            long low = list.stream().filter(m -> m.score() <= LOW).count();
            if (list.size() >= MIN_EXERCISES_AT_A_POINT && low * 2 >= list.size()) {
                reasons.add("1 or 2 on " + low + " of " + list.size() + " exercises at the "
                        + ExercisePoints.label(point, stage).toLowerCase(Locale.ROOT).replace(
                                stage.label().toLowerCase(Locale.ROOT), stage.label()));
            }
        });
        return new Result(!reasons.isEmpty(), reasons);
    }
}
