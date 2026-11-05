package uk.co.motormovers.platform.common.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The platform's clock, in the deployment's time zone (its {@link CountrySettings}). */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock(CountrySettings country) {
        return Clock.system(country.zone());
    }
}
