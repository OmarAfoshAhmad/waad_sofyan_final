-- ============================================================
-- Table: visits
-- Depends on: members, employers
-- ============================================================
CREATE TABLE IF NOT EXISTS visits (
    id                    BIGSERIAL PRIMARY KEY,
    member_id             BIGINT NOT NULL,
    employer_id           BIGINT,
    provider_id           BIGINT,

    medical_category_id   BIGINT,
    medical_category_name VARCHAR(200),
    medical_service_id    BIGINT,
    medical_service_code  VARCHAR(50),
    medical_service_name  VARCHAR(200),

    doctor_name           VARCHAR(255),
    specialty             VARCHAR(100),
    visit_date            DATE NOT NULL,
    diagnosis             TEXT,
    treatment             TEXT,
    total_amount          NUMERIC(10,2),
    notes                 TEXT,

    visit_type            VARCHAR(30) DEFAULT 'OUTPATIENT'
        CHECK (visit_type IN (
            'EMERGENCY','INPATIENT','OUTPATIENT','ROUTINE','FOLLOW_UP',
            'PREVENTIVE','SPECIALIZED','HOME_CARE','TELECONSULTATION',
            'DAY_SURGERY','LEGACY_BACKLOG'
        )),
    status                VARCHAR(30) DEFAULT 'REGISTERED'
        CHECK (status IN (
            'REGISTERED','IN_PROGRESS','PENDING_PREAUTH',
            'CLAIM_SUBMITTED','COMPLETED','CANCELLED'
        )),

    eligibility_check_id  BIGINT,

    version               BIGINT DEFAULT 0,

    active                BOOLEAN DEFAULT true,
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

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
CREATE INDEX IF NOT EXISTS idx_visits_service     ON visits(medical_service_id)  WHERE medical_service_id IS NOT NULL;
