package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

/** The one-hour induction gate, nursery rhymes on sessions and the resource library, against the demo data. */
@SpringBootTest
@AutoConfigureMockMvc
class InductionAndResourcesTest {

    @Autowired
    MockMvc mvc;

    @Test
    void sessionsStayLockedUntilTheInductionIsDone() throws Exception {
        String lewis = login("newteacher@oakfield.test");
        Integer classId = JsonPath.read(fetch(lewis, "/api/classes"), "$[0].id");
        String today = fetch(lewis, "/api/classes/" + classId + "/today");
        Integer sessionId = JsonPath.read(today, "$.nextSession.id");
        assertThat((Boolean) JsonPath.read(today, "$.inductionComplete")).isFalse();

        mvc.perform(post("/api/classes/" + classId + "/sessions").header("Authorization", bearer(lewis))
                .contentType(MediaType.APPLICATION_JSON).content("{\"sessionId\":" + sessionId + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("induction")));

        String induction = fetch(lewis, "/api/training/induction");
        assertThat((Integer) JsonPath.read(induction, "$.totalMinutes")).isEqualTo(60);
        List<Integer> lessonIds = JsonPath.read(induction, "$.lessons[*].id");
        for (Integer id : lessonIds) {
            mvc.perform(post("/api/training/induction/lessons/" + id + "/complete")
                    .header("Authorization", bearer(lewis)))
                    .andExpect(status().isOk());
        }

        mvc.perform(get("/api/training/induction").header("Authorization", bearer(lewis)))
                .andExpect(jsonPath("$.lessonsDone").value(lessonIds.size()))
                .andExpect(jsonPath("$.completedOn").isNotEmpty());
        mvc.perform(get("/api/classes/" + classId + "/today").header("Authorization", bearer(lewis)))
                .andExpect(jsonPath("$.inductionComplete").value(true));
    }

    @Test
    void parentsHaveNoInduction() throws Exception {
        mvc.perform(get("/api/training/induction").header("Authorization", bearer(login("parent@example.test"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void yellowSessionsCarryAnApprovedRhyme() throws Exception {
        String hardy = login("teacher@oakfield.test");
        Integer classId = JsonPath.read(fetch(hardy, "/api/classes"), "$[0].id");
        mvc.perform(get("/api/classes/" + classId + "/today").header("Authorization", bearer(hardy)))
                .andExpect(jsonPath("$.inductionComplete").value(true))
                .andExpect(jsonPath("$.nextSession.rhyme.title").isNotEmpty())
                .andExpect(jsonPath("$.nextSession.rhyme.lines.length()").value(greaterThan(2)));
    }

    @Test
    void staffSeeApprovedResourcesOnlyAndParentsSeeNone() throws Exception {
        String hardy = login("teacher@oakfield.test");
        mvc.perform(get("/api/resources").header("Authorization", bearer(hardy)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Motor Movers rhyme book')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.kind == 'RHYME')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.title == 'Five little speckled frogs')]").isEmpty());

        mvc.perform(get("/api/resources").header("Authorization", bearer(login("parent@example.test"))))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/resources").header("Authorization", bearer(login("admin@motormovers.test"))))
                .andExpect(jsonPath("$[?(@.title == 'Five little speckled frogs')].status").value("DRAFT"));
    }

    @Test
    void diagnosticWordingBlocksResourcesAndLessons() throws Exception {
        String admin = login("admin@motormovers.test");
        String resource = mvc.perform(post("/api/admin/resources").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"PRINTABLE\",\"title\":\"Dyslexia checklist\",\"description\":\"For parents\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/admin/resources/" + JsonPath.read(resource, "$.id") + "/approve")
                .header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());

        String lesson = mvc.perform(post("/api/admin/induction").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"position\":99,\"title\":\"Spotting a disorder\",\"summary\":\"Early signs\","
                        + "\"body\":\"Look closely.\",\"minutes\":5}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/admin/induction/" + JsonPath.read(lesson, "$.id") + "/approve")
                .header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
    }

    private String fetch(String token, String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
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
