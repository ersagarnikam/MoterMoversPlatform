package uk.co.motormovers.platform.common.config;

import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import uk.co.motormovers.platform.common.model.Region;

/** The deployment's {@link CountrySettings}: the block of {@code platform.countries} for {@code platform.region}. */
@Configuration
public class CountryConfig {

    @Bean
    CountrySettings countrySettings(PlatformProperties properties, CountryProperties countries) {
        Region region = properties.region();
        String key = region.name().toLowerCase(Locale.ROOT);
        CountrySettings settings = countries.countries() == null ? null : countries.countries().get(region);
        if (settings == null) {
            throw new CountrySettingsException("No country settings for region " + region + " (platform.countries." + key
                    + " in application.yml)");
        }
        return settings.validated(key);
    }
}
