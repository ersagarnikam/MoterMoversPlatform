package uk.co.motormovers.platform.common.storage;

/**
 * Keeps uploaded files out of the API: the browser sends a file straight to the store with a short-lived upload link,
 * and reads it back with a short-lived signed link. Callers check access before asking for a read link.
 */
public interface FileStorage {

    /** False when no store is configured; the other methods must not be called then. */
    boolean available();

    /** A link that accepts one PUT of exactly this type and size. */
    String uploadLink(String key, String contentType, long size);

    /** A link that reads the file, ranges included, so a film can be seeked. */
    String readLink(String key);
}
