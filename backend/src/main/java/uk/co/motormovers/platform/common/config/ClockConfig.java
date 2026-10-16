package uk.co.motormovers.platform.common.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import uk.co.motormovers.platform.common.model.Region;

@Configuration
public class ClockConfig {

    @Bean
    Clock clock(PlatformProperties properties) {
        ZoneId zone = properties.region() == Region.AU ? ZoneId.of("Australia/Sydney") : ZoneId.of("Europe/London");
        return Clock.system(zone);
    }
}
