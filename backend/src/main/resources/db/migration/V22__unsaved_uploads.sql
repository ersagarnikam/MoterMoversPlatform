-- Keys handed out for an upload and not yet saved on an item. Only these can ever be cleaned up (UnsavedUploadJob).
CREATE TABLE unsaved_upload (
    storage_key VARCHAR(200) PRIMARY KEY,
    created_at  TIMESTAMP NOT NULL
);
