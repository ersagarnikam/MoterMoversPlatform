package uk.co.motormovers.platform;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

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
            verify.invoke(new ProductionChecks(properties, env));
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
