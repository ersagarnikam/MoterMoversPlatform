package uk.co.motormovers.platform.training.zoom;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;

/**
 * Zoom's REST API with a Server-to-Server OAuth app. The access token lasts an hour and is reused until a
 * minute before it expires, or until Zoom refuses a call (e.g. after the app's scopes change). A refusal becomes
 * a {@link ZoomException} carrying Zoom's own reason, which the team sees.
 */
public class ZoomApiClient implements ZoomClient {

    private static final Logger log = LoggerFactory.getLogger(ZoomApiClient.class);

    /** Zoom refused a call. The message is safe to show: Zoom's reason, never a key or token. */
    public static class ZoomException extends IllegalStateException {
        ZoomException(String message) {
            super(message);
        }
    }

    private record ZoomError(Integer code, String message, String reason) {
    }

    private record Token(String value, Instant expiresAt) {
    }

    private record TokenResponse(String access_token, long expires_in) {
    }

    private record MeetingResponse(long id, String password, String join_url) {
    }

    private record ZakResponse(String token) {
    }

    private final RestClient oauth;
    private final RestClient api;
    private final ZoomProperties properties;
    private final Clock clock;
    private Token token;

    public ZoomApiClient(RestClient.Builder builder, ZoomProperties properties, Clock clock) {
        this.oauth = builder.clone().baseUrl("https://zoom.us").build();
        this.api = builder.clone().baseUrl("https://api.zoom.us/v2").build();
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Meeting createMeeting(String hostUser, String topic, Instant startsAt, int minutes, ZoneId zone,
            boolean record) {
        Map<String, Object> body = Map.of(
                "topic", topic,
                "type", 2,
                "start_time", DateTimeFormatter.ISO_INSTANT.format(startsAt.truncatedTo(ChronoUnit.SECONDS)),
                "duration", minutes,
                "timezone", zone.getId(),
                "settings", Map.of(
                        "join_before_host", false,
                        "waiting_room", false,
                        "mute_upon_entry", true,
                        "host_video", true,
                        "participant_video", false,
                        "auto_recording", record ? "cloud" : "none"));
        MeetingResponse created = call("create the meeting", () -> api.post()
                .uri("/users/{user}/meetings", hostUser)
                .header("Authorization", "Bearer " + accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(MeetingResponse.class));
        return new Meeting(String.valueOf(created.id()), created.password(), created.join_url());
    }

    @Override
    public String hostToken(String hostUser) {
        ZakResponse zak = call("start the meeting as host " + hostUser, () -> api.get()
                .uri(b -> b.path("/users/{user}/token").queryParam("type", "zak").build(hostUser))
                .header("Authorization", "Bearer " + accessToken())
                .retrieve()
                .body(ZakResponse.class));
        return zak.token();
    }

    private <T> T call(String what, java.util.function.Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            synchronized (this) {
                token = null;
            }
            ZoomError error = e.getResponseBodyAs(ZoomError.class);
            String reason = error == null ? null : error.message() != null ? error.message() : error.reason();
            // Zoom quotes the token in some messages; keep it out of logs and screens.
            reason = reason == null ? "HTTP " + e.getStatusCode().value()
                    : reason.replaceAll("(?i)(access token)[=:]\\S+", "$1");
            log.warn("Zoom refused to {}: {}", what, reason);
            throw new ZoomException("Zoom refused to " + what + ": " + reason);
        }
    }

    private synchronized String accessToken() {
        Instant now = clock.instant();
        if (token == null || now.isAfter(token.expiresAt())) {
            String basic = Base64.getEncoder().encodeToString(
                    (properties.clientId() + ":" + properties.clientSecret()).getBytes(StandardCharsets.UTF_8));
            TokenResponse response = call("sign in with the Server-to-Server app", () -> oauth.post()
                    .uri(b -> b.path("/oauth/token").queryParam("grant_type", "account_credentials")
                            .queryParam("account_id", properties.accountId()).build())
                    .header("Authorization", "Basic " + basic)
                    .retrieve()
                    .body(TokenResponse.class));
            token = new Token(response.access_token(), now.plusSeconds(Math.max(0, response.expires_in() - 60)));
        }
        return token.value();
    }
}
