package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import uk.co.motormovers.platform.training.zoom.ZoomClient;

/**
 * With only the Server-to-Server app (no Meeting SDK app), sessions open in Zoom: the team gets a start link that
 * signs them in as host, learners get the meeting's join link. Meeting SDK keys equal to the Server-to-Server app's
 * are treated as absent, since Zoom rejects them for the SDK.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:zoominzoom;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "platform.zoom.account-id=acc", "platform.zoom.client-id=cid", "platform.zoom.client-secret=csecret",
    "platform.zoom.sdk-key=cid", "platform.zoom.sdk-secret=csecret",
    "platform.zoom.host-email=host@motormovers.test",
})
@AutoConfigureMockMvc
class ZoomInZoomSessionTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ZoomClient zoom;

    @Test
    void theTeamGetsAStartLinkAndLearnersTheJoinLink() throws Exception {
        when(zoom.createMeeting(anyString(), anyString(), any(), anyInt(), any(), anyBoolean()))
                .thenReturn(new ZoomClient.Meeting("81234567890", "pass1", "https://zoom.us/j/81234567890?pwd=enc"));
        when(zoom.hostToken("host@motormovers.test")).thenReturn("zak-token");

        String parent = login("parent@example.test");
        Integer session = JsonPath.read(fetch(parent, "/api/training/live"), "$[0].id");

        String asHost = join(login("pat@motormovers.test"), session);
        assertThat((Boolean) JsonPath.read(asHost, "$.zoomConnected")).isTrue();
        assertThat((Boolean) JsonPath.read(asHost, "$.embedded")).isFalse();
        assertThat((String) JsonPath.read(asHost, "$.zoomUrl")).isEqualTo("https://zoom.us/s/81234567890?zak=zak-token");
        assertThat((Object) JsonPath.read(asHost, "$.signature")).isNull();

        String asLearner = join(parent, session);
        assertThat((String) JsonPath.read(asLearner, "$.zoomUrl")).isEqualTo("https://zoom.us/j/81234567890?pwd=enc");
        // The learner never gets the host's token.
        assertThat(asLearner).doesNotContain("zak-token");
    }

    private String join(String token, Integer session) throws Exception {
        return mvc.perform(post("/api/training/live/" + session + "/join").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
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
