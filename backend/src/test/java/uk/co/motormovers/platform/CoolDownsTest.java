package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;
import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.home.service.CoolDowns;

/** Which cool-down ends a home session (Pat, 2026-11-05). */
class CoolDownsTest {

    private final CoolDowns coolDowns = new CoolDowns(JsonMapper.builder().build());

    @Test
    void greenEndsWithCoolDownOneUntilALaterModuleHasItsOwn() {
        assertThat(coolDowns.forModule(ColourStage.GREEN, 1).title()).isEqualTo("Cool Down 1");
        assertThat(coolDowns.forModule(ColourStage.GREEN, 4).title()).isEqualTo("Cool Down 1");
    }

    @Test
    void rhymeColoursAndColoursWithoutOneYetHaveNone() {
        assertThat(coolDowns.forModule(ColourStage.RED, 1)).isNull();
        assertThat(coolDowns.forModule(ColourStage.YELLOW, 3)).isNull();
        // Blue and Indigo have cool-downs in Pat's plans, not sent yet; Violet was not named.
        assertThat(coolDowns.forModule(ColourStage.BLUE, 1)).isNull();
        assertThat(coolDowns.forModule(ColourStage.VIOLET, 1)).isNull();
    }
}
