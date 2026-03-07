-- V74: Relax legacy NOT NULL constraints in provider_admin_documents
-- Root cause: modern code writes file_name/type/file_url while legacy columns
-- document_name/document_type/file_path were still NOT NULL.

ALTER TABLE provider_admin_documents
    ALTER COLUMN document_name DROP NOT NULL,
    ALTER COLUMN document_type DROP NOT NULL,
    ALTER COLUMN file_path DROP NOT NULL;
