-- ============================================================
-- V060: Patient visits and attachments
-- ============================================================
-- Depends on: V050 (members), V005 (employers), V006 (providers),
--             V020 (medical_categories), V021 (medical_services)

-- ----------------------------------------------------------
-- SECTION 1: Visits (central patient encounter record)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS visits (
    id          BIGSERIAL PRIMARY KEY,
    member_id   BIGINT NOT NULL,
    employer_id BIGINT,
    provider_id BIGINT,

    -- Medical category / service
    medical_category_id   BIGINT,
    medical_category_name VARCHAR(200),
    medical_service_id    BIGINT,
    medical_service_code  VARCHAR(50),
    medical_service_name  VARCHAR(200),

    -- Visit details
    doctor_name      VARCHAR(255),
    specialty        VARCHAR(100),
    visit_date       DATE NOT NULL,
    diagnosis        TEXT,
    treatment        TEXT,
    total_amount     NUMERIC(10,2),
    notes            TEXT,

    -- Visit type and status
    visit_type VARCHAR(30) DEFAULT 'OUTPATIENT'
        CHECK (visit_type IN (
            'EMERGENCY','INPATIENT','OUTPATIENT','ROUTINE','FOLLOW_UP',
            'PREVENTIVE','SPECIALIZED','HOME_CARE','TELECONSULTATION',
            'DAY_SURGERY','LEGACY_BACKLOG'
        )),
    status VARCHAR(30) DEFAULT 'REGISTERED'
        CHECK (status IN (
            'REGISTERED','IN_PROGRESS','PENDING_PREAUTH',
            'CLAIM_SUBMITTED','COMPLETED','CANCELLED'
        )),

    -- Eligibility check that produced this visit
    eligibility_check_id BIGINT,

    -- Optimistic locking
    version BIGINT DEFAULT 0,

    -- Audit
    active      BOOLEAN DEFAULT true,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_visit_member   FOREIGN KEY (member_id)   REFERENCES members(id)   ON DELETE RESTRICT,
    CONSTRAINT fk_visit_employer FOREIGN KEY (employer_id) REFERENCES employers(id) ON DELETE RESTRICT,
    CONSTRAINT chk_visit_date_reasonable CHECK (
        visit_date <= CURRENT_DATE AND visit_date >= CURRENT_DATE - INTERVAL '10 years'
    )
);

CREATE INDEX IF NOT EXISTS idx_visits_member      ON visits(member_id);
CREATE INDEX IF NOT EXISTS idx_visits_employer    ON visits(employer_id);
CREATE INDEX IF NOT EXISTS idx_visits_provider    ON visits(provider_id);
CREATE INDEX IF NOT EXISTS idx_visits_date        ON visits(visit_date DESC);
CREATE INDEX IF NOT EXISTS idx_visits_status      ON visits(status);
CREATE INDEX IF NOT EXISTS idx_visits_member_date ON visits(member_id, visit_date DESC);
CREATE INDEX IF NOT EXISTS idx_visits_provider_date ON visits(provider_id, visit_date DESC);
CREATE INDEX IF NOT EXISTS idx_visits_category    ON visits(medical_category_id) WHERE medical_category_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_visits_service     ON visits(medical_service_id)  WHERE medical_service_id  IS NOT NULL;

-- ----------------------------------------------------------
-- SECTION 2: Visit supporting documents
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS visit_attachments (
    id                  BIGSERIAL PRIMARY KEY,
    visit_id            BIGINT NOT NULL,
    file_name           VARCHAR(500) NOT NULL,
    original_file_name  VARCHAR(500),
    file_key            VARCHAR(500),
    file_type           VARCHAR(100),
    file_size           BIGINT,
    attachment_type     VARCHAR(50)
        CHECK (attachment_type IN ('XRAY','MRI','CT_SCAN','LAB_RESULT','PRESCRIPTION','MEDICAL_REPORT','OTHER')),
    description         TEXT,
    uploaded_by         VARCHAR(100),
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_visit_attachment FOREIGN KEY (visit_id) REFERENCES visits(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_visit_attachments_visit      ON visit_attachments(visit_id);
CREATE INDEX IF NOT EXISTS idx_visit_attachments_type       ON visit_attachments(attachment_type);
CREATE INDEX IF NOT EXISTS idx_visit_attachments_visit_date ON visit_attachments(visit_id, created_at DESC);
