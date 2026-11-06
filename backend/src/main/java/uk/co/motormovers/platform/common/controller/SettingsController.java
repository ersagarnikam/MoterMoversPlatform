package uk.co.motormovers.platform.common.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import uk.co.motormovers.platform.common.config.CountrySettings;
import uk.co.motormovers.platform.common.config.PlatformProperties;
import uk.co.motormovers.platform.common.model.Region;

/**
 * The deployment's country settings, open to anyone (like {@code /api/home/available}): the website and the parent
 * app use them to show money, tax, year names and the legal entity the way the country does. Nothing in them is
 * private.
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    /**
     * What {@code GET /api/settings} returns. {@code taxPercent} is, for example, 20 for VAT at 20%. {@code legalLine}
     * is the legal entity in one line, or null while it is a placeholder.
     */
    public record Settings(Region region, String country, String currency, String locale, String taxLabel,
            BigDecimal taxPercent, String timeZone, CountrySettings.SchoolYear schoolYear, List<String> yearNames,
            CountrySettings.LegalEntity legalEntity, String legalLine, String timeLabel, String dataHeldIn) {
    }

    private final Settings settings;

    public SettingsController(PlatformProperties properties, CountrySettings country) {
        this.settings = new Settings(properties.region(), country.name(), country.currency(), country.locale(),
                country.taxLabel(), country.taxPercent(), country.timeZone(), country.schoolYear(),
                List.copyOf(country.yearNames()), country.legalEntity(), country.legalEntity().line(),
                country.timeLabel(), country.dataHeldIn());
    }

    @GetMapping
    public Settings settings() {
        return settings;
    }
}
