package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

import uk.co.motormovers.platform.common.config.CountryProperties;
import uk.co.motormovers.platform.common.config.CountrySettings;
import uk.co.motormovers.platform.common.config.CountrySettingsException;
import uk.co.motormovers.platform.common.config.CountrySettingsFailureAnalyzer;
import uk.co.motormovers.platform.common.model.Region;

/** The country settings in application.yml, region by region (go-live plan, three deployments). */
class CountrySettingsTest {

    @Test
    void theUnitedKingdomIsAsToday() {
        CountrySettings uk = countries(Map.of()).get(Region.UK).validated("uk");
        assertThat(uk.currency()).isEqualTo("GBP");
        assertThat(uk.locale()).isEqualTo("en-GB");
        assertThat(uk.taxLabel()).isEqualTo("VAT");
        assertThat(uk.taxPercent()).isEqualByComparingTo("20");
        assertThat(uk.timeZone()).isEqualTo("Europe/London");
        assertThat(uk.schoolYear()).isEqualTo(new CountrySettings.SchoolYear(9, 8, 3, 9, 1, 5));
        assertThat(uk.yearNames()).containsExactly("Nursery", "Reception", "Year 1", "Year 2", "Year 3");
    }

    @Test
    void irelandHasEurosVatAt23AndASchoolYearToJune() {
        CountrySettings ie = countries(Map.of()).get(Region.IE).validated("ie");
        assertThat(ie.currency()).isEqualTo("EUR");
        assertThat(ie.locale()).isEqualTo("en-IE");
        assertThat(ie.taxPercent()).isEqualByComparingTo("23");
        assertThat(ie.timeZone()).isEqualTo("Europe/Dublin");
        assertThat(ie.schoolYear()).isEqualTo(new CountrySettings.SchoolYear(9, 6, 3, 9, 1, 4));
        assertThat(ie.yearNames()).startsWith("Preschool", "Junior Infants", "Senior Infants");
    }

    @Test
    void australiaIsQueenslandTimeGstAndACalendarSchoolYear() {
        CountrySettings au = countries(Map.of()).get(Region.AU).validated("au");
        assertThat(au.currency()).isEqualTo("AUD");
        assertThat(au.taxLabel()).isEqualTo("GST");
        assertThat(au.taxPercent()).isEqualByComparingTo("10");
        // Queensland has no daylight saving, unlike Sydney.
        assertThat(au.timeZone()).isEqualTo("Australia/Brisbane");
        assertThat(au.schoolYear()).isEqualTo(new CountrySettings.SchoolYear(1, 12, 4, 1, 5, 9));
        assertThat(au.yearNames()).startsWith("Kindy", "Prep");
    }

    @Test
    void australiaLabelsItsTaxNumberAnAbnAndTheOthersAVatNumber() {
        assertThat(countries(Map.of()).get(Region.AU).legalEntity().taxNumberLabel()).isEqualTo("ABN");
        assertThat(countries(Map.of()).get(Region.IE).legalEntity().taxNumberLabel()).isEqualTo("VAT number");
    }

    @Test
    void theLegalEntityReadsAsOneLineOnceItIsReal() {
        CountrySettings.LegalEntity real = new CountrySettings.LegalEntity("Neuroway Ltd", "1 High Street", "123",
                "VAT number", "GB456", "a@example.org", "0118");
        assertThat(real.isPlaceholder()).isFalse();
        assertThat(real.line()).isEqualTo("Neuroway Ltd, 1 High Street. Company number 123. VAT number GB456.");
        // A value that does not apply can be left blank.
        CountrySettings.LegalEntity noVat = new CountrySettings.LegalEntity("Neuroway Ltd", "1 High Street", "123",
                "VAT number", "", "a@example.org", "");
        assertThat(noVat.isPlaceholder()).isFalse();
        assertThat(noVat.line()).isEqualTo("Neuroway Ltd, 1 High Street. Company number 123.");
    }

