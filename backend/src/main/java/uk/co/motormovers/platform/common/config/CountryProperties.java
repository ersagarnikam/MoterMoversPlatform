package uk.co.motormovers.platform.common.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import uk.co.motormovers.platform.common.model.Region;

/** Every country's settings ({@code platform.countries} in application.yml); {@link CountryConfig} picks one. */
@ConfigurationProperties(prefix = "platform")
public record CountryProperties(Map<Region, CountrySettings> countries) {
}
