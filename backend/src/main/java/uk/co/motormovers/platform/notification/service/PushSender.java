package uk.co.motormovers.platform.notification.service;

import java.util.List;
import java.util.Map;

/** Delivers notifications to phones. Returns the tokens the push service says are no longer registered. */
public interface PushSender {

    record Message(String token, String title, String body, Map<String, String> data) {
    }

    List<String> send(List<Message> messages);
}
