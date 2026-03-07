-- ============================================================
-- V040: Benefit policies and coverage rules
-- ============================================================
-- Depends on: V005 (employers), V021 (medical_services)

-- ----------------------------------------------------------
-- SECTION 1: Benefit policies (insurance plans)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS benefit_policies (
    id          BIGINT PRIMARY KEY DEFAULT nextval('benefit_policy_seq'),
    policy_name VARCHAR(255) NOT NULL,
    policy_code VARCHAR(50)  NOT NULL UNIQUE,
    employer_id BIGINT NOT NULL,

    -- Runtime alias columns
    name        VARCHAR(255),

    -- Coverage financial limits
    annual_limit         NUMERIC(12,2),
    per_visit_limit      NUMERIC(10,2),
    deductible_amount    NUMERIC(10,2),
    copay_percentage     NUMERIC(5,2),
    annual_deductible    DECIMAL(15,2) DEFAULT 0.00,
    out_of_pocket_max    DECIMAL(15,2) DEFAULT 0.00,

    -- Per-member / per-family limits
    per_member_limit     NUMERIC(15,2),
    per_family_limit     NUMERIC(15,2),

    -- Policy metadata
    policy_type VARCHAR(50) CHECK (policy_type IN ('BASIC','PREMIUM','EXECUTIVE','CUSTOM')),
    description TEXT,
    notes       VARCHAR(1000),

    -- Status and dates
    status          VARCHAR(20) DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','ACTIVE')),
    start_date      DATE,
    end_date        DATE,
    effective_date  DATE NOT NULL,
    expiry_date     DATE,

    -- Coverage defaults
    default_coverage_percent     INTEGER DEFAULT 80,
    default_waiting_period_days  INTEGER DEFAULT 0,
    covered_members_count        INTEGER DEFAULT 0,

    -- Audit
    active      BOOLEAN DEFAULT true,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),

    CONSTRAINT fk_policy_employer FOREIGN KEY (employer_id) REFERENCES employers(id) ON DELETE RESTRICT,
    CONSTRAINT chk_policy_dates CHECK (expiry_date IS NULL OR expiry_date >= effective_date)
);

CREATE INDEX IF NOT EXISTS idx_policies_code       ON benefit_policies(policy_code);
CREATE INDEX IF NOT EXISTS idx_policies_employer   ON benefit_policies(employer_id);
CREATE INDEX IF NOT EXISTS idx_policies_type       ON benefit_policies(policy_type);
CREATE INDEX IF NOT EXISTS idx_policies_active     ON benefit_policies(active);
CREATE INDEX IF NOT EXISTS idx_policies_start_date ON benefit_policies(start_date);
CREATE INDEX IF NOT EXISTS idx_policies_end_date   ON benefit_policies(end_date);

-- ----------------------------------------------------------
-- SECTION 2: Benefit policy rules (per-service coverage details)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS benefit_policy_rules (
    id          BIGSERIAL PRIMARY KEY,
    policy_id   BIGINT NOT NULL,

    -- Service targeting
    service_category        VARCHAR(100),
    medical_category_id     BIGINT,
    medical_service_id      BIGINT,

    -- Coverage rules
    coverage_percentage         NUMERIC(5,2),
    coverage_percent            INTEGER,            -- runtime alias
    max_sessions_per_year       INTEGER,
    times_limit                 INTEGER,            -- runtime alias
    requires_preauth            BOOLEAN DEFAULT false,
    requires_pre_approval       BOOLEAN DEFAULT false,  -- runtime alias
    waiting_period_days         INTEGER,

    -- Limits
    max_amount_per_session  NUMERIC(10,2),
    max_amount_per_year     NUMERIC(12,2),
    amount_limit            NUMERIC(15,2),          -- runtime alias

    notes       VARCHAR(500),
    active      BOOLEAN DEFAULT true,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  VARCHAR(255),

    CONSTRAINT fk_rule_policy  FOREIGN KEY (policy_id)          REFERENCES benefit_policies(id)  ON DELETE CASCADE,
    CONSTRAINT fk_rule_service FOREIGN KEY (medical_service_id) REFERENCES medical_services(id)  ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_bpr_policy   ON benefit_policy_rules(policy_id);
CREATE INDEX IF NOT EXISTS idx_bpr_category ON benefit_policy_rules(medical_category_id);
CREATE INDEX IF NOT EXISTS idx_bpr_service  ON benefit_policy_rules(medical_service_id);
CREATE INDEX IF NOT EXISTS idx_bpr_active   ON benefit_policy_rules(active);
