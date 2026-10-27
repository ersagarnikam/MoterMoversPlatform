package uk.co.motormovers.platform.notification.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Expo's push service (https://docs.expo.dev/push-notifications/sending-notifications/): up to 100 messages a
 * request; each gets a ticket, in order, and a "DeviceNotRegistered" error means the app was removed from the phone.
 * A failed request is logged and dropped: notifications are a convenience, and emails carry the same news.
 */
public class ExpoPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(ExpoPushSender.class);
    private static final int BATCH = 100;

    private record Ticket(String status, String message, Map<String, Object> details) {
    }

    private record Tickets(List<Ticket> data) {
    }

    private final RestClient client;

    public ExpoPushSender(RestClient.Builder builder, String accessToken) {
        RestClient.Builder b = builder.baseUrl("https://exp.host/--/api/v2/push/send");
        if (accessToken != null && !accessToken.isBlank()) {
            b = b.defaultHeader("Authorization", "Bearer " + accessToken.strip());
        }
        this.client = b.build();
    }

    @Override
    public List<String> send(List<Message> messages) {
        List<String> gone = new ArrayList<>();
        for (int from = 0; from < messages.size(); from += BATCH) {
            List<Message> batch = messages.subList(from, Math.min(from + BATCH, messages.size()));
            List<Map<String, Object>> body = batch.stream().map(ExpoPushSender::json).toList();
            try {
                Tickets tickets = client.post().contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON).body(body).retrieve().body(Tickets.class);
                List<Ticket> data = tickets == null || tickets.data() == null ? List.of() : tickets.data();
                for (int i = 0; i < data.size() && i < batch.size(); i++) {
                    Ticket t = data.get(i);
                    if (!"error".equals(t.status())) {
                        continue;
                    }
                    if (t.details() != null && "DeviceNotRegistered".equals(t.details().get("error"))) {
                        gone.add(batch.get(i).token());
                    } else {
                        log.warn("Push not delivered: {}", t.message());
                    }
                }
            } catch (RestClientException e) {
                log.warn("Could not reach Expo's push service: {}", e.getMessage());
            }
        }
        return gone;
    }

    private static Map<String, Object> json(Message m) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("to", m.token());
        json.put("title", m.title());
        json.put("body", m.body());
        json.put("data", m.data());
        json.put("sound", "default");
        json.put("channelId", "default");
        return json;
    }
}
