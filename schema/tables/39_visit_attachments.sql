-- ============================================================
-- Table: visit_attachments
-- Depends on: visits
-- ============================================================
CREATE TABLE IF NOT EXISTS visit_attachments (
    id                 BIGSERIAL PRIMARY KEY,
    visit_id           BIGINT NOT NULL,
    file_name          VARCHAR(500) NOT NULL,
    original_file_name VARCHAR(500),
    file_key           VARCHAR(500),
    file_type          VARCHAR(100),
    file_size          BIGINT,
    attachment_type    VARCHAR(50)
        CHECK (attachment_type IN ('XRAY','MRI','CT_SCAN','LAB_RESULT','PRESCRIPTION','MEDICAL_REPORT','OTHER')),
    description        TEXT,
    uploaded_by        VARCHAR(100),
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_visit_attachment FOREIGN KEY (visit_id) REFERENCES visits(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_visit_attachments_visit      ON visit_attachments(visit_id);
CREATE INDEX IF NOT EXISTS idx_visit_attachments_type       ON visit_attachments(attachment_type);
CREATE INDEX IF NOT EXISTS idx_visit_attachments_visit_date ON visit_attachments(visit_id, created_at DESC);
