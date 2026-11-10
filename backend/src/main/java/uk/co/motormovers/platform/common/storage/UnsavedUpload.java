package uk.co.motormovers.platform.common.storage;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A key handed out for an upload and not yet saved on an item. */
@Entity
@Table(name = "unsaved_upload")
public class UnsavedUpload {

    @Id
    @Column(name = "storage_key", length = 200)
    private String storageKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UnsavedUpload() {
    }

    public UnsavedUpload(String storageKey, Instant createdAt) {
        this.storageKey = storageKey;
        this.createdAt = createdAt;
    }

    public String getStorageKey() {
        return storageKey;
    }
}
