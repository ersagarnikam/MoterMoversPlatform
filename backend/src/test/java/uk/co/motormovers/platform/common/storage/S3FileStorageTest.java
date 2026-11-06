package uk.co.motormovers.platform.common.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Signing, checked against the worked example in AWS's Signature Version 4 documentation for presigned links. */
class S3FileStorageTest {

    private static final Clock AWS_EXAMPLE_TIME = Clock.fixed(Instant.parse("2013-05-24T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void matchesAwsPublishedExample() {
        StorageProperties example = new StorageProperties("", "https://s3.amazonaws.com", "us-east-1", "examplebucket",
                "AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", 8, 2048);

        String link = new S3FileStorage(example, AWS_EXAMPLE_TIME).presign("GET", "test.txt", Duration.ofHours(24),
                Map.of());

        assertThat(link).isEqualTo("https://examplebucket.s3.amazonaws.com/test.txt"
                + "?X-Amz-Algorithm=AWS4-HMAC-SHA256"
                + "&X-Amz-Credential=AKIAIOSFODNN7EXAMPLE%2F20130524%2Fus-east-1%2Fs3%2Faws4_request"
                + "&X-Amz-Date=20130524T000000Z&X-Amz-Expires=86400&X-Amz-SignedHeaders=host"
                + "&X-Amz-Signature=aeeed9bbccd4d02ee5c0109b86d86835f995330da4c265957d157751f604d404");
    }

    @Test
    void readLinksUseTheBucketsOwnHostInItsRegion() {
        URI link = URI.create(spaces().readLink("uploads/film.mp4"));

        assertThat(link.getHost()).isEqualTo("motor-movers-uk.lon1.digitaloceanspaces.com");
        assertThat(link.getPath()).isEqualTo("/uploads/film.mp4");
        assertThat(link.getRawQuery()).contains("%2Flon1%2Fs3%2Faws4_request").contains("X-Amz-Expires=28800");
    }

    @Test
    void anUploadLinkIsBoundToTheTypeAndSize() {
        String one = spaces().uploadLink("uploads/film.mp4", "video/mp4", 1000);

        assertThat(one).contains("X-Amz-SignedHeaders=content-length%3Bcontent-type%3Bhost");
        assertThat(signature(one)).isNotEqualTo(signature(spaces().uploadLink("uploads/film.mp4", "video/mp4", 1001)));
        assertThat(signature(one)).isNotEqualTo(signature(spaces().uploadLink("uploads/film.mp4", "video/webm", 1000)));
    }

    private static String signature(String link) {
        return link.substring(link.indexOf("X-Amz-Signature="));
    }

    private static S3FileStorage spaces() {
        return new S3FileStorage(new StorageProperties("", "https://lon1.digitaloceanspaces.com", "lon1",
                "motor-movers-uk", "access", "secret", 8, 2048), AWS_EXAMPLE_TIME);
    }
}
