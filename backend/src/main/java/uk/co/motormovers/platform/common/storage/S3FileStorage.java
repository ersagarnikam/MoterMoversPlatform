package uk.co.motormovers.platform.common.storage;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Any S3-compatible bucket, through presigned links (AWS Signature Version 4, query string). Signing happens here with
 * no call to the store. Links name the bucket's own host, virtual-hosted style, never a CDN: DigitalOcean's Spaces docs
 * say signed requests are not cached there, may be charged twice, and are limited to 8100 KiB.
 */
class S3FileStorage implements FileStorage {

    private static final Duration UPLOAD_LINK = Duration.ofHours(1);
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final StorageProperties properties;
    private final Clock clock;
    private final String scheme;
    private final String host;

    S3FileStorage(StorageProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        URI endpoint = URI.create(properties.endpoint().strip());
        this.scheme = endpoint.getScheme();
        this.host = properties.bucket().strip() + "." + endpoint.getHost();
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String uploadLink(String key, String contentType, long size) {
        // Signing the type and length makes the store refuse anything other than the file the API agreed to.
        return presign("PUT", key, UPLOAD_LINK,
                Map.of("content-length", Long.toString(size), "content-type", contentType));
    }

    @Override
    public String readLink(String key) {
        return presign("GET", key, Duration.ofHours(properties.readHours()), Map.of());
    }

    String presign(String method, String key, Duration lifetime, Map<String, String> headers) {
        String timestamp = TIMESTAMP.format(clock.instant());
        String date = timestamp.substring(0, 8);
        String region = properties.region().strip();
        String scope = date + "/" + region + "/s3/aws4_request";

        TreeMap<String, String> signed = new TreeMap<>(headers);
        signed.put("host", host);
        String signedHeaders = String.join(";", signed.keySet());

        TreeMap<String, String> query = new TreeMap<>();
        query.put("X-Amz-Algorithm", "AWS4-HMAC-SHA256");
        query.put("X-Amz-Credential", properties.accessKey().strip() + "/" + scope);
        query.put("X-Amz-Date", timestamp);
        query.put("X-Amz-Expires", Long.toString(lifetime.toSeconds()));
        query.put("X-Amz-SignedHeaders", signedHeaders);
        String canonicalQuery = query.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));

        String path = "/" + key;
        String canonicalHeaders = signed.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue() + "\n")
                .collect(Collectors.joining());
        String canonicalRequest = String.join("\n", method, path, canonicalQuery, canonicalHeaders, signedHeaders,
                "UNSIGNED-PAYLOAD");
        String toSign = String.join("\n", "AWS4-HMAC-SHA256", timestamp, scope, sha256(canonicalRequest));

        byte[] signingKey = hmac(("AWS4" + properties.secretKey().strip()).getBytes(StandardCharsets.UTF_8), date);
        signingKey = hmac(signingKey, region);
        signingKey = hmac(signingKey, "s3");
        signingKey = hmac(signingKey, "aws4_request");
        String signature = HexFormat.of().formatHex(hmac(signingKey, toSign));

        return scheme + "://" + host + path + "?" + canonicalQuery + "&X-Amz-Signature=" + signature;
    }

    /** Percent-encoding as Signature Version 4 wants it: unreserved characters stay, everything else is encoded. */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hmac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
