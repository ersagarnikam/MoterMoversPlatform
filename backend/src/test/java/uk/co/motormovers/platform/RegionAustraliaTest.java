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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import uk.co.motormovers.platform.assessment.model.YearPoint;
import uk.co.motormovers.platform.assessment.util.SchoolYear;
import uk.co.motormovers.platform.notification.service.PushService;
import uk.co.motormovers.platform.training.repository.CohortRepository;
import uk.co.motormovers.platform.training.repository.CohortSessionRepository;
import uk.co.motormovers.platform.training.service.SessionReminderJob;

/** A deployment started with PLATFORM_REGION=AU: Australia's settings, on Queensland time. */
// Its own database: the demo data is loaded once per database, on the clock of the first context to start, so a
// context with another clock or time zone must not share the default one (task 83).
@SpringBootTest(properties = { "PLATFORM_REGION=AU", "spring.datasource.url=jdbc:h2:mem:regionau;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH" })
@AutoConfigureMockMvc
@Import(MonthlyInRegion.class)
class RegionAustraliaTest {

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
    MonthlyInRegion monthly;

    /** Go-live task 68: a month of the home programme, in this country's currency with its tax. */
    @Test
    void aMonthOfASubscriptionIsInDollarsWithGst() {
        monthly.aMonthIsPaidWithTax("aud", "gbp", 2_750, 250);
    }

    @Autowired
    CohortRepository cohorts;

    @Autowired
    CohortSessionRepository cohortSessions;

    @Test
    void aSessionNoticeSaysQueenslandTimeNotUkTime() {
        // Its own session in the next 15 minutes, whenever the demo was loaded (task 86).
        OpeningSession.inFiveMinutes(cohorts, cohortSessions, jdbc, clock);
        job.announceOpening();
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(push, atLeastOnce()).notify(any(), anyString(), body.capture(), anyString());
        assertThat(body.getAllValues()).anyMatch(b -> b.contains("(Queensland time)")).noneMatch(b -> b.contains("UK"));
    }

    @Test
    void theSettingsAreAustraliasOnQueenslandTime() throws Exception {
        mvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.region").value("AU"))
                .andExpect(jsonPath("$.currency").value("AUD"))
                .andExpect(jsonPath("$.dataHeldIn").value("Australia"))
                .andExpect(jsonPath("$.creditNoteTitle").value("Adjustment note"))
                .andExpect(jsonPath("$.taxLabel").value("GST"))
                .andExpect(jsonPath("$.taxPercent").value(10))
                .andExpect(jsonPath("$.timeZone").value("Australia/Brisbane"))
                .andExpect(jsonPath("$.schoolYear.firstMonth").value(1))
                .andExpect(jsonPath("$.schoolYear.terms").value(4))
                .andExpect(jsonPath("$.yearNames[0]").value("Preschool/Kindergarten"))
                .andExpect(jsonPath("$.termLabel").value("This term"));
        // Not Sydney: Queensland has no daylight saving.
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Australia/Brisbane"));
        // The school year is the calendar year: this deployment's bean follows the settings.
        assertThat(schoolYear.pointOn(LocalDate.of(2027, 1, 27))).isEqualTo(YearPoint.START);
        assertThat(schoolYear.yearOf(LocalDate.of(2027, 12, 31))).isEqualTo(2027);
    }

    @Autowired
    uk.co.motormovers.platform.school.service.PlanService launchPlans;

    @Autowired
    uk.co.motormovers.platform.training.repository.CourseRepository launchCourses;

    @Autowired
    uk.co.motormovers.platform.home.repository.HomePassPriceRepository launchPassPrices;

    /** Go-live task 85: this country's launch prices, in cents. */
    @Test
    void anAustralianDeploymentStartsWithKaysPrices() {
        LaunchPricesTest.assertPrices(launchPlans, launchCourses, launchPassPrices, LocalDate.now(clock), 75_000, 120_000, 108_000, 20_000, 6_000, 10_000, 100_000);
    }
}
