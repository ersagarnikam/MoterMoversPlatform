package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.common.model.Region;
import uk.co.motormovers.platform.content.model.ContentStatus;
import uk.co.motormovers.platform.content.model.ProgrammeSession;
import uk.co.motormovers.platform.content.repository.ProgrammeSessionRepository;
import uk.co.motormovers.platform.school.model.Licence;
import uk.co.motormovers.platform.school.model.School;
import uk.co.motormovers.platform.school.model.SchoolClass;
import uk.co.motormovers.platform.school.repository.LicenceRepository;
import uk.co.motormovers.platform.school.repository.SchoolClassRepository;
import uk.co.motormovers.platform.school.repository.SchoolRepository;
import uk.co.motormovers.platform.school.service.PlanService;

/**
 * A session is shown and recorded only while one of the school's licences runs that day and covers the class's
 * colour. Each test sets up its own school and licences; the clock is fixed on a school day so recording works
 * whatever day the tests run.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LicenceCheckTest {

    /** Wednesday 4 November 2026, 10am UK time. */
    static final ZonedDateTime NOW = ZonedDateTime.of(2026, 11, 4, 10, 0, 0, 0, ZoneId.of("Europe/London"));

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW.toInstant(), NOW.getZone());
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    SchoolRepository schools;

    @Autowired
    SchoolClassRepository classes;

    @Autowired
    LicenceRepository licences;

    @Autowired
    ProgrammeSessionRepository content;

    @Autowired
    PlanService plans;

    private final LocalDate today = NOW.toLocalDate();

    @Test
    void aCurrentLicenceCoveringTheColourRecordsTheSession() throws Exception {
        ColourStage stage = stageWithSessions();
        SchoolClass schoolClass = classWithLicences(stage, licence(today.minusMonths(2), today.plusMonths(10), stage));

        complete(schoolClass, stage).andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(today.toString()));
        today(schoolClass).andExpect(jsonPath("$.stageLicensed").value(true))
                .andExpect(jsonPath("$.doneToday").value(true));
    }

    @Test
    void anExpiredLicenceIsRefused() throws Exception {
        ColourStage stage = stageWithSessions();
        SchoolClass schoolClass = classWithLicences(stage, licence(today.minusYears(1), today.minusDays(1), stage));

        refused(complete(schoolClass, stage));
        today(schoolClass).andExpect(jsonPath("$.stageLicensed").value(false))
                .andExpect(jsonPath("$.nextSession").doesNotExist());
    }

    @Test
    void aColourNotOnTheLicenceIsRefused() throws Exception {
        ColourStage stage = stageWithSessions();
        SchoolClass schoolClass = classWithLicences(stage,
                licence(today.minusMonths(2), today.plusMonths(10), otherThan(stage)));

        refused(complete(schoolClass, stage));
        today(schoolClass).andExpect(jsonPath("$.stageLicensed").value(false));
    }

    @Test
    void aLicenceStartingTomorrowIsRefused() throws Exception {
        ColourStage stage = stageWithSessions();
        SchoolClass schoolClass = classWithLicences(stage, licence(today.plusDays(1), today.plusYears(1), stage));

        refused(complete(schoolClass, stage));
        today(schoolClass).andExpect(jsonPath("$.stageLicensed").value(false));
    }

    @Test
    void aLaterRenewalForOtherColoursDoesNotHideTheCurrentLicence() throws Exception {
        ColourStage stage = stageWithSessions();
        SchoolClass schoolClass = classWithLicences(stage,
                licence(today.minusMonths(6), today.plusMonths(1), stage),
                licence(today.plusMonths(1).plusDays(1), today.plusMonths(13), otherThan(stage)));

        today(schoolClass).andExpect(jsonPath("$.stageLicensed").value(true))
                .andExpect(jsonPath("$.nextSession.id").exists());
        complete(schoolClass, stage).andExpect(status().isOk());
    }

    private void refused(ResultActions result) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "This class's colour is not covered by your school's licence today. Please ask your head teacher."));
    }

    /** A new school with the given licences and one class at the stage. */
    private SchoolClass classWithLicences(ColourStage stage, LicenceSpec... specs) {
        School school = schools.save(new School("Licence " + UUID.randomUUID(), null, Region.UK));
        for (LicenceSpec spec : specs) {
            licences.save(new Licence(school, plans.find("TRUST"), 0, spec.from(), spec.to(), EnumSet.of(spec.stage())));
        }
        return classes.save(new SchoolClass(school, "Licence L", "Year 1", null, stage));
    }

    private record LicenceSpec(LocalDate from, LocalDate to, ColourStage stage) {
    }

    private static LicenceSpec licence(LocalDate from, LocalDate to, ColourStage stage) {
        return new LicenceSpec(from, to, stage);
    }

    /** A colour with approved sessions in the demo content. */
    private ColourStage stageWithSessions() {
        return Arrays.stream(ColourStage.values())
                .filter(s -> !sessions(s).isEmpty())
                .findFirst().orElseThrow();
    }

    private static ColourStage otherThan(ColourStage stage) {
        return Arrays.stream(ColourStage.values()).filter(s -> s != stage).findFirst().orElseThrow();
    }

    private List<ProgrammeSession> sessions(ColourStage stage) {
        return content.findByStageAndStatusOrderByUnitAscDayAsc(stage, ContentStatus.APPROVED);
    }

    private ResultActions complete(SchoolClass schoolClass, ColourStage stage) throws Exception {
        Long sessionId = sessions(stage).getFirst().getId();
        return mvc.perform(post("/api/classes/" + schoolClass.getId() + "/sessions")
                .header("Authorization", bearer(admin()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":" + sessionId + ",\"absentChildIds\":[]}"));
    }

    private ResultActions today(SchoolClass schoolClass) throws Exception {
        return mvc.perform(get("/api/classes/" + schoolClass.getId() + "/today")
                .header("Authorization", bearer(admin()))).andExpect(status().isOk());
    }

    private String admin() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@motormovers.test\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");
        assertThat(token).isNotBlank();
        return token;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
