package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.screening.model.ReadinessBand;
import uk.co.motormovers.platform.screening.util.ReadinessRules;

class ReadinessRulesTest {

    @Test
    void bandsUseProgrammeTerms() {
        assertThat(ReadinessRules.band(ReadinessRules.fraction(List.of(0, 1)))).isEqualTo(ReadinessBand.EMERGING);
        assertThat(ReadinessRules.band(ReadinessRules.fraction(List.of(2, 2)))).isEqualTo(ReadinessBand.DEVELOPING);
        assertThat(ReadinessRules.band(ReadinessRules.fraction(List.of(3, 3)))).isEqualTo(ReadinessBand.SECURE);
    }

    @Test
    void suggestedStageStaysWithinTheSevenStages() {
        assertThat(ReadinessRules.suggestedStage(0)).isEqualTo(ColourStage.RED);
        assertThat(ReadinessRules.suggestedStage(0.5)).isEqualTo(ColourStage.GREEN);
        assertThat(ReadinessRules.suggestedStage(1.0)).isEqualTo(ColourStage.VIOLET);
    }
}
