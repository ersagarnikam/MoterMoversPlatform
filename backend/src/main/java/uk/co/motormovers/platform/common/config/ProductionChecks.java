package uk.co.motormovers.platform.common.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import uk.co.motormovers.platform.common.storage.StorageProperties;

/**
 * Refuses to start a production deployment with development settings: the sample signing secret, demo
 * data, no mail server (password links would only reach the log), plain-http addresses, or a placeholder legal
 * entity.
 */
@Component
@Profile("prod")
public class ProductionChecks {

    private final PlatformProperties properties;
    private final Environment env;
    private final CountrySettings country;
    private final StorageProperties storage;

    public ProductionChecks(PlatformProperties properties, Environment env, CountrySettings country,
            StorageProperties storage) {
        this.properties = properties;
        this.env = env;
        this.country = country;
        this.storage = storage;
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
        if (storage.localConfigured()) {
            problems.add("STORAGE_LOCAL_DIR is for development only: use a bucket, or leave it unset");
        }
        if (storage.s3PartlyConfigured()) {
            problems.add("STORAGE_ENDPOINT, STORAGE_REGION, STORAGE_BUCKET, STORAGE_ACCESS_KEY and STORAGE_SECRET_KEY"
                    + " must be set together (or all left blank)");
        }
        if (storage.s3Configured() && !storage.endpoint().startsWith("https://")) {
            problems.add("STORAGE_ENDPOINT must be an https:// address");
        }
        if (storage.s3Configured() && storage.endpoint().contains(".cdn.")) {
            // Signed requests through the Spaces CDN are not cached, may be charged twice and stop at 8100 KiB.
            problems.add("STORAGE_ENDPOINT must be the bucket's region endpoint, not its CDN");
        }
        if (storage.maxUploadMb() > StorageProperties.MOST_UPLOAD_MB) {
            problems.add("STORAGE_MAX_UPLOAD_MB can be at most " + StorageProperties.MOST_UPLOAD_MB);
        }
        if (storage.readHours() < 1 || storage.readHours() > StorageProperties.MOST_READ_HOURS) {
            problems.add("STORAGE_READ_HOURS must be between 1 and " + StorageProperties.MOST_READ_HOURS);
        }
        if (storage.unsavedDays() < 1) {
            problems.add("STORAGE_UNSAVED_DAYS must be at least 1");
        }
        if (country.legalEntity().isPlaceholder()) {
            problems.add("The legal entity is still a placeholder: set PLATFORM_COUNTRIES_" + properties.region()
                    + "_LEGALENTITY_NAME, _ADDRESS, _COMPANYNUMBER, _TAXNUMBER, _EMAIL and _PHONE in deploy/country.env");
        }
        properties.cors().allowedOrigins().stream()
                .filter(o -> !o.startsWith("https://"))
                .forEach(o -> problems.add("CORS origin " + o + " must be https://"));
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Not safe to start in production:\n - " + String.join("\n - ", problems));
        }
    }
}
