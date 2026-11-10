package uk.co.motormovers.platform.common.storage;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Turns what an item holds, an uploaded file's key or a pasted link, into a link to send. */
@Component
public class FileLinks {

    private final FileStorage storage;
    private final UnsavedUploadRepository unsaved;

    public FileLinks(FileStorage storage, UnsavedUploadRepository unsaved) {
        this.storage = storage;
        this.unsaved = unsaved;
    }

    /**
     * A short-lived signed link for an uploaded file, else the pasted link as it is. Call it only after the caller's
     * access checks, and pass a null key for someone who may not have the file.
     */
    public String link(String key, String pastedUrl) {
        return key != null && storage.available() ? storage.readLink(key) : pastedUrl;
    }

    /** As {@link #link}, for a file to save, named after the item with the file's own extension. */
    public String download(String key, String pastedUrl, String title) {
        return key != null && storage.available() ? storage.downloadLink(key, FileTypes.fileName(title, key)) : pastedUrl;
    }

    /**
     * The key sent with a new item, which must be one the API gave out and the store now holds. It stops counting as
     * unsaved in the item's own transaction, so a save that fails leaves it to the clean-up. The row goes first, so a
     * clean-up already deleting this file finishes before the store is asked.
     */
    @Transactional
    public String requireKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        if (storage.available() && FileTypes.isKey(key)) {
            unsaved.forget(key);
            if (storage.exists(key)) {
                return key;
            }
        }
        throw new IllegalArgumentException("That uploaded file could not be found. Please upload it again.");
    }
}
