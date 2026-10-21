package uk.co.motormovers.platform.training.zoom;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Zoom, from two apps in the Motor Movers Zoom account (see deploy/RUNBOOK.md, Live sessions on Zoom):
 * a Server-to-Server OAuth app ({@code accountId}, {@code clientId}, {@code clientSecret}) that creates
 * meetings and starts them as the host, and a Meeting SDK app ({@code sdkKey}, {@code sdkSecret}) that shows
 * them inside the platform. {@code hostEmail} is the Zoom user who hosts every live session; when it is blank,
 * the team member starting the session must have a Zoom user with the same email.
 */
@ConfigurationProperties(prefix = "platform.zoom")
public record ZoomProperties(String accountId, String clientId, String clientSecret, String sdkKey, String sdkSecret,
        String hostEmail) {

    /** Without every key, the platform runs live sessions without video and says that Zoom is not connected. */
    public boolean configured() {
        return present(accountId) && present(clientId) && present(clientSecret) && present(sdkKey)
                && present(sdkSecret);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
