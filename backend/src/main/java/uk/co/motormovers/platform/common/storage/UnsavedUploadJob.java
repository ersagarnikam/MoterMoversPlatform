package uk.co.motormovers.platform.common.storage;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import uk.co.motormovers.platform.common.exception.SafeErrors;

/**
 * Deletes files uploaded for a form that was never saved. It only ever takes keys from unsaved_upload, the keys the API
 * handed out, and never lists the bucket, so it cannot remove a file it did not hand out.
 */
@Component
public class UnsavedUploadJob {

    private static final Logger log = LoggerFactory.getLogger(UnsavedUploadJob.class);

    private final UnsavedUploadRepository unsaved;
    private final FileStorage storage;
    private final StorageProperties properties;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public UnsavedUploadJob(UnsavedUploadRepository unsaved, FileStorage storage, StorageProperties properties,
            TransactionTemplate transactions, Clock clock) {
        this.unsaved = unsaved;
        this.storage = storage;
        this.properties = properties;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** Daily. Returns how many files were deleted. One that cannot be deleted keeps its row for the next day. */
    @Scheduled(cron = "0 45 2 * * *")
    public int cleanUp() {
        if (!storage.available()) {
            return 0;
        }
        Instant cutoff = clock.instant().minus(Duration.ofDays(properties.unsavedDays()));
        int deleted = 0;
        int failed = 0;
        for (String key : unsaved.keysCreatedBefore(cutoff)) {
            try {
                if (Boolean.TRUE.equals(transactions.execute(status -> deleteIfUnsaved(key)))) {
                    deleted++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.warn("Could not delete unsaved upload {}: {}", key, SafeErrors.describe(e));
            }
        }
        if (deleted > 0 || failed > 0) {
            log.info("Unsaved uploads: deleted {}, could not delete {}", deleted, failed);
        }
        return deleted;
    }

    /** One key, with its row locked: a save of this key waits for the delete and then finds the file gone. */
    private boolean deleteIfUnsaved(String key) {
        if (unsaved.lock(key).isEmpty()) {
            return false;
        }
        boolean delete = unsaved.uses(key) == 0;
        if (delete) {
            storage.delete(key);
        }
        unsaved.forget(key);
        return delete;
    }
}
