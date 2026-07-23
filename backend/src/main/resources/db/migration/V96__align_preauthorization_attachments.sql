ALTER TABLE pre_authorization_attachments
    ADD COLUMN IF NOT EXISTS pre_authorization_id BIGINT,
    ADD COLUMN IF NOT EXISTS original_file_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS stored_file_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(100);

UPDATE pre_authorization_attachments
SET pre_authorization_id = COALESCE(pre_authorization_id, preauthorization_request_id),
    original_file_name = COALESCE(original_file_name, file_name, 'legacy-attachment-' || id),
    stored_file_name = COALESCE(stored_file_name, file_name),
    file_path = COALESCE(file_path, stored_file_name, file_name, 'legacy-attachment-' || id),
    created_at = COALESCE(created_at, uploaded_at, CURRENT_TIMESTAMP),
    created_by = COALESCE(created_by, uploaded_by)
WHERE pre_authorization_id IS NULL
   OR original_file_name IS NULL
   OR stored_file_name IS NULL
   OR file_path IS NULL
   OR created_at IS NULL
   OR created_by IS NULL;

ALTER TABLE pre_authorization_attachments
    ALTER COLUMN pre_authorization_id SET NOT NULL,
    ALTER COLUMN original_file_name SET NOT NULL,
    ALTER COLUMN file_path SET NOT NULL,
    ALTER COLUMN created_at SET NOT NULL;

ALTER TABLE pre_authorization_attachments
    DROP CONSTRAINT IF EXISTS fk_pre_authorization_attachments_request;

ALTER TABLE pre_authorization_attachments
    ADD CONSTRAINT fk_pre_authorization_attachments_request
        FOREIGN KEY (pre_authorization_id)
        REFERENCES preauthorization_requests(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_pre_authorization_attachments_request
    ON pre_authorization_attachments(pre_authorization_id);
