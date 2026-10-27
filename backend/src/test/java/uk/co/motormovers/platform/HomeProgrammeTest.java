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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import uk.co.motormovers.platform.content.util.WordingGuard;
import uk.co.motormovers.platform.home.dto.HomeViews;

/**
 * Motor Movers at Home against the demo data: Priya Shah's family (Ava on Green, Leo not checked yet), who sees a
 * home child (only the family), the starting check, the daily session and ratings, a second carer, and linking a
 * school later. Its own context with a raised sign-in limit, so its sign-ins do not use up the shared one.
 */
@SpringBootTest(properties = "platform.login-limit-per-ip=500")
@AutoConfigureMockMvc
class HomeProgrammeTest {

    private static final Map<String, String> TOKENS = new ConcurrentHashMap<>();

    @Autowired
    MockMvc mvc;

    @Autowired
    Clock clock;

    @Test
    void aFamilyAddsAChildWithoutASchool() throws Exception {
        String parent = register();
        mvc.perform(get("/api/home").header("Authorization", bearer(parent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.children.length()").value(0));
        mvc.perform(post("/api/home/children").header("Authorization", bearer(parent))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Sam\",\"ageMonths\":500}"))
                .andExpect(status().isBadRequest());
        String added = mvc.perform(post("/api/home/children").header("Authorization", bearer(parent))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Sam\",\"ageMonths\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sam"))
                .andExpect(jsonPath("$.ageMonths").value(50))
                .andExpect(jsonPath("$.checkDue").value(true))
                .andReturn().getResponse().getContentAsString();
        Integer sam = JsonPath.read(added, "$.id");
        mvc.perform(get("/api/home").header("Authorization", bearer(parent)))
                .andExpect(jsonPath("$.children[0].name").value("Sam"))
                .andExpect(jsonPath("$.members[0].owner").value(true));

        // Deleting removes the child and everything recorded.
        mvc.perform(delete("/api/home/children/" + sam).header("Authorization", bearer(parent)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/home/children/" + sam).header("Authorization", bearer(parent)))
                .andExpect(status().isNotFound());
    }

    @Test
    void theStartingCheckSuggestsAColour() throws Exception {
        String priya = login("home@example.test");
        Integer leo = childNamed(priya, "Leo");
        String check = mvc.perform(get("/api/home/children/" + leo + "/check").header("Authorization", bearer(priya)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.banner").value(containsString("Not a diagnosis")))
                .andExpect(jsonPath("$.questions.length()").value(greaterThan(5)))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(check, "$.questions[*].milestoneId");

        // Two yes, the rest not yet: Leo (20 months, Orange for his age) is suggested the colour before.
        List<String> answers = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            answers.add("{\"milestoneId\":" + ids.get(i) + ",\"answer\":\"" + (i < 2 ? "YES" : "NOT_YET") + "\"}");
        }
        mvc.perform(post("/api/home/children/" + leo + "/check").header("Authorization", bearer(priya))
                .contentType(MediaType.APPLICATION_JSON).content("{\"answers\":[" + String.join(",", answers) + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.forAge").value("ORANGE"))
                .andExpect(jsonPath("$.suggested").value("RED"))
                .andExpect(jsonPath("$.earlier").value(true))
                .andExpect(jsonPath("$.yes").value(2));
        mvc.perform(get("/api/home/children/" + leo).header("Authorization", bearer(priya)))
                .andExpect(jsonPath("$.child.stage").value("RED"))
                .andExpect(jsonPath("$.child.checkDue").value(false));
        // The yes answers are secure movements on Pat's charts, with his age.
        mvc.perform(get("/api/children/" + leo + "/development").header("Authorization", bearer(priya)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.className").value("At home"));

        mvc.perform(put("/api/home/children/" + leo + "/stage").header("Authorization", bearer(priya))
                .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"ORANGE\"}"))
                .andExpect(jsonPath("$.stage").value("ORANGE"));
    }

    @Test
    void theDailySessionFollowsPatsModulesAndAsksForRatings() throws Exception {
        String priya = login("home@example.test");
        Integer ava = childNamed(priya, "Ava");
        String today = mvc.perform(get("/api/home/children/" + ava + "/today").header("Authorization", bearer(priya)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("GREEN"))
                .andExpect(jsonPath("$.hasExercises").value(true))
                .andExpect(jsonPath("$.module").value(2))
                .andExpect(jsonPath("$.modules").value(6))
                .andExpect(jsonPath("$.exercises.length()").value(4))
                .andExpect(jsonPath("$.canRate").value(true))
                .andExpect(jsonPath("$.levels[3].label").value("Got it"))
                // Module 1 is done, so its end-of-module ratings are due.
                .andExpect(jsonPath("$.review.point").value("MODULE_1"))
                .andExpect(jsonPath("$.review.label").value("End of module 1"))
                .andReturn().getResponse().getContentAsString();
        List<Integer> review = JsonPath.read(today, "$.review.exercises[*].id");

        // Ratings use the assessment endpoint, which checks the family.
        List<String> ratings = new ArrayList<>();
        for (Integer id : review) {
            ratings.add("{\"clientId\":\"" + UUID.randomUUID() + "\",\"childId\":" + ava + ",\"exerciseId\":" + id
                    + ",\"point\":\"MODULE_1\",\"score\":3,\"assessedOn\":\"" + LocalDate.now(clock) + "\"}");
        }
        mvc.perform(post("/api/assessment/exercise-scores").header("Authorization", bearer(priya))
                .contentType(MediaType.APPLICATION_JSON).content("[" + String.join(",", ratings) + "]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(review.size()));
        mvc.perform(get("/api/home/children/" + ava + "/today").header("Authorization", bearer(priya)))
                .andExpect(jsonPath("$.review").doesNotExist());

        // A session sent twice from the phone is stored once.
        String session = "{\"clientId\":\"" + UUID.randomUUID() + "\",\"childId\":" + ava + ",\"doneOn\":\""
                + LocalDate.now(clock) + "\"}";
        mvc.perform(post("/api/home/sessions").header("Authorization", bearer(priya))
                .contentType(MediaType.APPLICATION_JSON).content("[" + session + "," + session + "]"))
                .andExpect(jsonPath("$.saved").value(1))
                .andExpect(jsonPath("$.duplicates").value(1));
        mvc.perform(get("/api/home/children/" + ava + "/today").header("Authorization", bearer(priya)))
                .andExpect(jsonPath("$.doneToday").value(true));

        // Progress in the parent's words: first and latest rating.
        mvc.perform(get("/api/home/children/" + ava).header("Authorization", bearer(priya)))
                .andExpect(jsonPath("$.exercises[0].latest").value("Nearly"))
                .andExpect(jsonPath("$.charts[0].secure").value(greaterThan(10)));
    }

    @Test
    void onlyTheFamilySeesAHomeChild() throws Exception {
        String priya = login("home@example.test");
        Integer ava = childNamed(priya, "Ava");
        for (String someone : List.of("parent@example.test", "teacher@oakfield.test", "head@oakfield.test",
                "admin@motormovers.test")) {
            String token = login(someone);
            mvc.perform(get("/api/children/" + ava + "/assessment").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
        }
        String jo = login("parent@example.test");
        mvc.perform(get("/api/home/children/" + ava + "/today").header("Authorization", bearer(jo)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/home/children/" + ava).header("Authorization", bearer(jo)))
                .andExpect(status().isForbidden());
        // Staff cannot use the parent app's home endpoints at all.
        mvc.perform(get("/api/home").header("Authorization", bearer(login("teacher@oakfield.test"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aSecondCarerJoinsWithACode() throws Exception {
        String owner = register();
        mvc.perform(post("/api/home/children").header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Mia\",\"ageMonths\":30}"))
                .andExpect(status().isOk());
        String code = JsonPath.read(mvc.perform(post("/api/home/invites").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.code");

        String carer = register();
        mvc.perform(post("/api/home/join").header("Authorization", bearer(carer))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"WRONG-CODE\"}"))
                .andExpect(status().isBadRequest());
        String joined = mvc.perform(post("/api/home/join").header("Authorization", bearer(carer))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code.toLowerCase() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.children[0].name").value("Mia"))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.owner").value(false))
                .andReturn().getResponse().getContentAsString();
        // The code works once.
        mvc.perform(post("/api/home/join").header("Authorization", bearer(register()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isBadRequest());

        // The carer cannot remove the owner; the owner can remove the carer.
        List<Integer> ids = JsonPath.read(joined, "$.members[?(@.owner == true)].userId");
        mvc.perform(delete("/api/home/members/" + ids.getFirst()).header("Authorization", bearer(carer)))
                .andExpect(status().isForbidden());
        List<Integer> carers = JsonPath.read(joined, "$.members[?(@.owner == false)].userId");
        mvc.perform(delete("/api/home/members/" + carers.getFirst()).header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/home").header("Authorization", bearer(carer)))
                .andExpect(jsonPath("$.children.length()").value(0));
    }

    @Test
    void linkingASchoolLaterMovesTheHomeRecordOver() throws Exception {
        String parent = register();
        String added = mvc.perform(post("/api/home/children").header("Authorization", bearer(parent))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Kai\",\"ageMonths\":80}"))
                .andReturn().getResponse().getContentAsString();
        Integer kai = JsonPath.read(added, "$.id");
        String check = mvc.perform(get("/api/home/children/" + kai + "/check").header("Authorization", bearer(parent)))
                .andReturn().getResponse().getContentAsString();
        Integer movement = JsonPath.read(check, "$.questions[0].milestoneId");
        mvc.perform(post("/api/home/children/" + kai + "/check").header("Authorization", bearer(parent))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":[{\"milestoneId\":" + movement + ",\"answer\":\"YES\"}]}"))
                .andExpect(status().isOk());

        // The school (now on Motor Movers) gives a parent code for its own record of the child.
        String teacher = login("teacher@oakfield.test");
        String classes = mvc.perform(get("/api/classes").header("Authorization", bearer(teacher)))
                .andReturn().getResponse().getContentAsString();
        String sheet = mvc.perform(get("/api/classes/" + JsonPath.read(classes, "$[0].id") + "/exercise-sheet")
                .header("Authorization", bearer(teacher))).andReturn().getResponse().getContentAsString();
        Integer schoolChild = JsonPath.read(sheet, "$.children[-3].id");
        String code = JsonPath.read(mvc.perform(post("/api/children/" + schoolChild + "/parent-invites")
                .header("Authorization", bearer(teacher))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.code");

        mvc.perform(post("/api/home/children/" + kai + "/join-school").header("Authorization", bearer(parent))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.childId").value(schoolChild))
                .andExpect(jsonPath("$.schoolName").value("Oakfield Primary"));
        mvc.perform(get("/api/home/children/" + kai).header("Authorization", bearer(parent)))
                .andExpect(status().isNotFound());
        // The teacher now sees the movement recorded at home.
        String development = mvc.perform(get("/api/children/" + schoolChild + "/development")
                .header("Authorization", bearer(teacher))).andReturn().getResponse().getContentAsString();
        List<Integer> secure = JsonPath.read(development, "$.charts[*].milestones[?(@.achieved)].id");
        assertThat(secure).contains(movement);
        // The parent carries on with home sessions for the school child.
        mvc.perform(get("/api/home/children/" + schoolChild + "/today").header("Authorization", bearer(parent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canRate").value(false));
        mvc.perform(get("/api/family/children").header("Authorization", bearer(parent)))
                .andExpect(jsonPath("$[*].childId").value(hasItem(schoolChild)));
    }

    @Test
    void theParentWordingPassesTheWordingCheck() {
        List<String> texts = new ArrayList<>(HomeViews.LEVELS);
        texts.add(HomeViews.SUPPORT_PROMPT);
        texts.add(HomeViews.NOT_A_DIAGNOSIS.replace("Not a diagnosis.", ""));
        assertThat(new WordingGuard().check(texts)).isEmpty();
    }

    private Integer childNamed(String token, String name) throws Exception {
        String overview = mvc.perform(get("/api/home").header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(overview, "$.children[?(@.name == '" + name + "')].id");
        return ids.getFirst();
    }

    private String register() throws Exception {
        String email = "family-" + UUID.randomUUID() + "@example.test";
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"long enough password\","
                        + "\"displayName\":\"A Parent\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    /** Signs each account in once: the tests share one sign-in rate limit per address with every other test. */
    private String login(String email) throws Exception {
        String cached = TOKENS.get(email);
        if (cached != null) {
            return cached;
        }
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");
        TOKENS.put(email, token);
        return token;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
