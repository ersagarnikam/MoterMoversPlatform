package uk.co.motormovers.platform.screening.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.screening.model.MotorArea;
import uk.co.motormovers.platform.screening.model.ReadinessBand;
import uk.co.motormovers.platform.screening.model.ScreeningItem;

public final class ScreeningViews {

    /** Shown on every profile, permanently. */
    public static final String NOT_A_DIAGNOSIS =
            "Not a diagnosis. Readiness against programme stages, not a clinical test.";

    private ScreeningViews() {
    }

    public record Item(String code, int position, MotorArea area, String areaLabel, String title,
            String instructions, Map<Integer, String> levels) {

        public static Item of(ScreeningItem i) {
            return new Item(i.getCode(), i.getPosition(), i.getArea(), i.getArea().label(), i.getTitle(),
                    i.getInstructions(), i.getLevels());
        }
    }

    public record Score(String clientId, Long childId, String itemCode, int score, LocalDate screenedOn) {
    }

    public record SubmitResult(int saved, int duplicates) {
    }

    public record AreaResult(MotorArea area, String label, double fraction, ReadinessBand band, String bandLabel) {
    }

    public record RoundPoint(int round, LocalDate screenedOn, double overallFraction) {
    }

    public record Readiness(boolean screened, List<AreaResult> areas, List<RoundPoint> history,
            ColourStage suggestedStage) {
    }

    public record Profile(Long childId, String reference, String name, String className, String banner,
            boolean screened, ColourStage suggestedStage, String suggestedStageName, ColourStage currentStage,
            List<AreaResult> areas, List<RoundPoint> history) {
    }
}
