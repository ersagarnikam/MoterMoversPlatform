package uk.co.motormovers.platform.training.zoom;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The Meeting SDK signature: a JWT signed with HS256 and the SDK app's secret, naming the meeting and the role
 * (0 joins as a participant, 1 starts as host). It is valid for two hours; Zoom needs at least 30 minutes.
 */
public final class ZoomSignature {

    public static final int PARTICIPANT = 0;
    public static final int HOST = 1;
    static final long VALID_SECONDS = 2 * 60 * 60;

    private ZoomSignature() {
    }

    public static String sign(String sdkKey, String sdkSecret, String meetingNumber, int role, Instant now) {
        // Issued slightly in the past, so a client clock a little behind Zoom's does not reject it.
        long iat = now.getEpochSecond() - 30;
        long exp = iat + VALID_SECONDS;
        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payload = "{\"appKey\":\"" + sdkKey + "\",\"sdkKey\":\"" + sdkKey + "\",\"mn\":\"" + meetingNumber
                + "\",\"role\":" + role + ",\"iat\":" + iat + ",\"exp\":" + exp + ",\"tokenExp\":" + exp + "}";
        String unsigned = base64Url(header.getBytes(StandardCharsets.UTF_8)) + "."
                + base64Url(payload.getBytes(StandardCharsets.UTF_8));
        return unsigned + "." + base64Url(hmac(sdkSecret, unsigned));
    }

    private static byte[] hmac(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
