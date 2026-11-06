package uk.co.motormovers.platform.common.storage;

import org.springframework.stereotype.Component;

/** Turns what an item holds, an uploaded file's key or a pasted link, into a link to send. */
@Component
public class FileLinks {

    private final FileStorage storage;

    public FileLinks(FileStorage storage) {
        this.storage = storage;
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

    /** The key sent with a new item, which must be one the API gave out and the store now holds. */
    public String requireKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        if (!storage.available() || !FileTypes.isKey(key) || !storage.exists(key)) {
            throw new IllegalArgumentException("That uploaded file could not be found. Please upload it again.");
        }
        return key;
    }
}
