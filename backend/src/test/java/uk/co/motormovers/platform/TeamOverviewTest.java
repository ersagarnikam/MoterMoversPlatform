package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

/** The team's home page: what needs attention today, for the team only. */
@SpringBootTest
@AutoConfigureMockMvc
class TeamOverviewTest {

    @Autowired
    MockMvc mvc;

    @Test
    void theTeamSeesWhatNeedsAttention() throws Exception {
        String body = mvc.perform(get("/api/admin/overview").header("Authorization", "Bearer " + login("admin@motormovers.test")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // The demo has drafts waiting (e.g. the one the wording check blocks) and a parent session today.
        assertThat((Integer) JsonPath.read(body, "$.draftsAwaiting")).isPositive();
        assertThat((List<String>) JsonPath.read(body, "$.sessionsToday[*].cohortName")).contains("Cohort A · starting now");
        assertThat((Integer) JsonPath.read(body, "$.sessionsToday[0].booked")).isPositive();
        assertThat((List<?>) JsonPath.read(body, "$.renewals")).isNotNull();
        assertThat((Integer) JsonPath.read(body, "$.clashingCohorts")).isNotNegative();
    }

    @Test
    void onlyTheTeamSeesIt() throws Exception {
        mvc.perform(get("/api/admin/overview").header("Authorization", "Bearer " + login("trainer@motormovers.test")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/overview").header("Authorization", "Bearer " + login("head@oakfield.test")))
                .andExpect(status().isForbidden());
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").with(r -> {
                    r.setRemoteAddr("10.0.0.28");
                    return r;
                }).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }
}
