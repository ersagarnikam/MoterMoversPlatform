package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import uk.co.motormovers.platform.assessment.model.Exercise;
import uk.co.motormovers.platform.assessment.repository.ExerciseRepository;
import uk.co.motormovers.platform.assessment.repository.MilestoneRepository;
import uk.co.motormovers.platform.common.model.ColourStage;
import uk.co.motormovers.platform.content.util.WordingGuard;

/** Pat's two assessments against the demo data: content, recording, the profile, the flag and who sees what. */
@SpringBootTest
@AutoConfigureMockMvc
class AssessmentTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Clock clock;

    @Autowired
    MilestoneRepository milestones;

    @Autowired
    ExerciseRepository exercises;

    @Test
    void patsChartsAndGridsAreLoadedAndPassTheWordingCheck() {
        assertThat(milestones.count()).isEqualTo(133);
        List<Exercise> green = exercises.findByStageOrderByNumber(ColourStage.GREEN);
        List<Exercise> yellow = exercises.findByStageOrderByNumber(ColourStage.YELLOW);
        assertThat(green).hasSize(27);
        assertThat(yellow).hasSize(18);
        assertThat(green.get(0).getPoints()).containsExactly("START", "MODULE_1", "MODULE_2", "END");
        assertThat(yellow.get(0).getPoints()).containsExactly("START", "END");
        // Motor Spin came without its four descriptions; it is still assessed.
        assertThat(green.get(3).getTitle()).isEqualTo("Motor Spin");
        assertThat(green.get(3).getLevels()).isEmpty();

        List<String> texts = new ArrayList<>();
        milestones.findAll().forEach(m -> texts.add(m.getDescription()));
        for (Exercise e : exercises.findAll()) {
            texts.add(e.getTitle());
            texts.add(e.getInstructions());
            texts.addAll(e.getLevels().values());
        }
        assertThat(new WordingGuard().check(texts)).isEmpty();
    }

    @Test
    void teacherScoresTheirClassOneExerciseAtATime() throws Exception {
        String teacher = login("teacher@oakfield.test");
        Integer classId = myClass(teacher);
        String sheet = mvc.perform(get("/api/classes/" + classId + "/exercise-sheet").header("Authorization",
                bearer(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("YELLOW"))
                .andExpect(jsonPath("$.exercises.length()").value(18))
                .andReturn().getResponse().getContentAsString();
        Integer exerciseId = JsonPath.read(sheet, "$.exercises[0].id");
        Integer childId = JsonPath.read(sheet, "$.children[-1].id");

        // Sent twice from the offline queue: stored once.
        String clientId = UUID.randomUUID().toString();
        String score = score(clientId, childId, exerciseId, "END", 3);
        mvc.perform(post("/api/assessment/exercise-scores").header("Authorization", bearer(teacher))
                .contentType(MediaType.APPLICATION_JSON).content("[" + score + "," + score + "]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(1))
                .andExpect(jsonPath("$.duplicates").value(1));
        // A later score at the same point replaces the earlier one.
        mvc.perform(post("/api/assessment/exercise-scores").header("Authorization", bearer(teacher))
                .contentType(MediaType.APPLICATION_JSON)
                .content("[" + score(UUID.randomUUID().toString(), childId, exerciseId, "END", 4) + "]"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/children/" + childId + "/assessment").header("Authorization", bearer(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("YELLOW"))
                .andExpect(jsonPath("$.exercises[0].points[1].score").value(4))
                .andExpect(jsonPath("$.totals.end").value(4))
                .andExpect(jsonPath("$.totals.max").value(72));

        // Yellow exercises have no module points, and scores run from 1 to 4.
        mvc.perform(post("/api/assessment/exercise-scores").header("Authorization", bearer(teacher))
                .contentType(MediaType.APPLICATION_JSON)
                .content("[" + score(UUID.randomUUID().toString(), childId, exerciseId, "MODULE_1", 3) + "]"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/assessment/exercise-scores").header("Authorization", bearer(teacher))
                .contentType(MediaType.APPLICATION_JSON)
                .content("[" + score(UUID.randomUUID().toString(), childId, exerciseId, "START", 0) + "]"))
                .andExpect(status().isBadRequest());

        // Another class's sheet is not theirs.
        mvc.perform(get("/api/classes/" + (classId + 1) + "/exercise-sheet").header("Authorization",
                bearer(teacher)))
                .andExpect(status().isForbidden());
    }

    @Test
    void developmentalChartsRecordTheAgeInMonthsOnce() throws Exception {
        String teacher = login("teacher@oakfield.test");
        String sheet = mvc.perform(get("/api/classes/" + myClass(teacher) + "/exercise-sheet")
                .header("Authorization", bearer(teacher)))
                .andReturn().getResponse().getContentAsString();
        Integer childId = JsonPath.read(sheet, "$.children[-2].id");

        String chart = mvc.perform(get("/api/children/" + childId + "/development").header("Authorization",
                bearer(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.banner").value(containsString("Not a diagnosis")))
                .andExpect(jsonPath("$.charts[0].chart").value("FINE"))
                .andExpect(jsonPath("$.charts[0].total").value(59))
                .andExpect(jsonPath("$.charts[1].total").value(74))
                .andExpect(jsonPath("$.charts[0].secure").value(0))
                .andReturn().getResponse().getContentAsString();
        Integer milestoneId = JsonPath.read(chart, "$.charts[0].milestones[0].id");

        String record = "{\"clientId\":\"" + UUID.randomUUID() + "\",\"childId\":" + childId + ",\"milestoneId\":"
                + milestoneId + ",\"ageMonths\":80,\"yearPoint\":\"START\",\"achievedOn\":\"" + today() + "\"}";
        mvc.perform(post("/api/assessment/milestones").header("Authorization", bearer(teacher))
                .contentType(MediaType.APPLICATION_JSON).content("[" + record + "," + record + "]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(1))
                .andExpect(jsonPath("$.duplicates").value(1));
        mvc.perform(get("/api/children/" + childId + "/development").header("Authorization", bearer(teacher)))
                .andExpect(jsonPath("$.charts[0].secure").value(1))
                .andExpect(jsonPath("$.charts[0].milestones[0].achieved.ageMonths").value(80));

        mvc.perform(put("/api/children/" + childId + "/development-comments").header("Authorization",
                bearer(teacher)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"chart\":\"FINE\",\"yearPoint\":\"START\",\"note\":\"Enjoys threading beads.\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/children/" + childId + "/development").header("Authorization", bearer(teacher)))
                .andExpect(jsonPath("$.charts[0].comments[0].note").value("Enjoys threading beads."));

        mvc.perform(delete("/api/children/" + childId + "/milestones/" + milestoneId).header("Authorization",
                bearer(teacher)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/children/" + childId + "/development").header("Authorization", bearer(teacher)))
                .andExpect(jsonPath("$.charts[0].secure").value(0));

        mvc.perform(post("/api/assessment/milestones").header("Authorization", bearer(teacher))
                .contentType(MediaType.APPLICATION_JSON).content("[" + record.replace("\"ageMonths\":80",
                        "\"ageMonths\":500").replaceFirst("\"clientId\":\"[^\"]+\"", "\"clientId\":\"x-1\"") + "]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lowScoresFlagAChildWhoMayBenefitFromExtraSupport() throws Exception {
        String head = login("head@oakfield.test");
        String overview = mvc.perform(get("/api/schools/1/assessment-overview").header("Authorization",
                bearer(head)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classes[?(@.name == 'Year 3 K')].extraSupport").value(hasItem(2)))
                // Each child counts once, at the middle of their latest scores: the two low scorers are at 2.
                .andExpect(jsonPath("$.classes[?(@.name == 'Year 3 K')].childrenAtLevel.2").value(hasItem(2)))
                .andExpect(jsonPath("$.classes[?(@.name == 'Year 2 R')].developmental.START")
                        .value(hasItem(greaterThan(0))))
                .andExpect(jsonPath("$.extraSupport.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        Integer flagged = JsonPath.read(overview, "$.extraSupport[0].childId");

        mvc.perform(get("/api/children/" + flagged + "/assessment").header("Authorization", bearer(head)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("GREEN"))
                .andExpect(jsonPath("$.banner").value(containsString("Not a diagnosis")))
                .andExpect(jsonPath("$.extraSupport.flagged").value(true))
                .andExpect(jsonPath("$.extraSupport.label").value("May benefit from extra support"))
                .andExpect(jsonPath("$.extraSupport.reasons[0]").value(containsString("at two points in a row on 21 exercises, including Yes and No")));

        mvc.perform(put("/api/children/" + flagged + "/colour-summary").header("Authorization", bearer(head))
                .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"GREEN\",\"personalComment\":"
                        + "\"Settles well.\",\"academicComment\":\"Reading with confidence.\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/children/" + flagged + "/assessment").header("Authorization", bearer(head)))
                .andExpect(jsonPath("$.summary.personalComment").value("Settles well."));

        mvc.perform(get("/api/schools/1/access-log").header("Authorization", bearer(head)))
                .andExpect(jsonPath("$[?(@.childId == " + flagged + ")].action").value(hasItem("VIEW_ASSESSMENT")));
    }

    @Test
    void trustLeadsSeeOnlyHowMany() throws Exception {
        String trust = login("trust@northbridge.test");
        String dashboard = mvc.perform(get("/api/trusts/1/dashboard").header("Authorization", bearer(trust)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schools[?(@.schoolName == 'Oakfield Primary')].extraSupport").value(hasItem(2)))
                .andReturn().getResponse().getContentAsString();
        List<Number> rate = JsonPath.read(dashboard,
                "$.schools[?(@.schoolName == 'Oakfield Primary')].developmentalRate");
        assertThat(rate.getFirst().doubleValue()).isGreaterThan(0.0);
        mvc.perform(get("/api/schools/1/assessment-overview").header("Authorization", bearer(trust)))
                .andExpect(status().isForbidden());

        String teacher = login("teacher@oakfield.test");
        String sheet = mvc.perform(get("/api/classes/" + myClass(teacher) + "/exercise-sheet")
                .header("Authorization", bearer(teacher)))
                .andReturn().getResponse().getContentAsString();
        Integer childId = JsonPath.read(sheet, "$.children[0].id");
        Integer exerciseId = JsonPath.read(sheet, "$.exercises[0].id");
        mvc.perform(get("/api/children/" + childId + "/assessment").header("Authorization", bearer(trust)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/assessment/exercise-scores").header("Authorization", bearer(trust))
                .contentType(MediaType.APPLICATION_JSON)
                .content("[" + score(UUID.randomUUID().toString(), childId, exerciseId, "END", 2) + "]"))
                .andExpect(status().isForbidden());
    }

    private String score(String clientId, Integer childId, Integer exerciseId, String point, int score) {
        return "{\"clientId\":\"" + clientId + "\",\"childId\":" + childId + ",\"exerciseId\":" + exerciseId
                + ",\"point\":\"" + point + "\",\"score\":" + score + ",\"assessedOn\":\"" + today() + "\"}";
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private Integer myClass(String token) throws Exception {
        String classes = mvc.perform(get("/api/classes").header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(classes, "$[0].id");
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
