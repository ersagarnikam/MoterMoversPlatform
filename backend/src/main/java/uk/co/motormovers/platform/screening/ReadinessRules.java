package uk.co.motormovers.platform.screening;

import java.util.List;

import uk.co.motormovers.platform.common.ColourStage;

/**
 * How scores turn into bands and a suggested starting stage. These thresholds are placeholders
 * until Pat and Laura confirm which screening items to use and how they map to the stages.
 */
public final class ReadinessRules {

    private ReadinessRules() {
    }

    /** Average of scores as a fraction of the maximum, 0.0 to 1.0. */
    public static double fraction(List<Integer> scores) {
        if (scores.isEmpty()) {
            return 0;
        }
        return scores.stream().mapToInt(Integer::intValue).average().orElse(0) / ScreeningItem.MAX_SCORE;
    }

    public static ReadinessBand band(double fraction) {
        if (fraction < 0.4) {
            return ReadinessBand.EMERGING;
        }
        if (fraction < 0.75) {
            return ReadinessBand.DEVELOPING;
        }
        return ReadinessBand.SECURE;
    }

    public static ColourStage suggestedStage(double overallFraction) {
        ColourStage[] stages = ColourStage.values();
        int index = (int) Math.floor(overallFraction * stages.length);
        return stages[Math.max(0, Math.min(stages.length - 1, index))];
    }
}
