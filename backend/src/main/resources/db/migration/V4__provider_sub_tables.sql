-- ============================================================
-- V4: جداول مقدمي الخدمات الفرعية
-- ============================================================

-- أصحاب العمل المسموح بهم لكل مقدم خدمة
CREATE TABLE IF NOT EXISTS provider_allowed_employers (
    id          BIGSERIAL PRIMARY KEY,
    provider_id BIGINT NOT NULL,
    employer_id BIGINT NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(255),

    CONSTRAINT fk_allowed_provider FOREIGN KEY (provider_id) REFERENCES providers(id) ON DELETE CASCADE,
    CONSTRAINT fk_allowed_employer FOREIGN KEY (employer_id) REFERENCES employers(id) ON DELETE CASCADE,
    CONSTRAINT uq_provider_employer UNIQUE (provider_id, employer_id)
);

CREATE INDEX IF NOT EXISTS idx_allowed_employers_provider ON provider_allowed_employers(provider_id);
CREATE INDEX IF NOT EXISTS idx_allowed_employers_employer ON provider_allowed_employers(employer_id);

-- وثائق مقدمي الخدمات الإدارية
CREATE TABLE IF NOT EXISTS provider_admin_documents (
    id              BIGSERIAL PRIMARY KEY,
    provider_id     BIGINT NOT NULL,
    document_name   VARCHAR(255) NOT NULL,
    document_type   VARCHAR(100) NOT NULL,
    file_path       VARCHAR(500) NOT NULL,
    file_size       BIGINT,
    uploaded_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    uploaded_by     VARCHAR(255),

    CONSTRAINT fk_provider_docs FOREIGN KEY (provider_id) REFERENCES providers(id) ON DELETE CASCADE
);
