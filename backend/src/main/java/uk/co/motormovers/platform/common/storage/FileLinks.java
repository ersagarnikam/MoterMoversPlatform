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

    /** The key sent with a new item, which must be one the API gave out. */
    public String requireKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        if (!storage.available() || !FileTypes.isKey(key)) {
            throw new IllegalArgumentException("That uploaded file could not be found. Please upload it again.");
        }
        return key;
    }
}
