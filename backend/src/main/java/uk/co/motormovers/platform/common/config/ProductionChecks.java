package uk.co.motormovers.platform.common.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Refuses to start a production deployment with development settings: the sample signing secret, demo
 * data, no mail server (password links would only reach the log), or plain-http addresses.
 */
@Component
@Profile("prod")
public class ProductionChecks {

    private final PlatformProperties properties;
    private final Environment env;

    public ProductionChecks(PlatformProperties properties, Environment env) {
        this.properties = properties;
        this.env = env;
    }

    @PostConstruct
    void verify() {
        List<String> problems = new ArrayList<>();
        String secret = properties.jwt().secret();
        if (secret == null || secret.startsWith("dev-only") || secret.length() < 32) {
            problems.add("PLATFORM_JWT_SECRET must be set to a random value of at least 32 characters");
        }
        if (properties.seedDemoData()) {
            problems.add("SEED_DEMO_DATA must be off");
        }
        if (env.getProperty("spring.mail.host", "").isBlank()) {
            problems.add("SPRING_MAIL_HOST must be set so invite and password emails are sent");
        }
        if (properties.mail().from() == null || properties.mail().from().isBlank()) {
            problems.add("PLATFORM_MAIL_FROM must be set");
        }
        if (properties.mail().webUrl() == null || !properties.mail().webUrl().startsWith("https://")) {
            problems.add("PLATFORM_WEB_URL must be the https:// address of the web app");
        }
        boolean stripeKey = !env.getProperty("platform.stripe.secret-key", "").isBlank();
        boolean stripeWebhook = !env.getProperty("platform.stripe.webhook-secret", "").isBlank();
        if (stripeKey != stripeWebhook) {
            // With only one, card payments would be taken but never marked paid, or never offered.
            problems.add("STRIPE_SECRET_KEY and STRIPE_WEBHOOK_SECRET must be set together (or both left blank)");
        }
        if (env.getProperty("platform.login-limit-per-ip", Integer.class, 50) > 50) {
            problems.add("LOGIN_LIMIT_PER_IP is for load tests only: leave it unset");
        }
        properties.cors().allowedOrigins().stream()
                .filter(o -> !o.startsWith("https://"))
                .forEach(o -> problems.add("CORS origin " + o + " must be https://"));
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Not safe to start in production:\n - " + String.join("\n - ", problems));
        }
    }
}
