package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import uk.co.motormovers.platform.child.repository.ChildRepository;
import uk.co.motormovers.platform.child.service.RetentionJob;
import uk.co.motormovers.platform.common.config.Mailer;

/** What going live needs: input checks, rate limits, staff accounts and password links, school setup. */
@SpringBootTest
@AutoConfigureMockMvc
class GoLiveTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ChildRepository children;

    @Autowired
    RetentionJob retention;

    @MockitoBean
    Mailer mailer;

    @BeforeEach
    void linksAreJustThePath() {
        when(mailer.link(anyString())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void badScoresAreRefusedNotStored() throws Exception {
        String teacher = login("teacher@oakfield.test", "password");
        Integer childId = JsonPath.read(fetch(teacher, "/api/classes/" + firstClass(teacher) + "/today"), "$.roster[0].id");
        String[] bad = {
            "{\"clientId\":\"g1\",\"childId\":" + childId + ",\"itemCode\":\"GM_HOP\",\"score\":2}",
            "{\"clientId\":\"g2\",\"childId\":" + childId + ",\"itemCode\":\"GM_HOP\",\"score\":2,\"screenedOn\":\""
                    + LocalDate.now().plusYears(1) + "\"}",
            "{\"childId\":" + childId + ",\"itemCode\":\"GM_HOP\",\"score\":2,\"screenedOn\":\"" + LocalDate.now() + "\"}",
            "{\"clientId\":\"g3\",\"childId\":" + childId + ",\"itemCode\":\"NOPE\",\"score\":2,\"screenedOn\":\""
                    + LocalDate.now() + "\"}",
        };
        for (String score : bad) {
            send(teacher, "/api/screening/results", "[" + score + "]").andExpect(status().isBadRequest());
        }
    }

    @Test
    void clashesAndOverlongValuesAreConflictsNotServerErrors() throws Exception {
        String admin = login("admin@motormovers.test", "password");
        send(admin, "/api/admin/content", "{\"stage\":\"RED\",\"unit\":1,\"day\":1,\"title\":\"t\",\"focus\":\"f\","
                + "\"durationMinutes\":10}")
                .andExpect(status().isConflict());
        send(admin, "/api/admin/content", "{\"stage\":\"RED\",\"unit\":70,\"day\":1,\"title\":\"" + "a".repeat(400)
                + "\",\"focus\":\"f\",\"durationMinutes\":10}")
                .andExpect(status().isConflict());
        mvc.perform(get("/api/classes/abc/today").header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("'classId' is not valid"));
    }

    @Test
    void repeatedSignInFailuresAreSlowedDown() throws Exception {
        String body = "{\"email\":\"nobody-" + UUID.randomUUID() + "@example.test\",\"password\":\"wrong\"}";
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void newStaffSetPasswordSignInResetAndCanBeSwitchedOff() throws Exception {
        String head = login("head@oakfield.test", "password");
        String email = "new-" + UUID.randomUUID() + "@oakfield.test";
        String staff = send(head, "/api/schools/1/staff",
                "{\"email\":\"" + email + "\",\"displayName\":\"Ms New\",\"role\":\"TEACHER\"}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer userId = JsonPath.read(staff, "$.id");

        // The invite link sets the first password; it works once.
        String invite = lastLinkTo(email);
        setPassword(invite, "a-long-password-1").andExpect(jsonPath("$.email").value(email));
        setPassword(invite, "another-password-2").andExpect(status().isBadRequest());
        String token = login(email, "a-long-password-1");

        // Forgot password: always 202, and the link sets a new password.
        mvc.perform(post("/api/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@nowhere.test\"}")).andExpect(status().isAccepted());
        mvc.perform(post("/api/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}")).andExpect(status().isAccepted());
        setPassword(lastLinkTo(email), "short").andExpect(status().isBadRequest());
        setPassword(lastLinkTo(email), "a-new-long-password-3").andExpect(status().isOk());
        login(email, "a-new-long-password-3");

        // Switched off: no new sign-in, and the token already held stops working.
        send(head, "/api/schools/1/staff/" + userId + "/enabled", "{\"enabled\":false}").andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"a-new-long-password-3\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/classes").header("Authorization", bearer(token))).andExpect(status().isUnauthorized());
    }

    @Test
    void headsSetUpTheirOwnSchoolOnly() throws Exception {
        String head = login("head@oakfield.test", "password");
        String created = send(head, "/api/schools/1/classes",
                "{\"name\":\"Nursery N\",\"yearGroup\":\"Nursery\",\"stage\":\"RED\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Integer classId = JsonPath.read(created, "$.id");

        String added = send(head, "/api/schools/1/classes/" + classId + "/children",
                "{\"children\":[{\"initials\":\"a.b.\"},{\"initials\":\"C.D.\",\"fullName\":\"Cara Dunn\"}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].initials").value("A.B."))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(added, "$[*].id");

        send(head, "/api/schools/1/children/" + ids.get(0) + "/left", "{\"leftOn\":\"" + LocalDate.now() + "\"}")
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/schools/1/classes/" + classId + "/children").header("Authorization", bearer(head)))
                .andExpect(jsonPath("$.length()").value(1));
        send(head, "/api/schools/1/children/" + ids.get(1) + "/left", "{\"leftOn\":\"" + LocalDate.now().plusDays(5) + "\"}")
                .andExpect(status().isBadRequest());

        // Another school's head, a teacher and a trust lead cannot.
        mvc.perform(get("/api/schools/2/setup").header("Authorization", bearer(head))).andExpect(status().isForbidden());
        mvc.perform(get("/api/schools/1/setup").header("Authorization", bearer(login("teacher@oakfield.test", "password"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/schools/1/setup").header("Authorization", bearer(login("trust@northbridge.test", "password"))))
                .andExpect(status().isForbidden());
        send(head, "/api/schools/1/staff", "{\"email\":\"x@y.test\",\"displayName\":\"X\",\"role\":\"ADMIN\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminAddsTrustsAndTrustLeads() throws Exception {
        String admin = login("admin@motormovers.test", "password");
        String trust = send(admin, "/api/admin/trusts", "{\"name\":\"Riverside Trust\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String email = "lead-" + UUID.randomUUID() + "@riverside.test";
        send(admin, "/api/admin/trusts/" + JsonPath.read(trust, "$.id") + "/leads",
                "{\"email\":\"" + email + "\",\"displayName\":\"Mr Lead\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TRUST_LEAD"));
        verify(mailer, atLeastOnce()).send(eq(email), anyString(), anyString());
    }

    @Test
    void leaversAreDeletedAfterTheRetentionPeriod() throws Exception {
        String head = login("head@oakfield.test", "password");
        String created = send(head, "/api/schools/1/classes",
                "{\"name\":\"Leavers L\",\"yearGroup\":\"Year 6\",\"stage\":\"RED\"}")
                .andReturn().getResponse().getContentAsString();
        String added = send(head, "/api/schools/1/classes/" + JsonPath.read(created, "$.id") + "/children",
                "{\"children\":[{\"initials\":\"L.V.\"}]}").andReturn().getResponse().getContentAsString();
        Long childId = ((Integer) JsonPath.read(added, "$[0].id")).longValue();
        send(head, "/api/schools/1/children/" + childId + "/left",
                "{\"leftOn\":\"" + LocalDate.now().minusYears(2) + "\"}").andExpect(status().isNoContent());

        assertThat(retention.purgeLeavers()).isGreaterThanOrEqualTo(1);
        assertThat(children.findById(childId)).isEmpty();
    }

    private String lastLinkTo(String email) {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailer, atLeastOnce()).send(eq(email), anyString(), body.capture());
        Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(body.getValue());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private ResultActions setPassword(String token, String password) throws Exception {
        return mvc.perform(post("/api/auth/password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions send(String token, String path, String json) throws Exception {
        return mvc.perform(post(path).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Integer firstClass(String token) throws Exception {
        return JsonPath.read(fetch(token, "/api/classes"), "$[0].id");
    }

    private String fetch(String token, String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
