package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import uk.co.motormovers.platform.training.zoom.ZoomApiClient;
import uk.co.motormovers.platform.training.zoom.ZoomClient;
import uk.co.motormovers.platform.training.zoom.ZoomProperties;
import uk.co.motormovers.platform.training.zoom.ZoomSignature;

/** The calls made to Zoom's API, and the Meeting SDK signature. */
class ZoomClientTest {

    private static final ZoomProperties KEYS = new ZoomProperties("acc-1", "client", "secret", "sdk", "sdk-secret",
            null);

    @Test
    void createsMeetingsAndFetchesTheHostTokenWithOneAccessToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer zoom = MockRestServiceServer.bindTo(builder).build();
        String basic = Base64.getEncoder().encodeToString("client:secret".getBytes(StandardCharsets.UTF_8));
        zoom.expect(requestTo("https://zoom.us/oauth/token?grant_type=account_credentials&account_id=acc-1"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic " + basic))
                .andRespond(withSuccess("{\"access_token\":\"t1\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        zoom.expect(requestTo("https://api.zoom.us/v2/users/pat%40motormovers.test/meetings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer t1"))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("\"start_time\":\"2026-11-03T19:00:00Z\""),
                        org.hamcrest.Matchers.containsString("\"duration\":90"),
                        org.hamcrest.Matchers.containsString("\"auto_recording\":\"cloud\""))))
                .andRespond(withSuccess("{\"id\":81234567890,\"password\":\"abc123\"}", MediaType.APPLICATION_JSON));
        zoom.expect(requestTo("https://api.zoom.us/v2/users/pat%40motormovers.test/token?type=zak"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer t1"))
                .andRespond(withSuccess("{\"token\":\"zak-1\"}", MediaType.APPLICATION_JSON));

        ZoomApiClient client = new ZoomApiClient(builder, KEYS, Clock.systemUTC());
        ZoomClient.Meeting meeting = client.createMeeting("pat@motormovers.test", "Parent course · session 1",
                Instant.parse("2026-11-03T19:00:00Z"), 90, ZoneId.of("Europe/London"), true);
        assertThat(meeting).isEqualTo(new ZoomClient.Meeting("81234567890", "abc123"));
        assertThat(client.hostToken("pat@motormovers.test")).isEqualTo("zak-1");
        zoom.verify();
    }

    @Test
    void theSignatureIsAnHs256JwtValidForAtLeastHalfAnHour() throws Exception {
        Instant now = Instant.parse("2026-11-03T18:55:00Z");
        String jwt = ZoomSignature.sign("sdk", "sdk-secret", "81234567890", ZoomSignature.HOST, now);
        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("sdk-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8)));
        assertThat(parts[2]).isEqualTo(expected);

        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        long iat = now.getEpochSecond() - 30;
        assertThat(payload).contains("\"appKey\":\"sdk\"", "\"mn\":\"81234567890\"", "\"role\":1",
                "\"iat\":" + iat, "\"exp\":" + (iat + 7200), "\"tokenExp\":" + (iat + 7200));
    }
}
