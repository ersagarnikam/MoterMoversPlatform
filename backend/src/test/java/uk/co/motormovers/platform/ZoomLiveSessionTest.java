package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import uk.co.motormovers.platform.training.zoom.ZoomClient;

/** With both Zoom apps set: the meeting is created once and shows in the page; the team hosts, learners join. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:zoomtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "platform.zoom.account-id=acc", "platform.zoom.client-id=cid", "platform.zoom.client-secret=csecret",
    "platform.zoom.sdk-key=sdk-key", "platform.zoom.sdk-secret=sdk-secret",
    "platform.zoom.host-email=host@motormovers.test",
})
@AutoConfigureMockMvc
class ZoomLiveSessionTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ZoomClient zoom;

    @Test
    void theMeetingIsCreatedOnceAndTheTeamStartsItAsHost() throws Exception {
        when(zoom.createMeeting(anyString(), anyString(), any(), anyInt(), any(), anyBoolean()))
                .thenReturn(new ZoomClient.Meeting("81234567890", "pass1", "https://zoom.us/j/81234567890?pwd=x"));
        when(zoom.hostToken("host@motormovers.test")).thenReturn("zak-token");

        String parent = login("parent@example.test");
        Integer session = JsonPath.read(fetch(parent, "/api/training/live"), "$[0].id");

        String asHost = join(login("pat@motormovers.test"), session);
        assertThat((Boolean) JsonPath.read(asHost, "$.zoomConnected")).isTrue();
        // A separate Meeting SDK app is set, so the meeting shows inside the page.
        assertThat((Boolean) JsonPath.read(asHost, "$.embedded")).isTrue();
        assertThat((String) JsonPath.read(asHost, "$.meetingNumber")).isEqualTo("81234567890");
        assertThat((String) JsonPath.read(asHost, "$.passcode")).isEqualTo("pass1");
        assertThat((String) JsonPath.read(asHost, "$.zak")).isEqualTo("zak-token");
        assertThat(payload(JsonPath.read(asHost, "$.signature"))).contains("\"role\":1", "\"mn\":\"81234567890\"");

        String asLearner = join(parent, session);
        assertThat((String) JsonPath.read(asLearner, "$.meetingNumber")).isEqualTo("81234567890");
        assertThat((Object) JsonPath.read(asLearner, "$.zak")).isNull();
        // The join link too, for the parent app to open the Zoom app; the host's page has no use for one.
        assertThat((String) JsonPath.read(asLearner, "$.zoomUrl")).isEqualTo("https://zoom.us/j/81234567890?pwd=x");
        assertThat((Object) JsonPath.read(asHost, "$.zoomUrl")).isNull();
        assertThat(payload(JsonPath.read(asLearner, "$.signature"))).contains("\"role\":0");

        // Both joined the same meeting, created once under the configured host.
        verify(zoom, times(1)).createMeeting(eq("host@motormovers.test"), anyString(), any(), eq(60), any(),
                eq(false));
        List<String> rules = List.of((String) JsonPath.read(asLearner, "$.rules"));
        assertThat(rules.getFirst()).contains("Adults only on camera");
    }

    @Test
    void aTrainerWithTheirOwnZoomLoginHostsUnderIt() throws Exception {
        when(zoom.createMeeting(anyString(), anyString(), any(), anyInt(), any(), anyBoolean()))
                .thenReturn(new ZoomClient.Meeting("89999999999", "pass2", "https://zoom.us/j/89999999999?pwd=y"));
        when(zoom.hostToken("amy@zoom.example")).thenReturn("amy-zak");

        String admin = login("admin@motormovers.test");
        Integer amy = JsonPath.read(fetch(login("trainer@motormovers.test"), "/api/auth/me"), "$.id");
        mvc.perform(put("/api/admin/trainers/" + amy + "/zoom-email").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"zoomEmail\":\"amy@zoom.example\"}"))
                .andExpect(status().isOk());

        String trainer = login("trainer@motormovers.test");
        Integer session = JsonPath.read(fetch(trainer, "/api/training/live"), "$[0].id");
        String asHost = join(trainer, session);
        assertThat((String) JsonPath.read(asHost, "$.zak")).isEqualTo("amy-zak");
        verify(zoom).createMeeting(eq("amy@zoom.example"), anyString(), any(), anyInt(), any(), anyBoolean());
    }

    private String join(String token, Integer session) throws Exception {
        return mvc.perform(post("/api/training/live/" + session + "/join").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static String payload(String jwt) {
        return new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
    }

    private String fetch(String token, String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + token))
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
}
