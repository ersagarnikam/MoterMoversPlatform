package uk.co.motormovers.platform.common.storage;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class StorageConfig {

    /** The bucket if it is fully set, otherwise the local folder, otherwise no store. */
    @Bean
    FileStorage fileStorage(StorageProperties properties, Clock clock) {
        if (properties.s3Configured()) {
            return new S3FileStorage(properties, clock);
        }
        if (properties.localConfigured()) {
            return new LocalFileStorage(properties, clock);
        }
        return new NoFileStorage();
    }

    private static final class NoFileStorage implements FileStorage {

        @Override
        public boolean available() {
            return false;
        }

        @Override
        public String uploadLink(String key, String contentType, long size) {
            throw new IllegalStateException("No file store is configured");
        }

        @Override
        public String readLink(String key) {
            throw new IllegalStateException("No file store is configured");
        }
    }
}
