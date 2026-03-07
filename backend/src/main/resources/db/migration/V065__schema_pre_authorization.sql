-- ============================================================
-- V065: Pre-authorizations
-- ============================================================
-- Depends on: V006 (providers), V050 (members),
--             V021 (medical_services), V060 (visits)

-- ----------------------------------------------------------
-- SECTION 1: Pre-authorization requests (original model)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS preauthorization_requests (
    id              BIGSERIAL PRIMARY KEY,
    request_number  VARCHAR(100),
    provider_id     BIGINT NOT NULL,
    member_id       BIGINT NOT NULL,

    -- Service details
    service_date            DATE,
    requested_service_date  DATE,
    diagnosis_code          VARCHAR(50),
    diagnosis_description   TEXT,

    -- Financial
    requested_amount NUMERIC(15,2),
    approved_amount  NUMERIC(15,2),

    -- Status machine
    status VARCHAR(50)
        CHECK (status IN ('PENDING','APPROVED','REJECTED','EXPIRED','CANCELLED')),

    -- Validity window
    valid_from  TIMESTAMP,
    valid_until TIMESTAMP,

    -- Audit
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP,
    approved_at TIMESTAMP,
    created_by  VARCHAR(255),
    approved_by VARCHAR(255),

    CONSTRAINT fk_pauthreq_provider FOREIGN KEY (provider_id) REFERENCES providers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pauthreq_member   FOREIGN KEY (member_id)   REFERENCES members(id)   ON DELETE RESTRICT
);

-- Sequences for this table
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_class WHERE relname = 'preauth_seq') THEN
        CREATE SEQUENCE preauth_seq START WITH 1 INCREMENT BY 50;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_pauthreq_member_status_date ON preauthorization_requests(member_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pauthreq_expiring           ON preauthorization_requests(valid_until)
    WHERE status = 'APPROVED' AND valid_until IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pauthreq_provider_date      ON preauthorization_requests(provider_id, created_at DESC, status);

-- ----------------------------------------------------------
-- SECTION 2: Pre-authorizations (runtime compatibility table)
-- ----------------------------------------------------------
-- This table satisfies JPQL queries that join on pre_authorizations.
-- It mirrors the full shape of the pre-authorization JPA entity.
CREATE TABLE IF NOT EXISTS pre_authorizations (
    id                              BIGSERIAL PRIMARY KEY,
    active                          BOOLEAN DEFAULT true,
    approved_amount                 NUMERIC(15,2),
    approved_at                     TIMESTAMP,
    approved_by                     VARCHAR(255),
    contract_price                  NUMERIC(15,2),
    copay_amount                    NUMERIC(15,2),
    copay_percentage                NUMERIC(10,2),
    coverage_percent_snapshot       INTEGER,
    created_at                      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by                      VARCHAR(255),
    currency                        VARCHAR(20),
    diagnosis_code                  VARCHAR(100),
    diagnosis_description           TEXT,
    expected_service_date           DATE,
    expiry_date                     DATE,
    insurance_covered_amount        NUMERIC(15,2),
    medical_service_id              BIGINT,
    member_id                       BIGINT,
    notes                           TEXT,
    patient_copay_percent_snapshot  INTEGER,
    pre_auth_number                 VARCHAR(100),
    priority                        VARCHAR(50),
    provider_id                     BIGINT,
    reference_number                VARCHAR(100),
    rejection_reason                TEXT,
    request_date                    TIMESTAMP,
    requires_pa                     BOOLEAN,
    reserved_amount                 NUMERIC(15,2),
    service_category_id             BIGINT,
    service_category_name           VARCHAR(255),
    service_code                    VARCHAR(100),
    service_name                    VARCHAR(255),
    service_type                    VARCHAR(100),
    status                          VARCHAR(50),
    updated_at                      TIMESTAMP,
    updated_by                      VARCHAR(255),
    version                         BIGINT,
    visit_id                        BIGINT
);

CREATE INDEX IF NOT EXISTS idx_preauth_member_id   ON pre_authorizations(member_id);
CREATE INDEX IF NOT EXISTS idx_preauth_provider_id ON pre_authorizations(provider_id);
CREATE INDEX IF NOT EXISTS idx_preauth_status      ON pre_authorizations(status);

-- ----------------------------------------------------------
-- SECTION 3: Pre-authorization supporting documents
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS pre_authorization_attachments (
    id                          BIGSERIAL PRIMARY KEY,
    preauthorization_request_id BIGINT NOT NULL,
    file_name                   VARCHAR(500) NOT NULL,
    file_path                   VARCHAR(500),
    file_type                   VARCHAR(100),
    file_size                   BIGINT,
    attachment_type             VARCHAR(50),
    uploaded_by                 VARCHAR(255),
    uploaded_at                 TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_preauth_att FOREIGN KEY (preauthorization_request_id)
        REFERENCES preauthorization_requests(id) ON DELETE CASCADE
);

-- ----------------------------------------------------------
-- SECTION 4: Pre-authorization audit trail
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS pre_authorization_audit (
    id                    BIGSERIAL PRIMARY KEY,
    pre_authorization_id  BIGINT NOT NULL,
    reference_number      VARCHAR(50),
    changed_by            VARCHAR(100) NOT NULL,
    change_date           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    action                VARCHAR(20) NOT NULL,
    field_name            VARCHAR(50),
    old_value             TEXT,
    new_value             TEXT,
    notes                 VARCHAR(500),
    ip_address            VARCHAR(45)
);

CREATE INDEX IF NOT EXISTS idx_preauth_audit_id     ON pre_authorization_audit(pre_authorization_id);
CREATE INDEX IF NOT EXISTS idx_preauth_audit_user   ON pre_authorization_audit(changed_by);
CREATE INDEX IF NOT EXISTS idx_preauth_audit_date   ON pre_authorization_audit(change_date DESC);
CREATE INDEX IF NOT EXISTS idx_preauth_audit_action ON pre_authorization_audit(action);
