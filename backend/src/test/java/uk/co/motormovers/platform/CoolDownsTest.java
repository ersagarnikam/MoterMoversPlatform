package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;
import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.home.service.CoolDowns;

/** Which cool-down ends a session (Pat's session plans). */
class CoolDownsTest {

    private final CoolDowns coolDowns = new CoolDowns(JsonMapper.builder().build());

    @Test
    void eachModuleHasItsOwnAndAModulePastTheLastUsesTheLatest() {
        assertThat(coolDowns.forModule(ColourStage.GREEN, 1).title()).isEqualTo("Cool Down 1");
        assertThat(coolDowns.forModule(ColourStage.GREEN, 4).title()).isEqualTo("Cool Down 4");
        assertThat(coolDowns.forModule(ColourStage.GREEN, 9).title()).isEqualTo("Cool Down 6");
    }

    @Test
    void rhymeColoursHaveNoneOfTheirOwn() {
        assertThat(coolDowns.forModule(ColourStage.RED, 1)).isNull();
        assertThat(coolDowns.forModule(ColourStage.ORANGE, 2)).isNull();
        assertThat(coolDowns.forModule(ColourStage.YELLOW, 3)).isNull();
    }
}
