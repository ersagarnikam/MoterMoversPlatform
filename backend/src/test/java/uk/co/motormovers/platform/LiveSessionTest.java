package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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

/** Live course sessions: dates, who sees and joins which session, and the register. Zoom is not connected here. */
@SpringBootTest
@AutoConfigureMockMvc
class LiveSessionTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Clock clock;

    @Test
    void aCohortsSessionsAreDatedWeeklyAtItsStartTimeAndMoveWithIt() throws Exception {
        String admin = login("admin@motormovers.test");
        String course = send(admin, post("/api/admin/courses"), "{\"track\":\"PROFESSIONAL\",\"title\":\"Dated course\","
                + "\"description\":\"Three sessions.\",\"liveSessions\":3,\"sessionMinutes\":90,\"pricePence\":1000,"
                + "\"renewalPence\":null,\"awardsCertificate\":false}")
                .andReturn().getResponse().getContentAsString();
        Integer courseId = JsonPath.read(course, "$.id");
        LocalDate start = LocalDate.now(clock).plusDays(30);
        String opened = send(admin, post("/api/admin/courses/" + courseId + "/cohorts"), cohort(start, "18:30"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> startsAt = JsonPath.read(opened, "$.cohorts[0].sessions[*].startsAt");
        assertThat(startsAt).containsExactly(at(start, "18:30"), at(start.plusWeeks(1), "18:30"),
                at(start.plusWeeks(2), "18:30"));
        assertThat((List<Integer>) JsonPath.read(opened, "$.cohorts[0].sessions[*].durationMinutes"))
                .containsOnly(90);

        // Moving the cohort moves its sessions.
        Integer cohortId = JsonPath.read(opened, "$.cohorts[0].id");
        String moved = send(admin, put("/api/admin/cohorts/" + cohortId), cohort(start, "09:00"))
                .andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(moved, "$.cohorts[0].sessions[*].startsAt"))
                .first().isEqualTo(at(start, "09:00"));
    }

    @Test
    void learnersSeeTheirOwnSessionsAndJoinOnlyWhenTheyOpen() throws Exception {
        // Jo is booked on the demo cohort that starts ten minutes after start-up, and nothing else.
        String parent = login("parent@example.test");
        String mine = fetch(parent, "/api/training/live");
        assertThat((List<String>) JsonPath.read(mine, "$[*].cohortName")).containsOnly("Cohort A · starting now");
        Integer now = JsonPath.read(mine, "$[0].id");
        assertThat((Boolean) JsonPath.read(mine, "$[0].open")).isTrue();
        assertThat((Boolean) JsonPath.read(mine, "$[0].host")).isFalse();

        // Without the Zoom keys the session still runs, with the rules, and says Zoom is not connected.
        mvc.perform(post("/api/training/live/" + now + "/join").header("Authorization", bearer(parent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zoomConnected").value(false))
                .andExpect(jsonPath("$.signature").doesNotExist())
                .andExpect(jsonPath("$.rules").value(containsString("Adults only on camera")));

        // Next week's session is not open yet.
        String team = login("admin@motormovers.test");
        List<Integer> later = JsonPath.read(fetch(team, "/api/training/live"),
                "$[?(@.cohortName == 'Cohort A · starting now' && @.number == 2)].id");
        mvc.perform(post("/api/training/live/" + later.getFirst() + "/join").header("Authorization", bearer(parent)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("opens 15 minutes before")));

        // Someone not booked on the cohort cannot join at all.
        mvc.perform(post("/api/training/live/" + now + "/join")
                        .header("Authorization", bearer(login("practitioner@example.test"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void theTeamHostsAnySessionAndKeepsTheRegister() throws Exception {
        String pat = login("pat@motormovers.test");
        String all = fetch(pat, "/api/training/live");
        assertThat((List<Boolean>) JsonPath.read(all, "$[*].host")).containsOnly(true);
        // The team can open a session ahead of time, e.g. to check the room.
        List<Integer> later = JsonPath.read(all, "$[?(@.cohortName == 'Cohort A · UK evening')].id");
        mvc.perform(post("/api/training/live/" + later.getFirst() + "/join").header("Authorization", bearer(pat)))
                .andExpect(status().isOk());

        List<Integer> now = JsonPath.read(all, "$[?(@.cohortName == 'Cohort A · starting now' && @.number == 1)].id");
        String register = fetch(pat, "/api/admin/live/" + now.getFirst() + "/register");
        assertThat((List<String>) JsonPath.read(register, "$.learners[*].learnerName")).containsExactly("Jo Bennett");
        Integer jo = JsonPath.read(register, "$.learners[0].enrolmentId");

        send(pat, put("/api/admin/live/" + now.getFirst() + "/register/" + jo), "{\"present\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendedSessions[0]").value(1));
        mvc.perform(get("/api/admin/live/" + now.getFirst() + "/register").header("Authorization", bearer(pat)))
                .andExpect(jsonPath("$.learners[0].present").value(true));
        // The learner sees it too.
        mvc.perform(get("/api/training/enrolments/mine").header("Authorization", bearer(login("parent@example.test"))))
                .andExpect(jsonPath("$[?(@.cohortName == 'Cohort A · starting now')].sessionsAttended").value(1));
        send(pat, put("/api/admin/live/" + now.getFirst() + "/register/" + jo), "{\"present\":false}")
                .andExpect(jsonPath("$.sessionsAttended").value(0));

        // A cohort's register only takes learners booked on that cohort.
        List<Integer> other = JsonPath.read(all, "$[?(@.cohortName == 'Cohort A · UK evening')].id");
        send(pat, put("/api/admin/live/" + other.getFirst() + "/register/" + jo), "{\"present\":true}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void theRegisterShowsWhoJoinedAndTicksThemAllInOneGo() throws Exception {
        String parent = login("parent@example.test");
        String pat = login("pat@motormovers.test");
        List<Integer> now = JsonPath.read(fetch(pat, "/api/training/live"),
                "$[?(@.cohortName == 'Cohort A · starting now' && @.number == 1)].id");
        String register = "/api/admin/live/" + now.getFirst() + "/register";

        mvc.perform(post("/api/training/live/" + now.getFirst() + "/join").header("Authorization", bearer(parent)))
                .andExpect(status().isOk());
        String afterJoin = fetch(pat, register);
        String joinedAt = ((List<String>) JsonPath.read(afterJoin,
                "$.learners[?(@.learnerName == 'Jo Bennett')].joinedAt")).getFirst();
        assertThat(joinedAt).isNotBlank();

        // Opening the session again keeps the first time and updates the latest.
        mvc.perform(post("/api/training/live/" + now.getFirst() + "/join").header("Authorization", bearer(parent)));
        String again = fetch(pat, register);
        assertThat((List<String>) JsonPath.read(again, "$.learners[?(@.learnerName == 'Jo Bennett')].joinedAt"))
                .containsExactly(joinedAt);

        // Ticks everyone who joined; the register is the attendance that counts.
        mvc.perform(post(register + "/mark-joined").header("Authorization", bearer(pat)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.learners[?(@.learnerName == 'Jo Bennett')].present").value(true));
        mvc.perform(post(register + "/mark-joined").header("Authorization", bearer(parent)))
                .andExpect(status().isForbidden());
    }

    private String cohort(LocalDate startsOn, String time) {
        return "{\"name\":\"Dated cohort\",\"startsOn\":\"" + startsOn + "\",\"startTime\":\"" + time + "\","
                + "\"timeSlot\":\"Weekly\",\"capacity\":10,\"trainerName\":\"Pat Preedy\",\"recordingAgreed\":false}";
    }

    /** The instant of a UK date and time, as the API writes it. */
    private String at(LocalDate date, String time) {
        Instant instant = date.atTime(LocalTime.parse(time)).atZone(clock.getZone()).toInstant();
        return instant.toString();
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
                    r.setRemoteAddr("10.0.0.22");
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
