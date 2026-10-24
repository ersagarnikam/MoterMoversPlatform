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

/**
 * Pat's training team: trainers run the live sessions of the cohorts assigned to them and nothing else, and places
 * booked by school staff are invoiced to their school.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TrainerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Clock clock;

    @Test
    void theTeamAddsTrainersAndSetsTheirZoomLogin() throws Exception {
        String admin = login("admin@motormovers.test");
        String list = fetch(admin, "/api/admin/trainers");
        assertThat((List<Boolean>) JsonPath.read(list, "$[?(@.displayName == 'Amy Clarke')].team")).containsExactly(false);
        assertThat((List<Boolean>) JsonPath.read(list, "$[?(@.displayName == 'Pat Preedy')].team")).containsExactly(true);

        String added = send(admin, post("/api/admin/trainers"),
                "{\"email\":\"Ben.Trainer@motormovers.test\",\"displayName\":\"Ben Ward\",\"zoomEmail\":\"\"}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(added, "$.email")).isEqualTo("ben.trainer@motormovers.test");
        assertThat((Object) JsonPath.read(added, "$.zoomEmail")).isNull();
        send(admin, post("/api/admin/trainers"),
                "{\"email\":\"ben.trainer@motormovers.test\",\"displayName\":\"Ben again\",\"zoomEmail\":null}")
                .andExpect(status().isConflict());

        Integer ben = JsonPath.read(added, "$.id");
        send(admin, put("/api/admin/trainers/" + ben + "/zoom-email"), "{\"zoomEmail\":\"Ben@Zoom.example\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zoomEmail").value("ben@zoom.example"));

        // Only trainers and the team host sessions.
        Integer parent = JsonPath.read(fetch(login("parent@example.test"), "/api/auth/me"), "$.id");
        send(admin, put("/api/admin/trainers/" + parent + "/zoom-email"), "{\"zoomEmail\":\"jo@zoom.example\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void aTrainerCannotManageCoursesOrTrainers() throws Exception {
        String amy = login("trainer@motormovers.test");
        mvc.perform(get("/api/admin/trainers").header("Authorization", bearer(amy))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/courses").header("Authorization", bearer(amy))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/payments").header("Authorization", bearer(amy))).andExpect(status().isForbidden());
    }

    @Test
    void aTrainerRunsOnlyTheSessionsOfTheirOwnCohorts() throws Exception {
        String amy = login("trainer@motormovers.test");
        String mine = fetch(amy, "/api/training/live");
        assertThat((List<String>) JsonPath.read(mine, "$[*].cohortName"))
                .isNotEmpty().containsOnly("Cohort B · UK morning / AU evening");
        assertThat((List<Boolean>) JsonPath.read(mine, "$[*].host")).containsOnly(true);

        // Like the team, she can open her own session ahead of time and keep its register.
        Integer own = JsonPath.read(mine, "$[0].id");
        mvc.perform(post("/api/training/live/" + own + "/join").header("Authorization", bearer(amy)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.host").value(true));
        mvc.perform(get("/api/admin/live/" + own + "/register").header("Authorization", bearer(amy)))
                .andExpect(status().isOk());

        // Pat's sessions are not hers.
        List<Integer> pats = JsonPath.read(fetch(login("pat@motormovers.test"), "/api/training/live"),
                "$[?(@.cohortName == 'Cohort A · starting now')].id");
        mvc.perform(post("/api/training/live/" + pats.getFirst() + "/join").header("Authorization", bearer(amy)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(containsString("another trainer")));
        mvc.perform(get("/api/admin/live/" + pats.getFirst() + "/register").header("Authorization", bearer(amy)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/live/" + pats.getFirst() + "/register/mark-joined")
                        .header("Authorization", bearer(amy)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aCohortIsAssignedToATrainerOrTheTeamOnly() throws Exception {
        String admin = login("admin@motormovers.test");
        Integer amy = JsonPath.read(fetch(login("trainer@motormovers.test"), "/api/auth/me"), "$.id");
        String course = send(admin, post("/api/admin/courses"), "{\"track\":\"PROFESSIONAL\",\"title\":\"Team course\","
                + "\"description\":\"For tests.\",\"liveSessions\":2,\"sessionMinutes\":60,\"pricePence\":1000,"
                + "\"renewalPence\":null,\"awardsCertificate\":false}")
                .andReturn().getResponse().getContentAsString();
        Integer courseId = JsonPath.read(course, "$.id");

        String opened = send(admin, post("/api/admin/courses/" + courseId + "/cohorts"), cohort(amy))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(opened, "$.cohorts[0].trainerName")).isEqualTo("Amy Clarke");
        assertThat((Integer) JsonPath.read(opened, "$.cohorts[0].trainerUserId")).isEqualTo(amy);

        Integer parent = JsonPath.read(fetch(login("parent@example.test"), "/api/auth/me"), "$.id");
        send(admin, post("/api/admin/courses/" + courseId + "/cohorts"), cohort(parent))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Choose a trainer")));
    }

    @Test
    void aPlaceBookedBySchoolStaffIsInvoicedToTheirSchool() throws Exception {
        String lewis = login("newteacher@oakfield.test");
        List<Integer> open = JsonPath.read(fetch(lewis, "/api/training/courses"),
                "$.courses[?(@.title == 'Motor Movers practitioner course')].cohorts[*].id");
        mvc.perform(post("/api/training/cohorts/" + open.getFirst() + "/enrol").header("Authorization", bearer(lewis)))
                .andExpect(status().isOk());
        // Booking again changes nothing, and is not invoiced twice.
        mvc.perform(post("/api/training/cohorts/" + open.getFirst() + "/enrol").header("Authorization", bearer(lewis)))
                .andExpect(status().isOk());

        String head = login("head@oakfield.test");
        Integer oakfield = JsonPath.read(fetch(head, "/api/auth/me"), "$.schoolId");
        String payments = fetch(head, "/api/schools/" + oakfield + "/payments");
        List<String> due = JsonPath.read(payments, "$.payments[?(@.description =~ /.*Mr Lewis.*/)].status");
        assertThat(due).containsExactly("DUE");
    }

    private String cohort(Integer trainerUserId) {
        LocalDate start = LocalDate.now(clock).plusDays(40);
        return "{\"name\":\"Team cohort\",\"startsOn\":\"" + start + "\",\"startTime\":\"18:00\",\"timeSlot\":\"Weekly\","
                + "\"capacity\":10,\"trainerName\":\"Someone\",\"recordingAgreed\":false,\"trainerUserId\":"
                + trainerUserId + "}";
    }

    private String fetch(String token, String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions send(String token, MockHttpServletRequestBuilder request, String json) throws Exception {
        return mvc.perform(request.header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String login(String email) throws Exception {
        // Signs in from an address of its own, so these sign-ins do not count towards the per-address limit
        // that the other test classes share.
        String body = mvc.perform(post("/api/auth/login").with(r -> {
                    r.setRemoteAddr("10.0.0.23");
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
