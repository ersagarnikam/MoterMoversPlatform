package uk.co.motormovers.platform.training.zoom;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ZoomConfig {

    private static final Logger log = LoggerFactory.getLogger(ZoomConfig.class);

    /** The real client when every key is set; otherwise one that refuses, since nothing should call it. */
    @Bean
    ZoomClient zoomClient(ZoomProperties properties, Clock clock) {
        if (properties.configured()) {
            return new ZoomApiClient(RestClient.builder(), properties, clock);
        }
        log.info("Zoom is not connected: live sessions run without video until the platform.zoom keys are set");
        return new ZoomClient() {
            @Override
            public Meeting createMeeting(String hostUser, String topic, Instant startsAt, int minutes, ZoneId zone,
                    boolean record) {
                throw new IllegalStateException("Zoom is not connected");
            }

            @Override
            public String hostToken(String hostUser) {
                throw new IllegalStateException("Zoom is not connected");
            }
        };
    }
}
