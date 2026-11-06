package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import uk.co.motormovers.platform.common.config.CountrySettings;
import uk.co.motormovers.platform.common.config.PlatformProperties;
import uk.co.motormovers.platform.common.config.ProductionChecks;
import uk.co.motormovers.platform.common.model.Region;

class ProductionChecksTest {

    @Test
    void refusesDevelopmentSettings() {
        PlatformProperties dev = properties("dev-only-secret-change-me-dev-only-secret-change-me", true,
                "http://localhost:5173");
        assertThatThrownBy(() -> verify(dev, new MockEnvironment()))
                .hasMessageContaining("PLATFORM_JWT_SECRET")
                .hasMessageContaining("SEED_DEMO_DATA")
                .hasMessageContaining("SPRING_MAIL_HOST")
                .hasMessageContaining("PLATFORM_WEB_URL")
                .hasMessageContaining("must be https://");
    }

    @Test
    void acceptsProductionSettings() {
        PlatformProperties prod = properties("f3a9c1e07b5d4a2e9c8b7a6f5e4d3c2b1a0f9e8d7c6b", false,
                "https://app.example.org");
        assertThatCode(() -> verify(prod, new MockEnvironment().withProperty("spring.mail.host", "smtp.example.org")))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesAPlaceholderLegalEntity() {
        PlatformProperties prod = properties("f3a9c1e07b5d4a2e9c8b7a6f5e4d3c2b1a0f9e8d7c6b", false,
                "https://app.example.org");
        MockEnvironment env = new MockEnvironment().withProperty("spring.mail.host", "smtp.example.org");
        CountrySettings.LegalEntity placeholder = new CountrySettings.LegalEntity(REAL_ENTITY.name(),
                REAL_ENTITY.address(), CountrySettings.LegalEntity.PLACEHOLDER, "VAT number", REAL_ENTITY.taxNumber(),
                REAL_ENTITY.email(), REAL_ENTITY.phone());
        assertThatThrownBy(() -> new ProductionChecksCall(prod, env, placeholder).run())
                .hasMessageContaining("legal entity is still a placeholder");
    }

    @Test
    void refusesHalfTheStripeKeys() {
        PlatformProperties prod = properties("f3a9c1e07b5d4a2e9c8b7a6f5e4d3c2b1a0f9e8d7c6b", false,
                "https://app.example.org");
        // Card payments would be taken but never marked paid.
        assertThatThrownBy(() -> verify(prod, new MockEnvironment().withProperty("spring.mail.host", "smtp.example.org")
                .withProperty("platform.stripe.secret-key", "sk_live_x")))
                .hasMessageContaining("STRIPE_WEBHOOK_SECRET");
    }

    private static final CountrySettings.LegalEntity REAL_ENTITY = new CountrySettings.LegalEntity("Neuroway Ltd",
            "1 High Street, Wokingham", "01234567", "VAT number", "GB123456789", "hello@example.org", "0118 000 0000");

    private static CountrySettings country(CountrySettings.LegalEntity entity) {
        return new CountrySettings("United Kingdom", "GBP", "en-GB", "VAT", BigDecimal.valueOf(20), "Europe/London",
                new CountrySettings.SchoolYear(9, 8, 3, 9, 1, 5), List.of("Reception"), entity, "Credit note", "UK time");
    }

    /** Runs the checks with a given legal entity. */
    private record ProductionChecksCall(PlatformProperties properties, MockEnvironment env,
            CountrySettings.LegalEntity entity) {

        void run() throws Throwable {
            Method verify = ProductionChecks.class.getDeclaredMethod("verify");
            verify.setAccessible(true);
            try {
                verify.invoke(new ProductionChecks(properties, env, country(entity)));
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    private static PlatformProperties properties(String secret, boolean seed, String webUrl) {
        return new PlatformProperties(Region.UK, 365, 0.7, seed,
                new PlatformProperties.Jwt(secret, 480, 43200),
                new PlatformProperties.Cors(List.of(webUrl)),
                new PlatformProperties.Mail("Motor Movers <no-reply@example.org>", webUrl));
    }

    private static void verify(PlatformProperties properties, MockEnvironment env) throws Throwable {
        Method verify = ProductionChecks.class.getDeclaredMethod("verify");
        verify.setAccessible(true);
        try {
            verify.invoke(new ProductionChecks(properties, env, country(REAL_ENTITY)));
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
