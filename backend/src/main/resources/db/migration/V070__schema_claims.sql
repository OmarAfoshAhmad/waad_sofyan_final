-- ============================================================
-- V070: Claims (insurance claim records)
-- ============================================================
-- Depends on: V050 (members), V006 (providers),
--             V065 (preauthorization_requests)

CREATE TABLE IF NOT EXISTS claims (
    id              BIGINT PRIMARY KEY DEFAULT nextval('claim_seq'),
    claim_number    VARCHAR(100) NOT NULL UNIQUE,
    external_claim_ref VARCHAR(100),

    -- Member + provider
    member_id       BIGINT NOT NULL,
    provider_id     BIGINT NOT NULL,
    provider_name   VARCHAR(255),

    -- Visit link
    visit_id        BIGINT,

    -- Service information
    service_date        DATE NOT NULL,
    diagnosis_code      VARCHAR(50),
    diagnosis_description TEXT,

    -- Financial amounts (NUMERIC(15,2) precision)
    requested_amount    NUMERIC(15,2) NOT NULL CHECK (requested_amount >= 0),
    approved_amount     NUMERIC(15,2),
    paid_amount         NUMERIC(15,2),
    patient_share       NUMERIC(15,2),
    refused_amount      DECIMAL(15,2) DEFAULT 0,
    difference_amount   NUMERIC(15,2),
    patient_copay       NUMERIC(15,2),
    net_provider_amount NUMERIC(15,2),
    copay_percent       NUMERIC(5,2),
    deductible_applied  NUMERIC(15,2),

    -- Status workflow
    status VARCHAR(50) NOT NULL
        CHECK (status IN (
            'APPROVED','BATCHED','SETTLED','REJECTED','SUBMITTED',
            'UNDER_REVIEW','RETURNED_FOR_INFO','PENDING_APPROVAL','BACKLOG_IMPORT'
        )),
    submitted_at TIMESTAMP,

    -- Review and approval
    reviewer_id       BIGINT,
    reviewed_at       TIMESTAMP,
    approval_reason   TEXT,
    reviewer_comment  TEXT,
    doctor_name       VARCHAR(255),

    -- Pre-authorization link
    pre_authorization_id BIGINT,

    -- Payment tracking
    payment_reference   VARCHAR(100),
    settled_at          TIMESTAMP,
    settlement_notes    TEXT,

    -- SLA tracking
    expected_completion_date DATE,
    actual_completion_date   DATE,
    within_sla               BOOLEAN,
    business_days_taken      INTEGER,
    sla_days_configured      INTEGER,

    -- Aggregates (de-normalised for performance)
    service_count       INTEGER,
    attachments_count   INTEGER,

    -- Backlog import flag
    is_backlog BOOLEAN DEFAULT FALSE,

    -- Audit
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),

    CONSTRAINT fk_claim_member   FOREIGN KEY (member_id)              REFERENCES members(id)                 ON DELETE RESTRICT,
    CONSTRAINT fk_claim_provider FOREIGN KEY (provider_id)            REFERENCES providers(id)               ON DELETE RESTRICT,
    CONSTRAINT fk_claim_preauth  FOREIGN KEY (pre_authorization_id)   REFERENCES preauthorization_requests(id) ON DELETE RESTRICT,
    CONSTRAINT chk_claim_date    CHECK (service_date <= CURRENT_DATE AND service_date >= CURRENT_DATE - INTERVAL '10 years')
);

CREATE INDEX IF NOT EXISTS idx_claims_member_date_status     ON claims(member_id, service_date DESC, status);
CREATE INDEX IF NOT EXISTS idx_claims_provider_status        ON claims(provider_id, status, approved_amount);
CREATE INDEX IF NOT EXISTS idx_claims_reviewer               ON claims(reviewer_id, status, service_date DESC);
CREATE INDEX IF NOT EXISTS idx_claims_unassigned             ON claims(status, service_date DESC) WHERE reviewer_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_claims_provider_date          ON claims(provider_id, service_date DESC, status);
CREATE INDEX IF NOT EXISTS idx_claims_pre_auth               ON claims(pre_authorization_id);
CREATE INDEX IF NOT EXISTS idx_claims_monthly_reporting      ON claims(status, service_date, approved_amount);
CREATE INDEX IF NOT EXISTS idx_claims_sla                    ON claims(within_sla, actual_completion_date);
CREATE INDEX IF NOT EXISTS idx_claims_pending_review         ON claims(status, created_at DESC) WHERE status = 'SUBMITTED';
CREATE INDEX IF NOT EXISTS idx_claims_approval_metrics       ON claims(status, reviewed_at, approved_amount);
