-- Migration to add tables for BenefitPolicy Excluded Categories and Coverage Simulation Run

-- 1. Create table for BenefitPolicy excludedCategoryCodes
CREATE TABLE IF NOT EXISTS benefit_policy_excluded_categories (
    benefit_policy_id BIGINT NOT NULL,
    category_code VARCHAR(50) NOT NULL,
    CONSTRAINT fk_bp_excluded_cat FOREIGN KEY (benefit_policy_id) REFERENCES benefit_policies (id) ON DELETE CASCADE
);

-- 2. Create coverage_simulation_runs
CREATE TABLE IF NOT EXISTS coverage_simulation_runs (
    id VARCHAR(36) PRIMARY KEY,
    contract_id BIGINT,
    policy_id BIGINT NOT NULL,
    effective_date DATE,
    encounter_type VARCHAR(50),
    generated_by_user_id BIGINT,
    generated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    limit_evaluation_mode VARCHAR(50),
    summary_json JSONB,
    total_services INT DEFAULT 0,
    covered_count INT DEFAULT 0,
    excluded_count INT DEFAULT 0,
    no_rule_count INT DEFAULT 0,
    needs_review_count INT DEFAULT 0,
    invalid_category_count INT DEFAULT 0,
    context_mismatch_count INT DEFAULT 0,
    zero_price_count INT DEFAULT 0
);

-- 3. Create coverage_simulation_items
CREATE TABLE IF NOT EXISTS coverage_simulation_items (
    id BIGSERIAL PRIMARY KEY,
    simulation_run_id VARCHAR(36) NOT NULL,
    provider_service_id BIGINT,
    service_name VARCHAR(255),
    service_code VARCHAR(50),
    price NUMERIC(15, 2),
    source_main_category VARCHAR(255),
    source_sub_category VARCHAR(255),
    category_code VARCHAR(50),
    category_name VARCHAR(255),
    coverage_status VARCHAR(50),
    coverage_reason VARCHAR(500),
    recommended_action VARCHAR(500),
    severity VARCHAR(50),
    matched_rule_id BIGINT,
    coverage_percent INT,
    patient_share NUMERIC(15, 2),
    company_share NUMERIC(15, 2),
    requires_review BOOLEAN DEFAULT FALSE,
    requires_pre_approval BOOLEAN DEFAULT FALSE,
    warnings_json JSONB,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sim_item_run FOREIGN KEY (simulation_run_id) REFERENCES coverage_simulation_runs (id) ON DELETE CASCADE
);

CREATE INDEX idx_cov_sim_run_policy ON coverage_simulation_runs(policy_id);
CREATE INDEX idx_cov_sim_item_run ON coverage_simulation_items(simulation_run_id);
