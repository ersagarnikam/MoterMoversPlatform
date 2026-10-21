package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

/** The team sets up courses and cohorts; learners book them from the catalogue. */
@SpringBootTest
@AutoConfigureMockMvc
class CourseAdminTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Clock clock;

    @Test
    void theTeamSetsUpACourseAndPractitionersCanBookIt() throws Exception {
        String admin = login("admin@motormovers.test");
        String course = send(admin, post("/api/admin/courses"), courseJson("PROFESSIONAL", "Early years refresher",
                "A short course for early-years staff.", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cohorts.length()").value(0))
                .andReturn().getResponse().getContentAsString();
        Integer courseId = JsonPath.read(course, "$.id");

        String startsOn = LocalDate.now(clock).plusDays(14).toString();
        String withCohort = send(admin, post("/api/admin/courses/" + courseId + "/cohorts"),
                cohortJson("Spring cohort", startsOn, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cohorts[0].name").value("Spring cohort"))
                .andExpect(jsonPath("$.cohorts[0].booked").value(0))
                .andReturn().getResponse().getContentAsString();
        Integer cohortId = JsonPath.read(withCohort, "$.cohorts[0].id");

        String practitioner = login("practitioner@example.test");
        mvc.perform(get("/api/training/courses").header("Authorization", bearer(practitioner)))
                .andExpect(jsonPath("$.courses[?(@.title == 'Early years refresher')].cohorts[0].seatsLeft")
                        .value(2));
        for (String learner : new String[] {practitioner, login("teacher@oakfield.test")}) {
            mvc.perform(post("/api/training/cohorts/" + cohortId + "/enrol").header("Authorization", bearer(learner)))
                    .andExpect(status().isOk());
        }

        // Both places are booked, so the cohort cannot shrink below two.
        send(admin, put("/api/admin/cohorts/" + cohortId), cohortJson("Spring cohort", startsOn, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("already has 2 places booked")));
        send(admin, put("/api/admin/cohorts/" + cohortId), cohortJson("Spring cohort (moved)", startsOn, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cohorts[0].name").value("Spring cohort (moved)"))
                .andExpect(jsonPath("$.cohorts[0].booked").value(2));

        send(admin, put("/api/admin/courses/" + courseId), courseJson("PROFESSIONAL", "Early years refresher",
                "Updated description.", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated description."));
    }

    @Test
    void coursesKeepTheirTrackAndParentCoursesHaveNoCertificate() throws Exception {
        String admin = login("admin@motormovers.test");
        send(admin, post("/api/admin/courses"), courseJson("PARENT", "Family evenings", "For parents.", true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("certificate")));

        String course = send(admin, post("/api/admin/courses"),
                courseJson("PARENT", "Family evenings", "For parents.", false))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        send(admin, put("/api/admin/courses/" + JsonPath.read(course, "$.id")),
                courseJson("PROFESSIONAL", "Family evenings", "For parents.", false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("track")));
    }

    @Test
    void diagnosticWordingAndPastStartDatesAreRefused() throws Exception {
        String admin = login("admin@motormovers.test");
        send(admin, post("/api/admin/courses"), courseJson("PROFESSIONAL", "Spotting dyspraxia",
                "Learn the signs.", true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Wording check")));

        String course = mvc.perform(get("/api/admin/courses").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer courseId = JsonPath.read(course, "$[0].id");
        send(admin, post("/api/admin/courses/" + courseId + "/cohorts"),
                cohortJson("Too late", LocalDate.now(clock).minusDays(1).toString(), 10))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theTeamMarksAttendanceAndTheAssessmentAndTheCertificateFollows() throws Exception {
        String admin = login("admin@motormovers.test");
        String course = send(admin, post("/api/admin/courses"), courseJson("PROFESSIONAL", "Certificate course",
                "Four live sessions and an assessment.", true))
                .andReturn().getResponse().getContentAsString();
        String withCohort = send(admin, post("/api/admin/courses/" + JsonPath.read(course, "$.id") + "/cohorts"),
                cohortJson("Autumn cohort", LocalDate.now(clock).plusDays(7).toString(), 5))
                .andReturn().getResponse().getContentAsString();
        Integer cohortId = JsonPath.read(withCohort, "$.cohorts[0].id");

        String practitioner = login("practitioner@example.test");
        String booking = mvc.perform(post("/api/training/cohorts/" + cohortId + "/enrol")
                        .header("Authorization", bearer(practitioner)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer enrolmentId = JsonPath.read(booking, "$.id");

        mvc.perform(get("/api/admin/cohorts/" + cohortId + "/enrolments").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].learnerEmail").value("practitioner@example.test"))
                .andExpect(jsonPath("$[0].sessionsAttended").value(0));

        // The cohort has one dated session per live session of the course, weekly from its start date.
        List<Integer> sessions = JsonPath.read(withCohort, "$.cohorts[0].sessions[*].id");
        assertThat(sessions).hasSize(4);
        mark(admin, sessions.get(0), enrolmentId, true);
        mark(admin, sessions.get(1), enrolmentId, true).andExpect(jsonPath("$.sessionsAttended").value(2));
        // A learner ticked by mistake can be unticked.
        mark(admin, sessions.get(1), enrolmentId, false).andExpect(jsonPath("$.sessionsAttended").value(1));
        for (Integer session : sessions) {
            mark(admin, session, enrolmentId, true);
        }
        // Ticking the same session twice counts once; the certificate waits for the assessment.
        mark(admin, sessions.get(3), enrolmentId, true)
                .andExpect(jsonPath("$.sessionsAttended").value(4))
                .andExpect(jsonPath("$.attendedSessions").value(org.hamcrest.Matchers.contains(1, 2, 3, 4)))
                .andExpect(jsonPath("$.certificateIssuedOn").doesNotExist());

        send(admin, post("/api/admin/enrolments/" + enrolmentId + "/assessment"), "{\"passed\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.certificateIssuedOn").value(LocalDate.now(clock).toString()))
                .andExpect(jsonPath("$.certificateExpiresOn").value(LocalDate.now(clock).plusYears(1).toString()));

        // Once issued, the certificate stands.
        mark(admin, sessions.get(0), enrolmentId, false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("certificate has been issued")));
        send(admin, post("/api/admin/enrolments/" + enrolmentId + "/assessment"), "{\"passed\":false}")
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/training/enrolments/mine").header("Authorization", bearer(practitioner)))
                .andExpect(jsonPath("$[?(@.cohortName == 'Autumn cohort')].certificateIssuedOn")
                        .value(LocalDate.now(clock).toString()));
        // Learners cannot keep the register.
        mark(practitioner, sessions.get(0), enrolmentId, true).andExpect(status().isForbidden());
    }

    @Test
    void onlyTheTeamManagesCourses() throws Exception {
        String head = login("head@oakfield.test");
        mvc.perform(get("/api/admin/courses").header("Authorization", bearer(head)))
                .andExpect(status().isForbidden());
        send(head, post("/api/admin/courses"), courseJson("PROFESSIONAL", "Head's own course", "Mine.", false))
                .andExpect(status().isForbidden());
    }

    private static String courseJson(String track, String title, String description, boolean certificate) {
        return "{\"track\":\"" + track + "\",\"title\":\"" + title + "\",\"description\":\"" + description
                + "\",\"liveSessions\":4,\"sessionMinutes\":60,\"pricePence\":9900,\"renewalPence\":null,"
                + "\"awardsCertificate\":" + certificate + "}";
    }

    private static String cohortJson(String name, String startsOn, int capacity) {
        return "{\"name\":\"" + name + "\",\"startsOn\":\"" + startsOn + "\",\"startTime\":\"19:00\","
                + "\"timeSlot\":\"Mondays 7:00 pm UK\","
                + "\"capacity\":" + capacity + ",\"trainerName\":\"Pat\",\"recordingAgreed\":false}";
    }

    private ResultActions mark(String token, Integer sessionId, Integer enrolmentId, boolean present)
            throws Exception {
        return send(token, put("/api/admin/live/" + sessionId + "/register/" + enrolmentId),
                "{\"present\":" + present + "}");
    }

    private ResultActions send(String token, MockHttpServletRequestBuilder request, String json) throws Exception {
        return mvc.perform(request.header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String login(String email) throws Exception {
        // Signs in from an address of its own, so these sign-ins do not count towards the per-address limit
        // that the other test classes share.
        String body = mvc.perform(post("/api/auth/login").with(r -> {
                    r.setRemoteAddr("10.0.0.21");
                    return r;
                }).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