    @Test
    void aPlaceholderLegalEntityShowsNothing() {
        CountrySettings.LegalEntity partly = new CountrySettings.LegalEntity("Neuroway Ltd",
                CountrySettings.LegalEntity.PLACEHOLDER, "123", "VAT number", "GB456", "a@example.org", "0118");
        assertThat(partly.isPlaceholder()).isTrue();
        assertThat(partly.line()).isNull();
        for (CountrySettings c : countries(Map.of()).values()) {
            assertThat(c.legalEntity().line()).isNull();
        }
    }

    @Test
    void aBadValueIsExplainedInOneLineAtStartUp() {
        CountrySettingsException bad = new CountrySettingsException("platform.countries.uk.currency is not an ISO 4217 "
                + "code: POUNDS");
        FailureAnalysis analysis = new CountrySettingsFailureAnalyzer().analyze(new RuntimeException("wrapped", bad));
        assertThat(analysis.getDescription()).contains("platform.countries.uk.currency");
        assertThat(analysis.getAction()).contains("deploy/country.env");
    }

    @Test
    void theLegalEntityIsAPlaceholderUntilKayDecides() {
        for (CountrySettings c : countries(Map.of()).values()) {
            assertThat(c.legalEntity().name()).isEqualTo("To be confirmed");
        }
    }

    @Test
    void anyValueCanBeOverriddenByAnEnvironmentVariable() {
        Map<Region, CountrySettings> countries = countries(Map.of(
                "PLATFORM_COUNTRIES_IE_TAXPERCENT", "13.5",
                "PLATFORM_COUNTRIES_AU_LEGALENTITY_NAME", "Motor Movers Australia Pty Ltd",
                "PLATFORM_COUNTRIES_UK_YEARNAMES", "Nursery, Reception, Year 1"));
        assertThat(countries.get(Region.IE).taxPercent()).isEqualByComparingTo("13.5");
        assertThat(countries.get(Region.IE).currency()).isEqualTo("EUR");
        assertThat(countries.get(Region.AU).legalEntity().name()).isEqualTo("Motor Movers Australia Pty Ltd");
        assertThat(countries.get(Region.AU).legalEntity().email()).isEqualTo("To be confirmed");
        assertThat(countries.get(Region.UK).yearNames()).containsExactly("Nursery", "Reception", "Year 1");
    }

    @Test
    void aValueThatCannotWorkStopsStartUp() {
        CountrySettings uk = countries(Map.of()).get(Region.UK);
        assertThatThrownBy(() -> new CountrySettings(uk.name(), "POUNDS", uk.locale(), uk.taxLabel(),
                uk.taxPercent(), uk.timeZone(), uk.schoolYear(), uk.yearNames(), uk.legalEntity()).validated("uk"))
                .hasMessageContaining("platform.countries.uk.currency");
        assertThatThrownBy(() -> new CountrySettings(uk.name(), uk.currency(), uk.locale(), uk.taxLabel(),
                BigDecimal.valueOf(120), uk.timeZone(), uk.schoolYear(), uk.yearNames(), uk.legalEntity())
                .validated("uk"))
                .hasMessageContaining("tax-percent");
        assertThatThrownBy(() -> new CountrySettings(uk.name(), uk.currency(), uk.locale(), uk.taxLabel(),
                uk.taxPercent(), "Europe/Atlantis", uk.schoolYear(), uk.yearNames(), uk.legalEntity())
                .validated("uk"))
                .hasMessageContaining("time-zone");
        assertThatThrownBy(() -> new CountrySettings(uk.name(), uk.currency(), uk.locale(), uk.taxLabel(),
                uk.taxPercent(), uk.timeZone(), new CountrySettings.SchoolYear(9, 8, 3, 13, 1, 5), uk.yearNames(),
                uk.legalEntity()).validated("uk"))
                .hasMessageContaining("school-year");
    }

    /** application.yml's countries, bound as Spring binds them, with these environment variables on top. */
    private static Map<Region, CountrySettings> countries(Map<String, Object> env) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, env));
        try {
            List<PropertySource<?>> yaml = new YamlPropertySourceLoader().load("application.yml",
                    new ClassPathResource("application.yml"));
            // The first document holds the defaults for every profile.
            environment.getPropertySources().addLast(yaml.getFirst());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return Binder.get(environment).bind("platform", CountryProperties.class).get().countries();
    }
}
