-- V20: Align claim_attachments table with ClaimAttachment entity for ddl-auto=validate

-- Required by entity
ALTER TABLE claim_attachments
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;

ALTER TABLE claim_attachments
    ADD COLUMN IF NOT EXISTS file_url VARCHAR(1000);

ALTER TABLE claim_attachments
    ADD COLUMN IF NOT EXISTS original_file_name VARCHAR(500);

ALTER TABLE claim_attachments
    ADD COLUMN IF NOT EXISTS file_key VARCHAR(500);

-- Backfill from legacy columns where possible
UPDATE claim_attachments
SET created_at = COALESCE(created_at, uploaded_at)
WHERE created_at IS NULL;

UPDATE claim_attachments
SET file_url = COALESCE(file_url, file_path)
WHERE file_url IS NULL;

UPDATE claim_attachments
SET original_file_name = COALESCE(original_file_name, file_name)
WHERE original_file_name IS NULL;

-- Enforce entity constraint
ALTER TABLE claim_attachments
    ALTER COLUMN created_at SET NOT NULL;
