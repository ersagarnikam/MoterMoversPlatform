package uk.co.motormovers.platform.notification.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class PushConfig {

    private static final Logger log = LoggerFactory.getLogger(PushConfig.class);

    /** Expo's push service when PUSH_ENABLED is set; otherwise notifications are only logged. */
    @Bean
    PushSender pushSender(PushProperties properties) {
        if (properties.enabled()) {
            return new ExpoPushSender(RestClient.builder(), properties.accessToken());
        }
        return messages -> {
            messages.forEach(m -> log.info("Push not sent (PUSH_ENABLED is off): \"{}\" - {}", m.title(), m.body()));
            return List.of();
        };
    }
}
