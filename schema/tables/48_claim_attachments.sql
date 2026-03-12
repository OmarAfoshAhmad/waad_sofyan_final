-- ============================================================
-- Table: claim_attachments
-- Depends on: claims
-- ============================================================
CREATE TABLE IF NOT EXISTS claim_attachments (
    id                 BIGSERIAL PRIMARY KEY,
    claim_id           BIGINT NOT NULL,
    file_name          VARCHAR(500) NOT NULL,
    file_path          VARCHAR(500),
    created_at         TIMESTAMP NOT NULL,
    file_url           VARCHAR(1000),
    original_file_name VARCHAR(500),
    file_key           VARCHAR(500),
    file_type          VARCHAR(100),
    file_size          BIGINT,
    attachment_type    VARCHAR(50)
        CHECK (attachment_type IN ('PRESCRIPTION','LAB_RESULT','XRAY','REFERRAL_LETTER','DISCHARGE_SUMMARY','OTHER')),
    uploaded_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    uploaded_by        VARCHAR(255),

    CONSTRAINT fk_claim_attachment FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_claim_attachments_claim     ON claim_attachments(claim_id);
CREATE INDEX IF NOT EXISTS idx_claim_attachments_type      ON claim_attachments(attachment_type);
CREATE INDEX IF NOT EXISTS idx_claim_attachments_date      ON claim_attachments(claim_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_claim_attachments_type_date ON claim_attachments(attachment_type, created_at DESC);
