-- Align provider_admin_documents with ProviderAdminDocument entity mapping

ALTER TABLE provider_admin_documents
    ADD COLUMN IF NOT EXISTS type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS file_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS file_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS document_number VARCHAR(100),
    ADD COLUMN IF NOT EXISTS expiry_date DATE,
    ADD COLUMN IF NOT EXISTS notes TEXT,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

UPDATE provider_admin_documents
SET type = COALESCE(type, document_type)
WHERE type IS NULL;

UPDATE provider_admin_documents
SET file_name = COALESCE(file_name, document_name)
WHERE file_name IS NULL;

UPDATE provider_admin_documents
SET created_at = COALESCE(created_at, uploaded_at, CURRENT_TIMESTAMP)
WHERE created_at IS NULL;

UPDATE provider_admin_documents
SET updated_at = COALESCE(updated_at, uploaded_at, created_at, CURRENT_TIMESTAMP)
WHERE updated_at IS NULL;

ALTER TABLE provider_admin_documents
    ALTER COLUMN type SET NOT NULL,
    ALTER COLUMN file_name SET NOT NULL,
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL;
