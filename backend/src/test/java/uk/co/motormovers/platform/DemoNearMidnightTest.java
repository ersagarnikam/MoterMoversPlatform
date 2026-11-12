package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

/**
 * The demo loaded in the last minutes of the day, UK time: its "starting now" parent session is still today's, and
 * open to join (task 83). Its own database and a fixed clock, so it never shares the demo with the other tests.
 */
@SpringBootTest(properties = { "platform.login-limit-per-ip=500",
    "spring.datasource.url=jdbc:h2:mem:nearmidnight;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH" })
@AutoConfigureMockMvc
class DemoNearMidnightTest {

    /** Thursday 12 November 2026, 11:55 pm UK time. */
    static final ZonedDateTime NOW = ZonedDateTime.of(2026, 11, 12, 23, 55, 0, 0, ZoneId.of("Europe/London"));

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

    @Test
    @SuppressWarnings("unchecked")
    void theStartingNowSessionIsStillTodaysAndOpen() throws Exception {
        String overview = fetch(login("admin@motormovers.test"), "/api/admin/overview");
        assertThat((List<String>) JsonPath.read(overview, "$.sessionsToday[*].cohortName"))
                .contains("Cohort A · starting now");

        String mine = fetch(login("parent@example.test"), "/api/training/live");
        List<Boolean> open = JsonPath.read(mine,
                "$[?(@.cohortName == 'Cohort A · starting now' && @.number == 1)].open");
        assertThat(open).containsExactly(true);
    }

    private String fetch(String token, String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }
}
