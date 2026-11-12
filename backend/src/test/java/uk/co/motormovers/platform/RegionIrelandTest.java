package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import uk.co.motormovers.platform.assessment.model.YearPoint;
import uk.co.motormovers.platform.assessment.util.SchoolYear;
import uk.co.motormovers.platform.common.model.Region;
import uk.co.motormovers.platform.notification.service.PushService;
import uk.co.motormovers.platform.school.model.School;
import uk.co.motormovers.platform.school.repository.SchoolRepository;
import uk.co.motormovers.platform.training.service.SessionReminderJob;

/** A deployment started with PLATFORM_REGION=IE: Ireland's settings, clock and schools. */
@SpringBootTest(properties = "PLATFORM_REGION=IE")
@AutoConfigureMockMvc
class RegionIrelandTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Clock clock;

    @Autowired
    SchoolYear schoolYear;

    @Autowired
    SessionReminderJob job;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    PushService push;

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
                .andExpect(jsonPath("$.dataHeldIn").value("the EU"))
                .andExpect(jsonPath("$.taxLabel").value("VAT"))
                .andExpect(jsonPath("$.taxPercent").value(23))
                .andExpect(jsonPath("$.schoolYear.lastMonth").value(6))
                .andExpect(jsonPath("$.yearNames[1]").value("Junior Infants"))
                .andExpect(jsonPath("$.termLabel").value("This term"))
                .andExpect(jsonPath("$.legalEntity.name").value("To be confirmed"));
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Europe/Dublin"));
        assertThat(schoolYear.pointOn(LocalDate.of(2027, 4, 1))).isEqualTo(YearPoint.END);
    }

    @Test
    void aSessionNoticeSaysIrishTimeNotUkTime() {
        jdbc.update("UPDATE cohort_session SET opening_push_sent_at = NULL");
        job.announceOpening();
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(push, atLeastOnce()).notify(any(), anyString(), body.capture(), anyString());
        assertThat(body.getAllValues()).anyMatch(b -> b.contains("(Irish time)")).noneMatch(b -> b.contains("UK"));
    }

    @Test
    void anIrishSchoolIsStoredWithoutAMigration() {
        School saved = schools.save(new School("Scoil Bhríde", null, Region.IE));
        assertThat(schools.findById(saved.getId()).orElseThrow().getRegion()).isEqualTo(Region.IE);
    }
}
