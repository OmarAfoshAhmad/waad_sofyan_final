-- ============================================================
-- Table: provider_admin_documents
-- Depends on: providers
-- ============================================================
CREATE TABLE IF NOT EXISTS provider_admin_documents (
    id            BIGSERIAL PRIMARY KEY,
    provider_id   BIGINT NOT NULL,
    document_name VARCHAR(255) NOT NULL,
    document_type VARCHAR(100) NOT NULL,
    file_path     VARCHAR(500) NOT NULL,
    file_size     BIGINT,
    uploaded_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    uploaded_by   VARCHAR(255),

    CONSTRAINT fk_provider_docs FOREIGN KEY (provider_id) REFERENCES providers(id) ON DELETE CASCADE
);
