package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import uk.co.motormovers.platform.assessment.model.YearPoint;
import uk.co.motormovers.platform.assessment.util.SchoolYear;

/** A deployment started with PLATFORM_REGION=AU: Australia's settings, on Queensland time. */
@SpringBootTest(properties = "PLATFORM_REGION=AU")
@AutoConfigureMockMvc
class RegionAustraliaTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Clock clock;

    @Autowired
    SchoolYear schoolYear;

    @Test
    void theSettingsAreAustraliasOnQueenslandTime() throws Exception {
        mvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.region").value("AU"))
                .andExpect(jsonPath("$.currency").value("AUD"))
                .andExpect(jsonPath("$.taxLabel").value("GST"))
                .andExpect(jsonPath("$.taxPercent").value(10))
                .andExpect(jsonPath("$.timeZone").value("Australia/Brisbane"))
                .andExpect(jsonPath("$.schoolYear.firstMonth").value(1))
                .andExpect(jsonPath("$.schoolYear.terms").value(4))
                .andExpect(jsonPath("$.yearNames[0]").value("Kindy"));
        // Not Sydney: Queensland has no daylight saving.
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Australia/Brisbane"));
        // The school year is the calendar year: this deployment's bean follows the settings.
        assertThat(schoolYear.pointOn(LocalDate.of(2027, 1, 27))).isEqualTo(YearPoint.START);
        assertThat(schoolYear.yearOf(LocalDate.of(2027, 12, 31))).isEqualTo(2027);
    }
}
