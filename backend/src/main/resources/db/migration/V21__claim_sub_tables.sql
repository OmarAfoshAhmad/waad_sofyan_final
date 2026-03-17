-- ============================================================
-- V21: الجداول الفرعية للمطالبات
-- ============================================================

-- مرفقات المطالبات
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

CREATE INDEX IF NOT EXISTS idx_claim_attachments_claim      ON claim_attachments(claim_id);
CREATE INDEX IF NOT EXISTS idx_claim_attachments_type       ON claim_attachments(attachment_type);
CREATE INDEX IF NOT EXISTS idx_claim_attachments_date       ON claim_attachments(claim_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_claim_attachments_type_date  ON claim_attachments(attachment_type, created_at DESC);

-- تاريخ حالات المطالبة
CREATE TABLE IF NOT EXISTS claim_history (
    id          BIGSERIAL PRIMARY KEY,
    claim_id    BIGINT NOT NULL,
    old_status  VARCHAR(50),
    new_status  VARCHAR(50),
    changed_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    changed_by  VARCHAR(255),
    reason      TEXT,

    CONSTRAINT fk_claim_history FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_claim_history_timeline ON claim_history(claim_id, changed_at DESC, new_status);

-- سجل تدقيق المطالبات
CREATE TABLE IF NOT EXISTS claim_audit_logs (
    id                        BIGSERIAL PRIMARY KEY,
    claim_id                  BIGINT NOT NULL,
    change_type               VARCHAR(50) NOT NULL,
    previous_status           VARCHAR(30),
    new_status                VARCHAR(30),
    previous_requested_amount NUMERIC(15,2),
    new_requested_amount      NUMERIC(15,2),
    previous_approved_amount  NUMERIC(15,2),
    new_approved_amount       NUMERIC(15,2),
    actor_user_id             BIGINT NOT NULL,
    actor_username            VARCHAR(100) NOT NULL,
    actor_role                VARCHAR(50) NOT NULL,
    timestamp                 TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    comment                   TEXT,
    ip_address                VARCHAR(45),
    before_snapshot           TEXT,
    after_snapshot            TEXT,

    CONSTRAINT fk_claim_audit_claim FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_claim_audit_claim_timestamp ON claim_audit_logs(claim_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_claim_audit_user_timestamp  ON claim_audit_logs(actor_user_id, timestamp DESC);

-- أسباب رفض المطالبات (V226)
CREATE TABLE IF NOT EXISTS claim_rejection_reasons (
    id          BIGSERIAL PRIMARY KEY,
    reason_text VARCHAR(500) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT uq_claim_rejection_reason_text UNIQUE (reason_text)
);
