package uk.co.motormovers.platform.payment.stripe;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Checks that a webhook call really came from Stripe (https://docs.stripe.com/webhooks#verify-manually): the
 * Stripe-Signature header holds a timestamp {@code t} and one or more {@code v1} HMAC-SHA256 signatures of
 * "t.payload" with the endpoint's signing secret. Old timestamps are refused, so a recorded call cannot be replayed.
 */
public final class StripeSignature {

    static final Duration TOLERANCE = Duration.ofMinutes(5);

    private StripeSignature() {
    }

    public static boolean valid(String header, String payload, String secret, Instant now) {
        if (header == null || secret == null) {
            return false;
        }
        String timestamp = null;
        java.util.List<String> signatures = new java.util.ArrayList<>();
        for (String part : header.split(",")) {
            String[] kv = part.strip().split("=", 2);
            if (kv.length == 2 && kv[0].equals("t")) {
                timestamp = kv[1];
            } else if (kv.length == 2 && kv[0].equals("v1")) {
                signatures.add(kv[1]);
            }
        }
        if (timestamp == null || signatures.isEmpty()) {
            return false;
        }
        long seconds;
        try {
            seconds = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return false;
        }
        if (Duration.between(Instant.ofEpochSecond(seconds), now).abs().compareTo(TOLERANCE) > 0) {
            return false;
        }
        byte[] expected = sign(secret, timestamp + "." + payload).getBytes(StandardCharsets.UTF_8);
        return signatures.stream()
                .anyMatch(s -> MessageDigest.isEqual(expected, s.getBytes(StandardCharsets.UTF_8)));
    }

    /** The v1 signature of a payload, as Stripe makes it. */
    public static String sign(String secret, String signedPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
