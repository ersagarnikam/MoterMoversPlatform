package uk.co.motormovers.platform;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

/** The parent app: invites and links, what a parent may see, home sessions and guidance. */
@SpringBootTest
@AutoConfigureMockMvc
class FamilyApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Clock clock;

    @Test
    void parentSeesLinkedChildAsBandsOnlyAndEveryViewIsLogged() throws Exception {
        String parent = login("parent@example.test");
        String children = mvc.perform(get("/api/family/children").header("Authorization", bearer(parent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        Integer childId = JsonPath.read(children, "$[0].childId");

        mvc.perform(get("/api/family/children/" + childId + "/progress").header("Authorization", bearer(parent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.banner").value(containsString("Not a diagnosis")))
                .andExpect(jsonPath("$.areas[0].bandLabel").exists())
                .andExpect(jsonPath("$.areas[0].fraction").doesNotExist())
                .andExpect(jsonPath("$.counterDays").value(20));

        mvc.perform(get("/api/schools/1/access-log").header("Authorization", bearer(login("head@oakfield.test"))))
                .andExpect(jsonPath("$[?(@.childId == " + childId + " && @.action == 'PARENT_VIEW_PROGRESS')]")
                        .exists());
    }

    @Test
    void parentSeesNoOtherChildAndNoStaffScreens() throws Exception {
        String parent = login("parent@example.test");
        List<Integer> roster = rosterOfTeacher();
        mvc.perform(get("/api/family/children/" + roster.get(3) + "/progress").header("Authorization", bearer(parent)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/children/" + roster.get(0) + "/profile").header("Authorization", bearer(parent)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/classes/1/today").header("Authorization", bearer(parent)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/family/children").header("Authorization", bearer(login("teacher@oakfield.test"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void schoolInvitesParentRedeemsAndHeadCanRemoveTheLink() throws Exception {
        String teacher = login("teacher@oakfield.test");
        Integer childId = rosterOfTeacher().get(2);
        String invite = mvc.perform(post("/api/children/" + childId + "/parent-invites")
                        .header("Authorization", bearer(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(matchesPattern("[A-Z2-9]{4}-[A-Z2-9]{4}")))
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(invite, "$.code");

        String email = "carer-" + UUID.randomUUID() + "@example.test";
        String parent = register(email);
        // Typed loosely: lower case, no dash.
        mvc.perform(post("/api/family/children").header("Authorization", bearer(parent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code.toLowerCase().replace("-", " ") + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.childId").value(childId));
        // Single use.
        mvc.perform(post("/api/family/children").header("Authorization", bearer(register("other-" + email)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/family/children/" + childId + "/progress").header("Authorization", bearer(parent)))
                .andExpect(status().isOk());

        String head = login("head@oakfield.test");
        String links = mvc.perform(get("/api/schools/1/parent-links").header("Authorization", bearer(head)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Integer> linkIds = JsonPath.read(links, "$[?(@.parentEmail == '" + email + "')].linkId");
        mvc.perform(delete("/api/schools/1/parent-links/" + linkIds.getFirst()).header("Authorization", bearer(head)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/family/children/" + childId + "/progress").header("Authorization", bearer(parent)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownCodeIsRejectedWithoutSayingWhy() throws Exception {
        mvc.perform(post("/api/family/children").header("Authorization", bearer(register(newEmail())))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"ZZZZ-ZZZZ\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("not valid or has expired")));
    }

    @Test
    void schoolsWithParentLinksOffCannotInvite() throws Exception {
        String admin = login("admin@motormovers.test");
        String classes = mvc.perform(get("/api/classes").header("Authorization", bearer(admin)))
                .andReturn().getResponse().getContentAsString();
        List<Integer> marlow = JsonPath.read(classes, "$[?(@.schoolName == 'Marlow Infants')].id");
        String today = mvc.perform(get("/api/classes/" + marlow.getFirst() + "/today")
                        .header("Authorization", bearer(admin)))
                .andReturn().getResponse().getContentAsString();
        Integer childId = JsonPath.read(today, "$.roster[0].id");

        mvc.perform(post("/api/children/" + childId + "/parent-invites").header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("switched off")));
    }

    @Test
    void homeSessionsSentTwiceAreStoredOnceAndFillTheCounter() throws Exception {
        String parent = register(newEmail());
        String home = mvc.perform(get("/api/family/home?stage=RED").header("Authorization", bearer(parent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doneToday").value(false))
                .andExpect(jsonPath("$.counterDays").value(14))
                .andExpect(jsonPath("$.counterGrid.length()").value(14))
                .andReturn().getResponse().getContentAsString();
        Integer activityId = JsonPath.read(home, "$.next.id");

        String entry = "{\"clientId\":\"" + UUID.randomUUID() + "\",\"activityId\":" + activityId
                + ",\"doneOn\":\"" + LocalDate.now(clock) + "\"}";
        mvc.perform(post("/api/family/home/sessions").header("Authorization", bearer(parent))
                        .contentType(MediaType.APPLICATION_JSON).content("[" + entry + "," + entry + "]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(1))
                .andExpect(jsonPath("$.duplicates").value(1));

        mvc.perform(get("/api/family/home?stage=RED").header("Authorization", bearer(parent)))
                .andExpect(jsonPath("$.doneToday").value(true))
                .andExpect(jsonPath("$.daysDone").value(1))
                .andExpect(jsonPath("$.next.id").value(activityId));
    }

    @Test
    void homeSessionForSomeoneElsesChildIsRefused() throws Exception {
        String parent = register(newEmail());
        String home = mvc.perform(get("/api/family/home?stage=RED").header("Authorization", bearer(parent)))
                .andReturn().getResponse().getContentAsString();
        Integer activityId = JsonPath.read(home, "$.next.id");
        String entry = "{\"clientId\":\"" + UUID.randomUUID() + "\",\"activityId\":" + activityId + ",\"childId\":"
                + rosterOfTeacher().getFirst() + ",\"doneOn\":\"" + LocalDate.now(clock) + "\"}";
        mvc.perform(post("/api/family/home/sessions").header("Authorization", bearer(parent))
                        .contentType(MediaType.APPLICATION_JSON).content("[" + entry + "]"))
                .andExpect(status().isForbidden());
    }

    @Test
    void registrationIsForParentsAndChecksTheBasics() throws Exception {
        String email = newEmail();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration(email, "long-enough")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("PARENT"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration(email.toUpperCase(), "long-enough")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration(newEmail(), "short")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void parentsSeeApprovedGuidanceOnly() throws Exception {
        String admin = login("admin@motormovers.test");
        String all = mvc.perform(get("/api/admin/guidance").header("Authorization", bearer(admin)))
                .andReturn().getResponse().getContentAsString();
        List<Integer> drafts = JsonPath.read(all, "$[?(@.status == 'DRAFT')].id");

        String parent = login("parent@example.test");
        mvc.perform(get("/api/family/guidance").header("Authorization", bearer(parent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + drafts.getFirst() + ")]").doesNotExist());
        mvc.perform(get("/api/family/guidance/" + drafts.getFirst()).header("Authorization", bearer(parent)))
                .andExpect(status().isNotFound());
    }

    @Test
    void homeActivityWithDiagnosticWordingCannotBeApproved() throws Exception {
        String admin = login("admin@motormovers.test");
        String all = mvc.perform(get("/api/admin/home-activities").header("Authorization", bearer(admin)))
                .andReturn().getResponse().getContentAsString();
        List<Integer> flagged = JsonPath.read(all, "$[?(@.status == 'DRAFT' && @.wordingIssues.length() > 0)].id");

        mvc.perform(post("/api/admin/home-activities/" + flagged.getFirst() + "/approve")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
    }

    private List<Integer> rosterOfTeacher() throws Exception {
        String teacher = login("teacher@oakfield.test");
        String classes = mvc.perform(get("/api/classes").header("Authorization", bearer(teacher)))
                .andReturn().getResponse().getContentAsString();
        Integer classId = JsonPath.read(classes, "$[0].id");
        String today = mvc.perform(get("/api/classes/" + classId + "/today").header("Authorization", bearer(teacher)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(today, "$.roster[*].id");
    }

    private String register(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration(email, "password123")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private static String registration(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"displayName\":\"Alex Carer\"}";
    }

    private static String newEmail() {
        return "parent-" + UUID.randomUUID() + "@example.test";
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
