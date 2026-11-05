package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import uk.co.motormovers.platform.common.model.Region;
import uk.co.motormovers.platform.school.model.School;
import uk.co.motormovers.platform.school.repository.SchoolRepository;

/** A deployment started with PLATFORM_REGION=IE: Ireland's settings, clock and schools. */
@SpringBootTest(properties = "PLATFORM_REGION=IE")
@AutoConfigureMockMvc
class RegionIrelandTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Clock clock;

    @Autowired
    SchoolRepository schools;

    @Test
    void theSettingsAreIrelandsAndOpenToAnyone() throws Exception {
        mvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.region").value("IE"))
                .andExpect(jsonPath("$.country").value("Ireland"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.locale").value("en-IE"))
                .andExpect(jsonPath("$.taxLabel").value("VAT"))
                .andExpect(jsonPath("$.taxPercent").value(23))
                .andExpect(jsonPath("$.schoolYear.lastMonth").value(6))
                .andExpect(jsonPath("$.yearNames[1]").value("Junior Infants"))
                .andExpect(jsonPath("$.legalEntity.name").value("To be confirmed"));
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Europe/Dublin"));
    }

    @Test
    void anIrishSchoolIsStoredWithoutAMigration() {
        School saved = schools.save(new School("Scoil Bhríde", null, Region.IE));
        assertThat(schools.findById(saved.getId()).orElseThrow().getRegion()).isEqualTo(Region.IE);
    }
}
