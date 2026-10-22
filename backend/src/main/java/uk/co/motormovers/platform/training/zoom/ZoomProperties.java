package uk.co.motormovers.platform.training.zoom;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Zoom, from apps in the Motor Movers Zoom account (see deploy/RUNBOOK.md, Live sessions on Zoom): a
 * Server-to-Server OAuth app ({@code accountId}, {@code clientId}, {@code clientSecret}) that creates meetings and
 * starts them as the host, which is enough to run sessions in Zoom; and optionally a separate Meeting SDK app
 * ({@code sdkKey}, {@code sdkSecret}, a General app with the Meeting SDK) that shows them inside the platform. {@code hostEmail} is the Zoom user who hosts every live session; when it is blank,
 * the team member starting the session must have a Zoom user with the same email.
 */
@ConfigurationProperties(prefix = "platform.zoom")
public record ZoomProperties(String accountId, String clientId, String clientSecret, String sdkKey, String sdkSecret,
        String hostEmail) {

    /** Without the Server-to-Server keys, the platform runs live sessions without video and says so. */
    public boolean configured() {
        return present(accountId) && present(clientId) && present(clientSecret);
    }

    /**
     * Whether meetings show inside the platform. That needs a Meeting SDK app's own keys: a Server-to-Server app's
     * keys cannot sign the SDK (Zoom answers "Signature is invalid"), so they are ignored here and sessions open in
     * Zoom instead.
     */
    public boolean embedded() {
        return configured() && present(sdkKey) && present(sdkSecret) && !sdkKey.strip().equals(clientId.strip());
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
