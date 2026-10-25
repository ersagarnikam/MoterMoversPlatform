package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import uk.co.motormovers.platform.training.service.CertificateService;

/**
 * Quizzes between live sessions: the team writes them, learners take them once the session they follow has started
 * or they attended it, and with quizzes every one is needed for the certificate, which comes as a PDF.
 */
@SpringBootTest
@AutoConfigureMockMvc
class QuizAndCertificateTest {

    private static final String QUIZ = "{\"afterSession\":1,\"title\":\"Session one check\",\"passPercent\":50,"
            + "\"questions\":[{\"text\":\"How long is the daily session?\",\"options\":[\"5 minutes\",\"10 minutes\"],"
            + "\"correctOption\":1},{\"text\":\"Who takes part?\",\"options\":[\"Some children\",\"The whole class\","
            + "\"Nobody\"],\"correctOption\":1}]}";

    @Autowired
    MockMvc mvc;

    @Autowired
    Clock clock;

    @Autowired
    CertificateService certificates;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void theTeamWritesQuizzesThatPassTheWordingCheck() throws Exception {
        String admin = login("admin@motormovers.test");
        Integer course = course(admin, "Quiz writing course", 2);
        send(admin, post("/api/admin/courses/" + course + "/quizzes"), QUIZ.replace("\"afterSession\":1", "\"afterSession\":3"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("2 live sessions")));
        send(admin, post("/api/admin/courses/" + course + "/quizzes"), QUIZ.replace("\"correctOption\":1}]", "\"correctOption\":7}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Choose the right answer")));
        send(admin, post("/api/admin/courses/" + course + "/quizzes"), QUIZ.replace("Some children", "Children with a diagnosis"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Wording check")));

        String saved = send(admin, post("/api/admin/courses/" + course + "/quizzes"), QUIZ)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        Integer quiz = JsonPath.read(saved, "$.id");
        send(admin, put("/api/admin/quizzes/" + quiz), QUIZ.replace("Session one check", "Renamed"))
                .andExpect(jsonPath("$.title").value("Renamed"));
        mvc.perform(delete("/api/admin/quizzes/" + quiz).header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());
        // Only the team writes quizzes.
        send(login("trainer@motormovers.test"), post("/api/admin/courses/" + course + "/quizzes"), QUIZ)
                .andExpect(status().isForbidden());
    }

    @Test
    void aQuizOpensWithItsSessionIsMarkedOnTheServerAndCompletesTheCertificate() throws Exception {
        String admin = login("admin@motormovers.test");
        Integer course = course(admin, "Certificate with quiz", 1);
        Integer quiz = JsonPath.read(send(admin, post("/api/admin/courses/" + course + "/quizzes"), QUIZ)
                .andReturn().getResponse().getContentAsString(), "$.id");
        String opened = send(admin, post("/api/admin/courses/" + course + "/cohorts"),
                "{\"name\":\"Quiz cohort\",\"startsOn\":\"" + LocalDate.now(clock).plusDays(20) + "\","
                        + "\"startTime\":\"18:00\",\"timeSlot\":\"Weekly\",\"capacity\":5,\"trainerName\":\"Pat\","
                        + "\"recordingAgreed\":false,\"trainerUserId\":null}")
                .andReturn().getResponse().getContentAsString();
        Integer cohort = JsonPath.read(opened, "$.cohorts[0].id");
        Integer session = JsonPath.read(opened, "$.cohorts[0].sessions[0].id");

        String sam = login("practitioner@example.test");
        Integer place = JsonPath.read(mvc.perform(post("/api/training/cohorts/" + cohort + "/enrol")
                .header("Authorization", bearer(sam))).andReturn().getResponse().getContentAsString(), "$.id");
        String base = "/api/training/enrolments/" + place + "/quizzes";

        // Not open before the session.
        assertThat((List<Boolean>) JsonPath.read(fetch(sam, base), "$[*].open")).containsExactly(false);
        mvc.perform(get(base + "/" + quiz).header("Authorization", bearer(sam)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("opens when live session 1 starts")));

        // Marked present and assessed: no certificate yet, the quiz is missing.
        send(admin, put("/api/admin/live/" + session + "/register/" + place), "{\"present\":true}")
                .andExpect(jsonPath("$.quizzes").value(1))
                .andExpect(jsonPath("$.quizzesPassed").value(0));
        send(admin, post("/api/admin/enrolments/" + place + "/assessment"), "{\"passed\":true}")
                .andExpect(jsonPath("$.certificateIssuedOn").doesNotExist());

        // The paper has no answers in it.
        String paper = fetch(sam, base + "/" + quiz);
        assertThat(paper).doesNotContain("correct");
        assertThat((List<String>) JsonPath.read(paper, "$.questions[1].options")).contains("The whole class");

        // Someone else cannot take Sam's quiz.
        send(login("parent@example.test"), post(base + "/" + quiz + "/attempts"), "{\"answers\":[1,1]}")
                .andExpect(status().isForbidden());
        send(sam, post(base + "/" + quiz + "/attempts"), "{\"answers\":[0]}")
                .andExpect(status().isBadRequest());
        send(sam, post(base + "/" + quiz + "/attempts"), "{\"answers\":[0,0]}")
                .andExpect(jsonPath("$.scorePercent").value(0))
                .andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.wrong[*]").value(org.hamcrest.Matchers.contains(1, 2)))
                .andExpect(jsonPath("$.certificateIssued").value(false));
        send(sam, post(base + "/" + quiz + "/attempts"), "{\"answers\":[1,1]}")
                .andExpect(jsonPath("$.scorePercent").value(100))
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.certificateIssued").value(true));
        assertThat((List<Integer>) JsonPath.read(fetch(sam, base), "$[*].bestScore")).containsExactly(100);

        // The certificate as a PDF, for Sam and the team only.
        byte[] pdf = mvc.perform(get("/api/training/enrolments/" + place + "/certificate.pdf")
                        .header("Authorization", bearer(sam)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        mvc.perform(get("/api/training/enrolments/" + place + "/certificate.pdf")
                        .header("Authorization", bearer(login("parent@example.test"))))
                .andExpect(status().isForbidden());

        // A quiz learners have taken is kept.
        mvc.perform(delete("/api/admin/quizzes/" + quiz).header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());

        // A month before it runs out, Sam is reminded, once.
        jdbc.update("UPDATE enrolment SET certificate_expires_on = ? WHERE id = ?", LocalDate.now(clock).plusDays(20),
                place);
        assertThat(certificates.remindExpiring()).isGreaterThanOrEqualTo(1);
        assertThat(certificates.remindExpiring()).isZero();
    }

    private Integer course(String admin, String title, int sessions) throws Exception {
        String course = send(admin, post("/api/admin/courses"), "{\"track\":\"PROFESSIONAL\",\"title\":\"" + title + "\","
                + "\"description\":\"For tests.\",\"liveSessions\":" + sessions + ",\"sessionMinutes\":60,"
                + "\"pricePence\":1000,\"renewalPence\":500,\"awardsCertificate\":true}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(course, "$.id");
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
                    r.setRemoteAddr("10.0.0.25");
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
