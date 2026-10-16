package uk.co.motormovers.platform.common.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import uk.co.motormovers.platform.common.model.Region;

@ConfigurationProperties(prefix = "platform")
public record PlatformProperties(
        Region region,
        int retentionDays,
        double participationFlagThreshold,
        boolean seedDemoData,
        Jwt jwt,
        Cors cors) {

    /** {@code parentTtlMinutes} of 0 means parents get the same lifetime as everyone else. */
    public record Jwt(String secret, long ttlMinutes, long parentTtlMinutes) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
