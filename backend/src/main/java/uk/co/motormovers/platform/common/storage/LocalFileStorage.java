package uk.co.motormovers.platform.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.access.AccessDeniedException;

/**
 * Files on this machine, for development and tests; production refuses it. It behaves as the bucket does: links are
 * signed, expire, and an upload link accepts one type and size. They are relative, so the browser stays on its own
 * origin. The signing key lasts as long as the process.
 */
public class LocalFileStorage implements FileStorage {

    static final String PATH = "/api/files/local/";

    private static final Duration UPLOAD_LINK = Duration.ofHours(1);
    private static final String REFUSED = "This file link is not valid or has expired";

    private final Path directory;
    private final Duration readLink;
    private final Clock clock;
    private final byte[] secret = new byte[32];

    LocalFileStorage(StorageProperties properties, Clock clock) {
        this.directory = Path.of(properties.localDir().strip());
        this.readLink = Duration.ofHours(properties.readHours());
        this.clock = clock;
        new SecureRandom().nextBytes(secret);
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String uploadLink(String key, String contentType, long size) {
        long expires = clock.instant().plus(UPLOAD_LINK).getEpochSecond();
        return PATH + key + "?expires=" + expires + "&type=" + URLEncoder.encode(contentType, StandardCharsets.UTF_8)
                + "&size=" + size + "&sig=" + sign("PUT", key, expires, contentType, size);
    }

    @Override
    public String readLink(String key) {
        long expires = clock.instant().plus(readLink).getEpochSecond();
        return PATH + key + "?expires=" + expires + "&sig=" + sign("GET", key, expires, "", 0);
    }

    @Override
    public String downloadLink(String key, String fileName) {
        long expires = clock.instant().plus(readLink).getEpochSecond();
        return PATH + key + "?expires=" + expires + "&name=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replace("+", "%20") + "&sig=" + sign("GET", key, expires, fileName, 0);
    }

    @Override
    public boolean exists(String key) {
        return Files.isRegularFile(pathOf(key));
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(pathOf(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Takes one upload. The type and size came with the link and must be what the browser actually sends. */
    void save(String key, long expires, String type, long size, String sig, String sentType, InputStream body) {
        require(sig, sign("PUT", key, expires, type, size), expires);
        // Spring adds a charset to the header on the way in; a bucket would see the type alone.
        if (!type.equals(sentType.split(";")[0].strip())) {
            throw new AccessDeniedException(REFUSED);
        }
        try {
            Files.createDirectories(directory);
            Path incoming = Files.createTempFile(directory, "upload", ".part");
            try {
                if (Files.copy(body, incoming, StandardCopyOption.REPLACE_EXISTING) != size) {
                    throw new AccessDeniedException(REFUSED);
                }
                Files.move(incoming, pathOf(key), StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(incoming);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The file behind a signed read link; a download link's name is signed too. */
    Path open(String key, long expires, String name, String sig) {
        require(sig, sign("GET", key, expires, name, 0), expires);
        Path file = pathOf(key);
        if (!Files.isRegularFile(file)) {
            throw new AccessDeniedException(REFUSED);
        }
        return file;
    }

    private Path pathOf(String key) {
        return directory.resolve(key.substring(key.lastIndexOf('/') + 1));
    }

    private void require(String given, String expected, long expires) {
        boolean signed = MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
        if (!signed || clock.instant().getEpochSecond() > expires) {
            throw new AccessDeniedException(REFUSED);
        }
    }

    private String sign(String method, String key, long expires, String type, long size) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            String request = String.join("\n", method, key, Long.toString(expires), type, Long.toString(size));
            return HexFormat.of().formatHex(mac.doFinal(request.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
