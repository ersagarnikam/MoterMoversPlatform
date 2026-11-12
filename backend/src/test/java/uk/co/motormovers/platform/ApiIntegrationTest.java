package uk.co.motormovers.platform;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

/** End-to-end checks against the demo data: sign-in, who can see what, and the safety rules. */
@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {

    @Autowired
    MockMvc mvc;


    @Test
    void theCountrySettingsAreOpenAndBritishByDefault() throws Exception {
        mvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.region").value("UK"))
                .andExpect(jsonPath("$.currency").value("GBP"))
                .andExpect(jsonPath("$.taxLabel").value("VAT"))
                .andExpect(jsonPath("$.taxPercent").value(20))
                .andExpect(jsonPath("$.timeZone").value("Europe/London"))
                .andExpect(jsonPath("$.yearNames[1]").value("Reception"))
                .andExpect(jsonPath("$.curriculumLine").value(startsWith(
                        "Motor Movers supports the Physical Development area of the EYFS.")));
    }

    @Test
    void rejectsAnonymousAndBadPasswords() throws Exception {
        mvc.perform(get("/api/classes")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"teacher@oakfield.test\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void teacherSeesOwnClassOnly() throws Exception {
        String token = login("teacher@oakfield.test");
        String classes = mvc.perform(get("/api/classes").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        Integer myClass = JsonPath.read(classes, "$[0].id");

        mvc.perform(get("/api/classes/" + myClass + "/today").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stageLicensed").value(true))
                .andExpect(jsonPath("$.counterDays").value(20));

        mvc.perform(get("/api/classes/" + (myClass + 1) + "/today").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void profileAlwaysCarriesTheNotADiagnosisBannerAndIsLogged() throws Exception {
        String teacher = login("teacher@oakfield.test");
        Integer childId = firstChildOfTeacher(teacher);

        mvc.perform(get("/api/children/" + childId + "/assessment").header("Authorization", bearer(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.banner").value(containsString("Not a diagnosis")));

        String head = login("head@oakfield.test");
        mvc.perform(get("/api/schools/1/access-log").header("Authorization", bearer(head)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.childId == " + childId + ")]").exists());
    }

    @Test
    void trustLeadSeesSummariesButNotChildren() throws Exception {
        String trust = login("trust@northbridge.test");
        mvc.perform(get("/api/trusts/1/dashboard").header("Authorization", bearer(trust)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schools.length()").value(4));
        Integer childId = firstChildOfTeacher(login("teacher@oakfield.test"));
        mvc.perform(get("/api/children/" + childId + "/assessment").header("Authorization", bearer(trust)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/schools/1/dashboard").header("Authorization", bearer(trust)))
                .andExpect(status().isForbidden());
    }

    @Test
    void diagnosticWordingCannotBeApproved() throws Exception {
        String admin = login("admin@motormovers.test");
        String created = mvc.perform(post("/api/admin/content").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stage\":\"RED\",\"unit\":99,\"day\":1,\"title\":\"Signs of autism\","
                        + "\"focus\":\"Spotting it early\",\"durationMinutes\":10,\"activities\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(created, "$.id");

        mvc.perform(post("/api/admin/content/" + id + "/approve").header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminAreaIsAdminOnly() throws Exception {
        mvc.perform(get("/api/admin/content").header("Authorization", bearer(login("head@oakfield.test"))))
                .andExpect(status().isForbidden());
    }

    private Integer firstChildOfTeacher(String token) throws Exception {
        String classes = mvc.perform(get("/api/classes").header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        Integer classId = JsonPath.read(classes, "$[0].id");
        String today = mvc.perform(get("/api/classes/" + classId + "/today").header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(today, "$.roster[0].id");
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
