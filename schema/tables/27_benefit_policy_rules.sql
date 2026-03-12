-- ============================================================
-- Table: benefit_policy_rules
-- Depends on: benefit_policies, medical_services
-- ============================================================
CREATE TABLE IF NOT EXISTS benefit_policy_rules (
    id                       BIGSERIAL PRIMARY KEY,
    benefit_policy_id        BIGINT NOT NULL,

    service_category         VARCHAR(100),
    medical_category_id      BIGINT,
    medical_service_id       BIGINT,

    coverage_percentage      NUMERIC(5,2),
    coverage_percent         INTEGER,
    max_sessions_per_year    INTEGER,
    times_limit              INTEGER,
    requires_preauth         BOOLEAN DEFAULT false,
    requires_pre_approval    BOOLEAN DEFAULT false,
    waiting_period_days      INTEGER,

    max_amount_per_session   NUMERIC(10,2),
    max_amount_per_year      NUMERIC(12,2),
    amount_limit             NUMERIC(15,2),

    notes                    VARCHAR(500),
    active                   BOOLEAN DEFAULT true,
    created_at               TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP,
    created_by               VARCHAR(255),

    CONSTRAINT fk_rule_policy  FOREIGN KEY (benefit_policy_id) REFERENCES benefit_policies(id) ON DELETE CASCADE,
    CONSTRAINT fk_rule_service FOREIGN KEY (medical_service_id) REFERENCES medical_services(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_bpr_policy   ON benefit_policy_rules(benefit_policy_id);
CREATE INDEX IF NOT EXISTS idx_bpr_category ON benefit_policy_rules(medical_category_id);
CREATE INDEX IF NOT EXISTS idx_bpr_service  ON benefit_policy_rules(medical_service_id);
CREATE INDEX IF NOT EXISTS idx_bpr_active   ON benefit_policy_rules(active);
