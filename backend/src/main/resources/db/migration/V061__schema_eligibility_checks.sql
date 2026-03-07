-- ============================================================
-- V061: Eligibility checks (verification audit trail)
-- ============================================================
-- Depends on: V050 (members), V040 (benefit_policies)

CREATE TABLE IF NOT EXISTS eligibility_checks (
    id          BIGSERIAL PRIMARY KEY,
    member_id   BIGINT NOT NULL,

    -- Legacy columns (V4)
    check_date      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_eligible     BOOLEAN NOT NULL,
    eligibility_reason TEXT,
    policy_id       BIGINT,
    coverage_status VARCHAR(50),
    visit_id        BIGINT,
    checked_by      VARCHAR(255),

    -- Runtime columns (V24 — unique request_id pattern)
    request_id          VARCHAR(36) NOT NULL UNIQUE,
    check_timestamp     TIMESTAMP   NOT NULL,
    provider_id         BIGINT,
    service_date        DATE NOT NULL,
    service_code        VARCHAR(50),
    eligible            BOOLEAN NOT NULL,
    status              VARCHAR(50) NOT NULL,
    reasons             TEXT,
    member_name         VARCHAR(255),
    member_civil_id     VARCHAR(50),
    member_status       VARCHAR(30),
    policy_number       VARCHAR(100),
    policy_status       VARCHAR(30),
    policy_start_date   DATE,
    policy_end_date     DATE,
    employer_id         BIGINT,
    employer_name       VARCHAR(255),
    checked_by_user_id  BIGINT,
    checked_by_username VARCHAR(100),
    company_scope_id    BIGINT,
    ip_address          VARCHAR(45),
    user_agent          VARCHAR(500),
    processing_time_ms  INTEGER,
    rules_evaluated     INTEGER,
    created_at          TIMESTAMP NOT NULL,

    CONSTRAINT fk_eligibility_member FOREIGN KEY (member_id)  REFERENCES members(id)         ON DELETE RESTRICT,
    CONSTRAINT fk_eligibility_policy FOREIGN KEY (policy_id)  REFERENCES benefit_policies(id) ON DELETE RESTRICT,
    CONSTRAINT uk_eligibility_request_id UNIQUE (request_id)
);

CREATE INDEX IF NOT EXISTS idx_eligibility_member        ON eligibility_checks(member_id);
CREATE INDEX IF NOT EXISTS idx_eligibility_date          ON eligibility_checks(check_date DESC);
CREATE INDEX IF NOT EXISTS idx_eligibility_status        ON eligibility_checks(status);
CREATE INDEX IF NOT EXISTS idx_eligibility_request_id    ON eligibility_checks(request_id);
CREATE INDEX IF NOT EXISTS idx_eligibility_policy_id     ON eligibility_checks(policy_id);
CREATE INDEX IF NOT EXISTS idx_eligibility_member_date   ON eligibility_checks(member_id, check_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_eligibility_service_date  ON eligibility_checks(service_date DESC);
CREATE INDEX IF NOT EXISTS idx_eligibility_scope         ON eligibility_checks(company_scope_id);
