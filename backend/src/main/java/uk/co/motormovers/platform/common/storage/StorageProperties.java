package uk.co.motormovers.platform.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where uploaded films, recordings and printables are kept. Any S3-compatible bucket ({@code endpoint}, {@code region},
 * {@code bucket} and the keys, DigitalOcean Spaces in production; RUNBOOK, File storage), or {@code localDir} for
 * development and tests. With neither, nothing can be uploaded and pasted links work as they always have.
 */
@ConfigurationProperties(prefix = "platform.storage")
public record StorageProperties(String localDir, String endpoint, String region, String bucket, String accessKey,
        String secretKey, int readHours, int maxUploadMb) {

    /** A single PUT to the bucket can be up to 5 GB (DigitalOcean's Spaces limits). */
    public static final int MOST_UPLOAD_MB = 5 * 1024;

    /** The longest a signed link can last. */
    public static final int MOST_READ_HOURS = 7 * 24;

    public boolean s3Configured() {
        return present(endpoint) && present(region) && present(bucket) && present(accessKey) && present(secretKey);
    }

    /** Some of the bucket's settings but not all: uploads and reads would fail, so production refuses it. */
    public boolean s3PartlyConfigured() {
        return !s3Configured()
                && (present(endpoint) || present(region) || present(bucket) || present(accessKey) || present(secretKey));
    }

    public boolean localConfigured() {
        return present(localDir);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
