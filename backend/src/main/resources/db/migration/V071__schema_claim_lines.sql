-- ============================================================
-- V071: Claim lines, attachments, history, and audit logs
-- ============================================================
-- Depends on: V070 (claims), V021 (medical_services), V010 (users)

-- ----------------------------------------------------------
-- SECTION 1: Claim line items (service-level detail)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS claim_lines (
    id          BIGSERIAL PRIMARY KEY,
    claim_id    BIGINT NOT NULL,

    -- Service details
    service_code        VARCHAR(50) NOT NULL,
    service_description VARCHAR(255),
    quantity            INTEGER,
    unit_price          NUMERIC(15,2) CHECK (unit_price >= 0),
    total_amount        NUMERIC(15,2) CHECK (total_amount >= 0),
    total_price         NUMERIC(15,2) NOT NULL,

    -- Service catalog link
    medical_service_id      BIGINT,
    service_name            VARCHAR(255),
    service_category_id     BIGINT,
    service_category_name   VARCHAR(200),

    -- PA requirement
    requires_pa BOOLEAN NOT NULL DEFAULT false,

    -- Approvals
    line_number         INTEGER,
    approved_amount     NUMERIC(15,2),
    approved_units      INTEGER,
    approval_notes      TEXT,

    -- Coverage snapshots at time of claim submission
    coverage_percent_snapshot       INTEGER,
    patient_copay_percent_snapshot  INTEGER,
    times_limit_snapshot            INTEGER,
    amount_limit_snapshot           NUMERIC(15,2),

    -- Refused amount tracking
    refused_amount NUMERIC(15,2) DEFAULT 0,

    -- Audit
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(255),

    CONSTRAINT fk_claim_line_claim   FOREIGN KEY (claim_id)          REFERENCES claims(id)          ON DELETE CASCADE,
    CONSTRAINT fk_claim_line_service FOREIGN KEY (medical_service_id) REFERENCES medical_services(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_claim_line_claim   ON claim_lines(claim_id);
CREATE INDEX IF NOT EXISTS idx_claim_line_service ON claim_lines(medical_service_id);
CREATE INDEX IF NOT EXISTS idx_claim_line_service_analysis ON claim_lines(medical_service_id, total_price);

-- ----------------------------------------------------------
-- SECTION 2: Claim supporting documents
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS claim_attachments (
    id                  BIGSERIAL PRIMARY KEY,
    claim_id            BIGINT NOT NULL,
    file_name           VARCHAR(500) NOT NULL,
    file_path           VARCHAR(500),
    created_at          TIMESTAMP NOT NULL,
    file_url            VARCHAR(1000),
    original_file_name  VARCHAR(500),
    file_key            VARCHAR(500),
    file_type           VARCHAR(100),
    file_size           BIGINT,
    attachment_type     VARCHAR(50)
        CHECK (attachment_type IN ('PRESCRIPTION','LAB_RESULT','XRAY','REFERRAL_LETTER','DISCHARGE_SUMMARY','OTHER')),
    uploaded_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    uploaded_by         VARCHAR(255),

    CONSTRAINT fk_claim_attachment FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_claim_attachments_claim ON claim_attachments(claim_id);
CREATE INDEX IF NOT EXISTS idx_claim_attachments_type  ON claim_attachments(attachment_type);
CREATE INDEX IF NOT EXISTS idx_claim_attachments_date  ON claim_attachments(claim_id, created_at DESC);

-- ----------------------------------------------------------
-- SECTION 3: Claim status change history (immutable)
-- ----------------------------------------------------------
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

-- ----------------------------------------------------------
-- SECTION 4: Claim audit log (field-level changes)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS claim_audit_logs (
    id                          BIGSERIAL PRIMARY KEY,
    claim_id                    BIGINT NOT NULL,
    change_type                 VARCHAR(50) NOT NULL,
    previous_status             VARCHAR(30),
    new_status                  VARCHAR(30),
    previous_requested_amount   NUMERIC(15,2),
    new_requested_amount        NUMERIC(15,2),
    previous_approved_amount    NUMERIC(15,2),
    new_approved_amount         NUMERIC(15,2),
    actor_user_id               BIGINT NOT NULL,
    actor_username              VARCHAR(100) NOT NULL,
    actor_role                  VARCHAR(50)  NOT NULL,
    timestamp                   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    comment                     TEXT,
    ip_address                  VARCHAR(45),
    before_snapshot             TEXT,
    after_snapshot              TEXT
);

CREATE INDEX IF NOT EXISTS idx_claim_audit_claim_id  ON claim_audit_logs(claim_id);
CREATE INDEX IF NOT EXISTS idx_claim_audit_timestamp ON claim_audit_logs(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_claim_audit_actor     ON claim_audit_logs(actor_user_id);
