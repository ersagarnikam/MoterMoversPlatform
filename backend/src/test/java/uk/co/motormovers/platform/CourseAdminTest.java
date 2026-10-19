package uk.co.motormovers.platform;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;

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
        return "{\"name\":\"" + name + "\",\"startsOn\":\"" + startsOn + "\",\"timeSlot\":\"Mondays 7:00 pm UK\","
                + "\"capacity\":" + capacity + ",\"trainerName\":\"Pat\",\"recordingAgreed\":false}";
    }

    private ResultActions send(String token, MockHttpServletRequestBuilder request, String json) throws Exception {
        return mvc.perform(request.header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
