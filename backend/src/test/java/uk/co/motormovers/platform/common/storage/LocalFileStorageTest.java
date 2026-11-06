package uk.co.motormovers.platform.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.access.AccessDeniedException;

/** The local store's links end, as a bucket's do. */
class LocalFileStorageTest {

    private static final String KEY = "uploads/11111111-1111-1111-1111-111111111111.mp3";
    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");

    @TempDir
    Path directory;

    @Test
    void aReadLinkEndsAfterItsHours() throws Exception {
        LocalFileStorage storage = storage(NOW);
        String upload = storage.uploadLink(KEY, "audio/mpeg", 3);
        storage.save(KEY, number(upload, "expires"), "audio/mpeg", 3, text(upload, "sig"), "audio/mpeg",
                new ByteArrayInputStream(new byte[3]));
        assertThat(Files.list(directory)).hasSize(1);

        String link = storage.readLink(KEY);
        assertThat(number(link, "expires")).isEqualTo(NOW.plus(Duration.ofHours(8)).getEpochSecond());
        assertThat(storage.open(KEY, number(link, "expires"), "", text(link, "sig"))).exists();

        // The same link, read once its time is up by a store with the same signing key.
        assertThat(number(link, "expires")).isLessThan(NOW.plus(Duration.ofHours(9)).getEpochSecond());
        assertThatThrownBy(() -> storage.open(KEY, number(link, "expires") + 1, "", text(link, "sig")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aFailedUploadLeavesNothingBehind() throws Exception {
        LocalFileStorage storage = storage(NOW);
        String upload = storage.uploadLink(KEY, "audio/mpeg", 3);
        assertThatThrownBy(() -> storage.save(KEY, number(upload, "expires"), "audio/mpeg", 3, text(upload, "sig"),
                "audio/mpeg", new ByteArrayInputStream(new byte[4])))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(Files.list(directory)).isEmpty();
    }

    private LocalFileStorage storage(Instant now) {
        return new LocalFileStorage(new StorageProperties(directory.toString(), "", "", "", "", "", 8, 2048),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private static long number(String link, String name) {
        return Long.parseLong(text(link, name));
    }

    private static String text(String link, String name) {
        Matcher m = Pattern.compile("[?&]" + name + "=([^&]+)").matcher(link);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }
}
