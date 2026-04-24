CREATE TABLE IF NOT EXISTS claim_coverage_rules (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    rule_type VARCHAR(60) NOT NULL,
    priority INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    rule_group VARCHAR(60) NOT NULL,
    dependency_rules JSONB NOT NULL DEFAULT '[]'::jsonb,
    configuration JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_claim_coverage_rules_active_group_priority
    ON claim_coverage_rules (enabled, rule_group, priority);

CREATE TABLE IF NOT EXISTS claim_rule_execution_audit (
    id BIGSERIAL PRIMARY KEY,
    correlation_id VARCHAR(80) NOT NULL,
    claim_id BIGINT NULL,
    rule_id BIGINT NOT NULL,
    rule_name VARCHAR(120) NOT NULL,
    rule_type VARCHAR(60) NOT NULL,
    rule_group VARCHAR(60) NOT NULL,
    decision VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NULL,
    before_context JSONB NOT NULL,
    after_context JSONB NOT NULL,
    delta_changes JSONB NOT NULL,
    execution_time_ms NUMERIC(12,3) NOT NULL,
    executed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_claim_rule_execution_audit_rule
        FOREIGN KEY (rule_id) REFERENCES claim_coverage_rules (id)
);

CREATE INDEX IF NOT EXISTS idx_claim_rule_exec_audit_correlation
    ON claim_rule_execution_audit (correlation_id);

CREATE INDEX IF NOT EXISTS idx_claim_rule_exec_audit_claim_time
    ON claim_rule_execution_audit (claim_id, executed_at DESC);

INSERT INTO claim_coverage_rules (name, rule_type, priority, enabled, rule_group, dependency_rules, configuration)
VALUES
    ('Times Limit Rule', 'TIMES_LIMIT_RULE', 10, TRUE, 'PRE_VALIDATION_RULES', '[]'::jsonb, '{}'::jsonb),
    ('Coverage Percent Rule', 'COVERAGE_PERCENT_RULE', 20, TRUE, 'COVERAGE_CALCULATION_RULES', '[]'::jsonb, '{"roundingMode":"HALF_UP","scale":2}'::jsonb),
    ('Amount Limit Rule', 'AMOUNT_LIMIT_RULE', 30, TRUE, 'LIMIT_ENFORCEMENT_RULES', '["Coverage Percent Rule"]'::jsonb, '{}'::jsonb)
ON CONFLICT (name) DO NOTHING;
