-- ============================================================
-- Table: claim_lines
-- Depends on: claims, medical_services
-- ============================================================
CREATE TABLE IF NOT EXISTS claim_lines (
    id                           BIGSERIAL PRIMARY KEY,
    claim_id                     BIGINT NOT NULL,

    service_code                 VARCHAR(50) NOT NULL,
    service_description          VARCHAR(255),
    quantity                     INTEGER NOT NULL DEFAULT 1,
    unit_price                   NUMERIC(15,2) NOT NULL CHECK (unit_price >= 0),
    total_amount                 NUMERIC(15,2) CHECK (total_amount >= 0),
    total_price                  NUMERIC(15,2) NOT NULL,

    medical_service_id           BIGINT,
    service_name                 VARCHAR(255),
    service_category_id          BIGINT,
    service_category_name        VARCHAR(200),

    requires_pa                  BOOLEAN NOT NULL DEFAULT false,

    line_number                  INTEGER,
    approved_amount              NUMERIC(15,2),
    approved_units               INTEGER,
    approval_notes               TEXT,

    coverage_percent_snapshot     INTEGER,
    patient_copay_percent_snapshot INTEGER,
    times_limit_snapshot          INTEGER,
    amount_limit_snapshot         NUMERIC(15,2),

    refused_amount               NUMERIC(15,2) DEFAULT 0,

    version                      BIGINT NOT NULL DEFAULT 0,
    rejection_reason             VARCHAR(500),
    rejection_reason_code        VARCHAR(50),
    reviewer_notes               TEXT,
    rejected                     BOOLEAN DEFAULT false,
    requested_unit_price         NUMERIC(15,2),
    approved_unit_price          NUMERIC(15,2),
    requested_quantity           INTEGER,
    approved_quantity            INTEGER,

    applied_category_id          BIGINT,
    applied_category_name        VARCHAR(200),

    pricing_item_id              BIGINT,
    benefit_limit                NUMERIC(15,2),
    used_amount_snapshot         NUMERIC(15,2),
    remaining_amount_snapshot    NUMERIC(15,2),

    created_at                   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by                   VARCHAR(255),

    CONSTRAINT fk_claim_line_claim   FOREIGN KEY (claim_id)          REFERENCES claims(id)           ON DELETE CASCADE,
    CONSTRAINT fk_claim_line_service FOREIGN KEY (medical_service_id) REFERENCES medical_services(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_claim_line_claim        ON claim_lines(claim_id);
CREATE INDEX IF NOT EXISTS idx_claim_line_service      ON claim_lines(medical_service_id);
CREATE INDEX IF NOT EXISTS idx_claim_line_service_analysis ON claim_lines(medical_service_id, total_price);
