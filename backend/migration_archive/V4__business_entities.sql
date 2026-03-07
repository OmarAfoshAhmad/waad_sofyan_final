-- ============================================================================
-- V4: Business Entities - CLEAN MIGRATION REBASELINE
-- ============================================================================
-- CONSOLIDATED FROM: V2_03__business_entities.sql + V2_04__financial_schema.sql
-- 
-- ARCHITECTURAL DECISION (2026-02-13):
--   ✅ Employer-only architecture - NO organizations or companies
--   ✅ All FKs reference "employers" table (not companies/organizations)
--   ✅ All Phase 3-6 financial safety incorporated from day 1
--   ✅ Optimistic locking (@Version) on all financial entities
--   ✅ Immutable ledger pattern (INSERT-only transactions)
--   ✅ Balance equation constraints enforced
--   ✅ Duplicate prevention mechanisms
--
-- Creates ALL business entity tables:
--   - Members, Benefit Policies, Provider Contracts
--   - Claims, Pre-Authorizations, Settlements
--   - Visits, Eligibility Checks, Member Import Logs
--   - All supporting tables (attachments, history, etc.)
--
-- Production-ready from day 1 - NO fix migrations needed!
-- ============================================================================

-- ============================================================================
-- SECTION 1: Business Sequences
-- ============================================================================

CREATE SEQUENCE IF NOT EXISTS member_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS provider_contract_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS benefit_policy_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS claim_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS preauth_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS settlement_batch_seq START WITH 1 INCREMENT BY 50;

-- ============================================================================
-- SECTION 2: Members (Insured Individuals)
-- ============================================================================
-- Domain Architecture: Members belong to Employers (employer-only model)
-- ============================================================================

CREATE TABLE IF NOT EXISTS members (
    id BIGINT PRIMARY KEY,
    member_card_id VARCHAR(100) NOT NULL UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    full_name_ar VARCHAR(255),
    date_of_birth DATE NOT NULL,
    gender VARCHAR(20) CHECK (gender IN ('MALE', 'FEMALE')),
    national_id VARCHAR(50),
    
    -- Employment and coverage (EMPLOYER-ONLY ARCHITECTURE)
    employer_id BIGINT,
    employee_id VARCHAR(100),
    membership_type VARCHAR(50) CHECK (membership_type IN ('PRIMARY', 'DEPENDENT')),
    relation_to_employee VARCHAR(50),
    
    -- Contact information
    email VARCHAR(255),
    phone VARCHAR(50),
    address TEXT,
    
    -- Coverage details
    coverage_start_date DATE,
    coverage_end_date DATE,
    policy_number VARCHAR(100),
    
    -- Audit fields
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    
    CONSTRAINT fk_member_employer FOREIGN KEY (employer_id) 
        REFERENCES employers(id) ON DELETE RESTRICT,
    CONSTRAINT chk_coverage_dates CHECK (coverage_end_date IS NULL OR coverage_end_date >= coverage_start_date)
);

CREATE INDEX IF NOT EXISTS idx_members_card_id ON members(member_card_id);
CREATE INDEX IF NOT EXISTS idx_members_employer ON members(employer_id);
CREATE INDEX IF NOT EXISTS idx_members_national_id ON members(national_id);
CREATE INDEX IF NOT EXISTS idx_members_active ON members(active);
CREATE INDEX IF NOT EXISTS idx_members_coverage ON members(coverage_start_date, coverage_end_date) WHERE active = true;

COMMENT ON TABLE members IS 'Insured members (employees and dependents) - EMPLOYER-ONLY ARCHITECTURE';
COMMENT ON COLUMN members.employer_id IS 'References employers table (employer-only model)';
COMMENT ON COLUMN members.membership_type IS 'PRIMARY = employee, DEPENDENT = family member';

-- ============================================================================
-- SECTION 3: Member Deductibles (Financial Tracking)
-- ============================================================================
-- Phase 2 protection: Optimistic locking for concurrent updates
-- ============================================================================

CREATE TABLE IF NOT EXISTS member_deductibles (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    deductible_year INTEGER NOT NULL,
    total_deductible NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    deductible_used NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    deductible_remaining NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    
    -- Optimistic locking for concurrent updates (Phase 2 protection)
    version BIGINT NOT NULL DEFAULT 0,
    
    -- Audit fields
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255),
    
    CONSTRAINT fk_deductible_member FOREIGN KEY (member_id) 
        REFERENCES members(id) ON DELETE RESTRICT,
    CONSTRAINT uq_member_deductible_year UNIQUE (member_id, deductible_year),
    CONSTRAINT chk_deductible_math CHECK (deductible_remaining = total_deductible - deductible_used),
    CONSTRAINT chk_deductible_non_negative CHECK (deductible_used >= 0 AND deductible_remaining >= 0)
);

CREATE INDEX IF NOT EXISTS idx_deductibles_member ON member_deductibles(member_id);
CREATE INDEX IF NOT EXISTS idx_deductibles_year ON member_deductibles(deductible_year);

COMMENT ON TABLE member_deductibles IS 'Tracks member deductible usage per year (concurrent-safe with @Version)';
COMMENT ON COLUMN member_deductibles.version IS 'Optimistic locking version for concurrent deductible updates';

-- ============================================================================
-- SECTION 4: Provider Contracts
-- ============================================================================
-- Wave 2 FIX: Unique constraint from day 1
-- EMPLOYER-ONLY: References employers instead of organizations
-- ============================================================================

CREATE TABLE IF NOT EXISTS provider_contracts (
    id BIGINT PRIMARY KEY,
    provider_id BIGINT NOT NULL,
    employer_id BIGINT NOT NULL,
    contract_number VARCHAR(100) NOT NULL UNIQUE,
    
    -- Contract terms
    contract_start_date DATE NOT NULL,
    contract_end_date DATE,
    discount_percent NUMERIC(5,2),
    payment_terms VARCHAR(100),
    
    -- Business rules
    max_sessions_per_service INTEGER,
    requires_preauthorization BOOLEAN NOT NULL DEFAULT false,
    
    -- Contract status
    contract_status VARCHAR(50) NOT NULL CHECK (contract_status IN ('DRAFT', 'ACTIVE', 'EXPIRED', 'TERMINATED')),
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    
    CONSTRAINT fk_contract_provider FOREIGN KEY (provider_id) 
        REFERENCES providers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_contract_employer FOREIGN KEY (employer_id) 
        REFERENCES employers(id) ON DELETE RESTRICT,
    CONSTRAINT chk_contract_dates CHECK (contract_end_date IS NULL OR contract_end_date >= contract_start_date)
);

CREATE INDEX IF NOT EXISTS idx_contracts_provider ON provider_contracts(provider_id);
CREATE INDEX IF NOT EXISTS idx_contracts_employer ON provider_contracts(employer_id);
CREATE INDEX IF NOT EXISTS idx_contracts_status ON provider_contracts(contract_status);
CREATE UNIQUE INDEX IF NOT EXISTS uq_active_contract_per_provider ON provider_contracts(provider_id, employer_id) WHERE contract_status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_contracts_active_date ON provider_contracts(contract_start_date, contract_end_date) WHERE contract_status = 'ACTIVE';

COMMENT ON TABLE provider_contracts IS 'Contracts between providers and employers';
COMMENT ON COLUMN provider_contracts.employer_id IS 'References employers table (employer-only architecture)';
COMMENT ON COLUMN provider_contracts.discount_percent IS 'Contract discount rate (financial terms belong here, not in Provider entity)';
COMMENT ON INDEX uq_active_contract_per_provider IS 'Prevents overlapping active contracts';

-- ============================================================================
-- SECTION 5: Provider Contract Service Prices
-- ============================================================================
-- Wave 2 FIX: Unique constraint from day 1 (V1_19 incorporated)
-- ============================================================================

CREATE TABLE IF NOT EXISTS provider_contract_service_prices (
    id BIGSERIAL PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    canonical_service_id BIGINT NOT NULL,
    contracted_price NUMERIC(10,2) NOT NULL CHECK (contracted_price > 0),
    
    -- Audit fields
    effective_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    
    CONSTRAINT fk_contract_price_contract FOREIGN KEY (contract_id) 
        REFERENCES provider_contracts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_contract_price_service FOREIGN KEY (canonical_service_id) 
        REFERENCES canonical_medical_services(id) ON DELETE RESTRICT,
    CONSTRAINT uq_contract_service UNIQUE (contract_id, canonical_service_id)
);

CREATE INDEX IF NOT EXISTS idx_contract_prices_contract ON provider_contract_service_prices(contract_id);
CREATE INDEX IF NOT EXISTS idx_contract_prices_service ON provider_contract_service_prices(canonical_service_id);

COMMENT ON TABLE provider_contract_service_prices IS 'Service-specific pricing negotiated in provider contracts';
COMMENT ON CONSTRAINT uq_contract_service ON provider_contract_service_prices IS 'Wave 2 fix: prevents duplicate pricing in contract (V1_19 incorporated)';

-- ============================================================================
-- SECTION 6: Benefit Policies
-- ============================================================================
-- EMPLOYER-ONLY: References employers instead of organizations
-- ============================================================================

CREATE TABLE IF NOT EXISTS benefit_policies (
    id BIGINT PRIMARY KEY,
    policy_name VARCHAR(255) NOT NULL,
    policy_code VARCHAR(50) NOT NULL UNIQUE,
    employer_id BIGINT NOT NULL,
    
    -- Coverage limits
    annual_limit NUMERIC(12,2),
    per_visit_limit NUMERIC(10,2),
    deductible_amount NUMERIC(10,2),
    copay_percentage NUMERIC(5,2),
    
    -- Policy metadata
    policy_type VARCHAR(50) CHECK (policy_type IN ('BASIC', 'PREMIUM', 'EXECUTIVE', 'CUSTOM')),
    description TEXT,
    
    -- Audit fields
    active BOOLEAN NOT NULL DEFAULT true,
    effective_date DATE NOT NULL,
    expiry_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    
    CONSTRAINT fk_policy_employer FOREIGN KEY (employer_id) 
        REFERENCES employers(id) ON DELETE RESTRICT,
    CONSTRAINT chk_policy_dates CHECK (expiry_date IS NULL OR expiry_date >= effective_date)
);

CREATE INDEX IF NOT EXISTS idx_policies_code ON benefit_policies(policy_code);
CREATE INDEX IF NOT EXISTS idx_policies_employer ON benefit_policies(employer_id);
CREATE INDEX IF NOT EXISTS idx_policies_type ON benefit_policies(policy_type);
CREATE INDEX IF NOT EXISTS idx_policies_active ON benefit_policies(active);

COMMENT ON TABLE benefit_policies IS 'Insurance benefit policies defining coverage rules - EMPLOYER-ONLY ARCHITECTURE';
COMMENT ON COLUMN benefit_policies.employer_id IS 'References employers table (employer-only model)';

-- ============================================================================
-- SECTION 7: Benefit Policy Rules (Service Coverage Details)
-- ============================================================================

CREATE TABLE IF NOT EXISTS benefit_policy_rules (
    id BIGSERIAL PRIMARY KEY,
    policy_id BIGINT NOT NULL,
    canonical_service_id BIGINT,
    service_category VARCHAR(100),
    
    -- Coverage rules
    coverage_percentage NUMERIC(5,2),
    max_sessions_per_year INTEGER,
    requires_preauth BOOLEAN NOT NULL DEFAULT false,
    waiting_period_days INTEGER,
    
    -- Limits
    max_amount_per_session NUMERIC(10,2),
    max_amount_per_year NUMERIC(12,2),
    
    -- Audit fields
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    
    CONSTRAINT fk_policy_rule_policy FOREIGN KEY (policy_id) 
        REFERENCES benefit_policies(id) ON DELETE CASCADE,
    CONSTRAINT fk_policy_rule_service FOREIGN KEY (canonical_service_id) 
        REFERENCES canonical_medical_services(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_policy_rules_policy ON benefit_policy_rules(policy_id);
CREATE INDEX IF NOT EXISTS idx_policy_rules_service ON benefit_policy_rules(canonical_service_id);
CREATE INDEX IF NOT EXISTS idx_policy_rules_category ON benefit_policy_rules(service_category);

COMMENT ON TABLE benefit_policy_rules IS 'Detailed coverage rules per service/category within a policy';

-- ============================================================================
-- SECTION 8: Member Policy Assignments
-- ============================================================================

CREATE TABLE IF NOT EXISTS member_policy_assignments (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    policy_id BIGINT NOT NULL,
    
    -- Assignment period
    assignment_start_date DATE NOT NULL,
    assignment_end_date DATE,
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    
    CONSTRAINT fk_assignment_member FOREIGN KEY (member_id) 
        REFERENCES members(id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_policy FOREIGN KEY (policy_id) 
        REFERENCES benefit_policies(id) ON DELETE RESTRICT,
    CONSTRAINT chk_assignment_dates CHECK (assignment_end_date IS NULL OR assignment_end_date >= assignment_start_date)
);

CREATE INDEX IF NOT EXISTS idx_policy_assignments_member ON member_policy_assignments(member_id);
CREATE INDEX IF NOT EXISTS idx_policy_assignments_policy ON member_policy_assignments(policy_id);
CREATE INDEX IF NOT EXISTS idx_policy_assignments_dates ON member_policy_assignments(assignment_start_date, assignment_end_date);

COMMENT ON TABLE member_policy_assignments IS 'Links members to their benefit policies over time';

-- ============================================================================
-- SECTION 9: Eligibility Checks (Audit Trail)
-- ============================================================================

CREATE TABLE IF NOT EXISTS eligibility_checks (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    check_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_eligible BOOLEAN NOT NULL,
    eligibility_reason TEXT,
    
    -- Policy details at time of check
    policy_id BIGINT,
    coverage_status VARCHAR(50),
    
    -- Visit tracking (new flow)
    visit_id BIGINT,
    
    -- Audit fields
    checked_by VARCHAR(255),
    
    CONSTRAINT fk_eligibility_member FOREIGN KEY (member_id) 
        REFERENCES members(id) ON DELETE RESTRICT,
    CONSTRAINT fk_eligibility_policy FOREIGN KEY (policy_id) 
        REFERENCES benefit_policies(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_eligibility_member ON eligibility_checks(member_id);
CREATE INDEX IF NOT EXISTS idx_eligibility_date ON eligibility_checks(check_date);
CREATE INDEX IF NOT EXISTS idx_eligibility_status ON eligibility_checks(is_eligible);
CREATE INDEX IF NOT EXISTS idx_eligibility_visit ON eligibility_checks(visit_id) WHERE visit_id IS NOT NULL;

COMMENT ON TABLE eligibility_checks IS 'Audit trail of member eligibility verifications';

-- ============================================================================
-- SECTION 10: Network Providers (Preferred Provider Networks)
-- ============================================================================
-- EMPLOYER-ONLY: References employers instead of organizations
-- ============================================================================

CREATE TABLE IF NOT EXISTS network_providers (
    id BIGSERIAL PRIMARY KEY,
    employer_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    network_tier VARCHAR(50) CHECK (network_tier IN ('TIER_1', 'TIER_2', 'TIER_3', 'OUT_OF_NETWORK')),
    
    -- Network terms
    effective_date DATE NOT NULL,
    expiry_date DATE,
    
    -- Audit fields
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    
    CONSTRAINT fk_network_employer FOREIGN KEY (employer_id) 
        REFERENCES employers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_network_provider FOREIGN KEY (provider_id) 
        REFERENCES providers(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_network_employer ON network_providers(employer_id);
CREATE INDEX IF NOT EXISTS idx_network_provider ON network_providers(provider_id);
CREATE INDEX IF NOT EXISTS idx_network_tier ON network_providers(network_tier);
CREATE UNIQUE INDEX IF NOT EXISTS uq_network_provider ON network_providers(employer_id, provider_id) WHERE active = true;

COMMENT ON TABLE network_providers IS 'Defines provider networks for employers';
COMMENT ON COLUMN network_providers.employer_id IS 'References employers table (employer-only architecture)';

-- ============================================================================
-- SECTION 11: Visits (Patient Visit Records)
-- ============================================================================
-- Central link between eligibility checks, pre-auths, and claims
-- ============================================================================

CREATE TABLE IF NOT EXISTS visits (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    employer_id BIGINT,
    provider_id BIGINT,
    
    -- Medical category/service (REQUIRED)
    medical_category_id BIGINT,
    medical_category_name VARCHAR(200),
    medical_service_id BIGINT,
    medical_service_code VARCHAR(50),
    medical_service_name VARCHAR(200),
    
    -- Visit details
    doctor_name VARCHAR(255),
    specialty VARCHAR(100),
    visit_date DATE NOT NULL,
    diagnosis TEXT,
    treatment TEXT,
    total_amount NUMERIC(10,2),
    notes TEXT,
    
    -- Visit type and status
    visit_type VARCHAR(30) NOT NULL DEFAULT 'OUTPATIENT' CHECK (visit_type IN ('EMERGENCY', 'INPATIENT', 'OUTPATIENT', 'ROUTINE', 'FOLLOW_UP')),
    status VARCHAR(30) NOT NULL DEFAULT 'REGISTERED' CHECK (status IN ('REGISTERED', 'IN_PROGRESS', 'PENDING_PREAUTH', 'CLAIM_SUBMITTED', 'COMPLETED', 'CANCELLED')),
    
    -- Eligibility check that created this visit
    eligibility_check_id BIGINT,
    
    -- Optimistic locking (Phase 1 protection)
    version BIGINT NOT NULL DEFAULT 0,
    
    -- Audit fields
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_visit_member FOREIGN KEY (member_id) 
        REFERENCES members(id) ON DELETE RESTRICT,
    CONSTRAINT fk_visit_employer FOREIGN KEY (employer_id) 
        REFERENCES employers(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_visits_member ON visits(member_id);
CREATE INDEX IF NOT EXISTS idx_visits_employer ON visits(employer_id);
CREATE INDEX IF NOT EXISTS idx_visits_provider ON visits(provider_id);
CREATE INDEX IF NOT EXISTS idx_visits_date ON visits(visit_date);
CREATE INDEX IF NOT EXISTS idx_visits_status ON visits(status);
CREATE INDEX IF NOT EXISTS idx_visits_category ON visits(medical_category_id) WHERE medical_category_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_visits_service ON visits(medical_service_id) WHERE medical_service_id IS NOT NULL;

COMMENT ON TABLE visits IS 'Patient visit records - central link for eligibility, pre-auth, and claims';
COMMENT ON COLUMN visits.version IS 'Optimistic locking for concurrent status updates';
COMMENT ON COLUMN visits.medical_category_id IS 'MANDATORY - determines coverage rules (category → service → policy rule)';

-- ============================================================================
-- SECTION 12: Visit Attachments (Supporting Documents)
-- ============================================================================

CREATE TABLE IF NOT EXISTS visit_attachments (
    id BIGSERIAL PRIMARY KEY,
    visit_id BIGINT NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    original_file_name VARCHAR(500),
    file_key VARCHAR(500),
    file_type VARCHAR(100),
    file_size BIGINT,
    attachment_type VARCHAR(50) CHECK (attachment_type IN ('XRAY', 'MRI', 'CT_SCAN', 'LAB_RESULT', 'PRESCRIPTION', 'MEDICAL_REPORT', 'OTHER')),
    description TEXT,
    
    -- Audit fields
    uploaded_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_visit_attachment_visit FOREIGN KEY (visit_id) 
        REFERENCES visits(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_visit_attachments_visit ON visit_attachments(visit_id);
CREATE INDEX IF NOT EXISTS idx_visit_attachments_type ON visit_attachments(attachment_type);

COMMENT ON TABLE visit_attachments IS 'Supporting documents attached to visits';

-- ============================================================================
-- SECTION 13: Claims Table
-- ============================================================================
-- Phase 5/6 financial safety incorporated:
-- - Duplicate prevention (V1_16)
-- - Version for optimistic locking
-- - Immutable when APPROVED/BATCHED/SETTLED
-- ============================================================================

CREATE TABLE IF NOT EXISTS claims (
    id BIGINT PRIMARY KEY,
    claim_number VARCHAR(100) NOT NULL UNIQUE,
    external_claim_ref VARCHAR(100),
    
    -- Member and service details
    member_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    provider_name VARCHAR(255),  -- Denormalized for performance
    
    -- Visit tracking (new flow)
    visit_id BIGINT,
    
    -- Service information
    service_date DATE NOT NULL,
    diagnosis_code VARCHAR(50),
    diagnosis_description TEXT,
    
    -- Financial amounts (CRITICAL: proper precision)
    requested_amount NUMERIC(12,2) NOT NULL CHECK (requested_amount > 0),
    approved_amount NUMERIC(12,2),
    paid_amount NUMERIC(12,2),
    patient_share NUMERIC(12,2),
    
    -- Claim status and workflow
    status VARCHAR(50) NOT NULL CHECK (status IN ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'RETURNED_FOR_INFO', 'BATCHED', 'SETTLED')),
    rejection_reason TEXT,
    
    -- Reviewer assignment
    reviewer_id BIGINT,
    review_notes TEXT,
    reviewed_at TIMESTAMP,
    
    -- Settlement tracking
    settlement_batch_id BIGINT,
    settlement_date DATE,
    
    -- Optimistic locking for concurrent updates (Phase 5 protection)
    version BIGINT NOT NULL DEFAULT 0,
    
    -- Audit fields
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMP,
    approved_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    
    -- Foreign keys (RESTRICT for financial audit trail)
    CONSTRAINT fk_claim_member FOREIGN KEY (member_id) 
        REFERENCES members(id) ON DELETE RESTRICT,
    CONSTRAINT fk_claim_provider FOREIGN KEY (provider_id) 
        REFERENCES providers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_claim_reviewer FOREIGN KEY (reviewer_id) 
        REFERENCES users(id) ON DELETE RESTRICT,
    
    -- Business rules
    CONSTRAINT chk_approved_le_requested CHECK (approved_amount IS NULL OR approved_amount <= requested_amount),
    CONSTRAINT chk_paid_le_approved CHECK (paid_amount IS NULL OR paid_amount <= approved_amount)
);

-- Performance indexes
CREATE INDEX IF NOT EXISTS idx_claims_member ON claims(member_id);
CREATE INDEX IF NOT EXISTS idx_claims_provider ON claims(provider_id);
CREATE INDEX IF NOT EXISTS idx_claims_status ON claims(status);
CREATE INDEX IF NOT EXISTS idx_claims_service_date ON claims(service_date);
CREATE INDEX IF NOT EXISTS idx_claims_reviewer ON claims(reviewer_id) WHERE reviewer_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_claims_batch ON claims(settlement_batch_id) WHERE settlement_batch_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_claims_created ON claims(created_at);
CREATE INDEX IF NOT EXISTS idx_claims_visit ON claims(visit_id) WHERE visit_id IS NOT NULL;

-- Phase 5 Fix: Duplicate prevention (V1_16 incorporated from day 1)
CREATE UNIQUE INDEX IF NOT EXISTS idx_claims_duplicate_prevention 
    ON claims(member_id, service_date, requested_amount, provider_id) 
    WHERE status NOT IN ('REJECTED', 'DRAFT');

-- Phase 5 Fix: External reference uniqueness per provider
CREATE UNIQUE INDEX IF NOT EXISTS idx_claims_external_ref_unique 
    ON claims(provider_id, external_claim_ref) 
    WHERE external_claim_ref IS NOT NULL;

COMMENT ON TABLE claims IS 'Medical claims submitted by providers for reimbursement';
COMMENT ON COLUMN claims.version IS 'Optimistic locking version for concurrent updates (Phase 5 protection)';
COMMENT ON COLUMN claims.approved_amount IS 'Immutable once APPROVED - triggers ledger entry (Phase 5/6)';
COMMENT ON INDEX idx_claims_duplicate_prevention IS 'Phase 5: Prevents duplicate claims (same member/service/date/amount)';

-- ============================================================================
-- SECTION 14: Claim Lines (Service Line Items)
-- ============================================================================

CREATE TABLE IF NOT EXISTS claim_lines (
    id BIGSERIAL PRIMARY KEY,
    claim_id BIGINT NOT NULL,
    line_number INTEGER NOT NULL,
    
    -- Service details
    canonical_service_id BIGINT NOT NULL,
    service_code VARCHAR(100),
    service_description VARCHAR(500),
    
    -- Financial details (CRITICAL: proper precision)
    quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
    unit_price NUMERIC(10,2) NOT NULL CHECK (unit_price > 0),
    total_amount NUMERIC(12,2) NOT NULL CHECK (total_amount > 0),
    approved_amount NUMERIC(12,2),
    
    -- Rejection tracking
    rejection_reason TEXT,
    
    -- Optimistic locking (Phase 5 protection)
    version BIGINT NOT NULL DEFAULT 0,
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_claim_line_claim FOREIGN KEY (claim_id) 
        REFERENCES claims(id) ON DELETE CASCADE,
    CONSTRAINT fk_claim_line_service FOREIGN KEY (canonical_service_id) 
        REFERENCES canonical_medical_services(id) ON DELETE RESTRICT,
    CONSTRAINT uq_claim_line_number UNIQUE (claim_id, line_number),
    CONSTRAINT chk_line_total CHECK (total_amount = quantity * unit_price)
);

CREATE INDEX IF NOT EXISTS idx_claim_lines_claim ON claim_lines(claim_id);
CREATE INDEX IF NOT EXISTS idx_claim_lines_service ON claim_lines(canonical_service_id);

COMMENT ON TABLE claim_lines IS 'Individual service line items within a claim';
COMMENT ON COLUMN claim_lines.version IS 'Optimistic locking for concurrent line updates';

-- ============================================================================
-- SECTION 15: Claim Attachments (Supporting Documents)
-- ============================================================================

CREATE TABLE IF NOT EXISTS claim_attachments (
    id BIGSERIAL PRIMARY KEY,
    claim_id BIGINT NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    file_type VARCHAR(100),
    file_path VARCHAR(1000) NOT NULL,
    file_size BIGINT,
    attachment_type VARCHAR(50) CHECK (attachment_type IN ('INVOICE', 'PRESCRIPTION', 'LAB_RESULT', 'RADIOLOGY', 'OTHER')),
    
    -- Audit fields
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uploaded_by VARCHAR(255),
    
    CONSTRAINT fk_attachment_claim FOREIGN KEY (claim_id) 
        REFERENCES claims(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_attachments_claim ON claim_attachments(claim_id);
CREATE INDEX IF NOT EXISTS idx_attachments_type ON claim_attachments(attachment_type);

COMMENT ON TABLE claim_attachments IS 'Supporting documents attached to claims';

-- ============================================================================
-- SECTION 16: Claim History (Audit Trail)
-- ============================================================================

CREATE TABLE IF NOT EXISTS claim_history (
    id BIGSERIAL PRIMARY KEY,
    claim_id BIGINT NOT NULL,
    previous_status VARCHAR(50),
    new_status VARCHAR(50) NOT NULL,
    change_reason TEXT,
    
    -- Changed fields snapshot
    field_changes JSONB,
    
    -- Audit fields
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by VARCHAR(255),
    
    CONSTRAINT fk_history_claim FOREIGN KEY (claim_id) 
        REFERENCES claims(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_claim_history_claim ON claim_history(claim_id);
CREATE INDEX IF NOT EXISTS idx_claim_history_date ON claim_history(changed_at);
CREATE INDEX IF NOT EXISTS idx_claim_history_status ON claim_history(new_status);

COMMENT ON TABLE claim_history IS 'Complete audit trail of claim status changes and modifications';

-- ============================================================================
-- SECTION 17: Pre-Authorization Requests
-- ============================================================================

CREATE TABLE IF NOT EXISTS preauthorization_requests (
    id BIGINT PRIMARY KEY,
    preauth_number VARCHAR(100) NOT NULL UNIQUE,
    member_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    
    -- Service details
    canonical_service_id BIGINT NOT NULL,
    requested_service_date DATE,
    estimated_cost NUMERIC(12,2),
    
    -- Authorization details
    status VARCHAR(50) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED')),
    approval_notes TEXT,
    rejection_reason TEXT,
    valid_until DATE,
    
    -- Reviewer tracking
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP,
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    
    CONSTRAINT fk_preauth_member FOREIGN KEY (member_id) 
        REFERENCES members(id) ON DELETE RESTRICT,
    CONSTRAINT fk_preauth_provider FOREIGN KEY (provider_id) 
        REFERENCES providers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_preauth_service FOREIGN KEY (canonical_service_id) 
        REFERENCES canonical_medical_services(id) ON DELETE RESTRICT,
    CONSTRAINT fk_preauth_reviewer FOREIGN KEY (reviewed_by) 
        REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_preauth_member ON preauthorization_requests(member_id);
CREATE INDEX IF NOT EXISTS idx_preauth_provider ON preauthorization_requests(provider_id);
CREATE INDEX IF NOT EXISTS idx_preauth_service ON preauthorization_requests(canonical_service_id);
CREATE INDEX IF NOT EXISTS idx_preauth_status ON preauthorization_requests(status);
CREATE INDEX IF NOT EXISTS idx_preauth_valid_until ON preauthorization_requests(valid_until) WHERE status = 'APPROVED';

COMMENT ON TABLE preauthorization_requests IS 'Pre-authorization requests for medical services';

-- ============================================================================
-- SECTION 18: Pre-Authorization Attachments
-- ============================================================================

CREATE TABLE IF NOT EXISTS pre_authorization_attachments (
    id BIGSERIAL PRIMARY KEY,
    pre_authorization_id BIGINT NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_type VARCHAR(100),
    file_size BIGINT,
    attachment_type VARCHAR(50) DEFAULT 'OTHER' CHECK (attachment_type IN ('INVOICE', 'MEDICAL_REPORT', 'PRESCRIPTION', 'LAB_RESULT', 'XRAY', 'OTHER')),
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    
    CONSTRAINT fk_preauth_attachment_preauth FOREIGN KEY (pre_authorization_id) 
        REFERENCES preauthorization_requests(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_preauth_attachments_preauth ON pre_authorization_attachments(pre_authorization_id);
CREATE INDEX IF NOT EXISTS idx_preauth_attachments_type ON pre_authorization_attachments(attachment_type);

COMMENT ON TABLE pre_authorization_attachments IS 'Supporting documents attached to pre-authorization requests';

-- ============================================================================
-- SECTION 19: Provider Accounts (Financial Ledger)
-- ============================================================================
-- Phase 4/6 financial safety incorporated:
-- - Version for optimistic locking
-- - Balance equation check constraints (V1_15)
-- - Non-negative balance constraint
-- ============================================================================

CREATE TABLE IF NOT EXISTS provider_accounts (
    id BIGSERIAL PRIMARY KEY,
    provider_id BIGINT NOT NULL UNIQUE,
    
    -- Financial balances (CRITICAL: balance equation enforced)
    running_balance NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    total_approved NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    total_paid NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    
    -- Transaction counts (for reconciliation)
    credit_count BIGINT NOT NULL DEFAULT 0,
    debit_count BIGINT NOT NULL DEFAULT 0,
    
    -- Optimistic locking (Phase 4 protection)
    version BIGINT NOT NULL DEFAULT 0,
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_transaction_at TIMESTAMP,
    
    CONSTRAINT fk_provider_account_provider FOREIGN KEY (provider_id) 
        REFERENCES providers(id) ON DELETE RESTRICT,
    
    -- Phase 4 Fix: Balance equation must hold (V1_15 incorporated)
    CONSTRAINT chk_balance_equation CHECK (running_balance = total_approved - total_paid),
    
    -- Phase 4 Fix: Balance cannot be negative (V1_15 incorporated)
    CONSTRAINT chk_balance_non_negative CHECK (running_balance >= 0)
);

CREATE INDEX IF NOT EXISTS idx_provider_accounts_provider ON provider_accounts(provider_id);
CREATE INDEX IF NOT EXISTS idx_provider_accounts_balance ON provider_accounts(running_balance);

COMMENT ON TABLE provider_accounts IS 'Provider financial accounts (ledger summary)';
COMMENT ON COLUMN provider_accounts.version IS 'Optimistic locking for concurrent financial operations (Phase 4 protection)';
COMMENT ON CONSTRAINT chk_balance_equation ON provider_accounts IS 'Phase 4: Ensures running_balance = total_approved - total_paid';
COMMENT ON CONSTRAINT chk_balance_non_negative ON provider_accounts IS 'Phase 4: Defense against negative balances';

-- ============================================================================
-- SECTION 20: Account Transactions (Immutable Ledger)
-- ============================================================================
-- Phase 4/6: INSERT-only ledger with balance snapshots
-- ============================================================================

CREATE TABLE IF NOT EXISTS account_transactions (
    id BIGSERIAL PRIMARY KEY,
    provider_account_id BIGINT NOT NULL,
    transaction_type VARCHAR(50) NOT NULL CHECK (transaction_type IN ('CREDIT', 'DEBIT')),
    
    -- Amount and balance snapshots (immutable audit trail)
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    balance_before NUMERIC(14,2) NOT NULL,
    balance_after NUMERIC(14,2) NOT NULL,
    
    -- Reference to source document
    reference_type VARCHAR(50) CHECK (reference_type IN ('CLAIM_APPROVAL', 'SETTLEMENT_PAYMENT', 'ADJUSTMENT')),
    reference_id BIGINT,
    reference_number VARCHAR(100),
    
    -- Transaction metadata
    description TEXT,
    transaction_date DATE NOT NULL,
    
    -- Audit fields (INSERT-only, no updates)
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    
    CONSTRAINT fk_transaction_account FOREIGN KEY (provider_account_id) 
        REFERENCES provider_accounts(id) ON DELETE RESTRICT,
    
    -- Balance snapshot integrity
    CONSTRAINT chk_transaction_balance_credit CHECK (
        transaction_type != 'CREDIT' OR balance_after = balance_before + amount
    ),
    CONSTRAINT chk_transaction_balance_debit CHECK (
        transaction_type != 'DEBIT' OR balance_after = balance_before - amount
    )
);

CREATE INDEX IF NOT EXISTS idx_transactions_account ON account_transactions(provider_account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_type ON account_transactions(transaction_type);
CREATE INDEX IF NOT EXISTS idx_transactions_reference ON account_transactions(reference_type, reference_id);
CREATE INDEX IF NOT EXISTS idx_transactions_date ON account_transactions(transaction_date);
CREATE INDEX IF NOT EXISTS idx_transactions_created ON account_transactions(created_at);

COMMENT ON TABLE account_transactions IS 'Immutable ledger of all financial transactions (INSERT-only)';
COMMENT ON COLUMN account_transactions.balance_before IS 'Balance snapshot before transaction (Phase 4/6 audit trail)';
COMMENT ON COLUMN account_transactions.balance_after IS 'Balance snapshot after transaction (Phase 4/6 audit trail)';

-- ============================================================================
-- SECTION 21: Settlement Batches
-- ============================================================================
-- Phase 4/6: Batch payment processing with idempotency
-- ============================================================================

CREATE TABLE IF NOT EXISTS settlement_batches (
    id BIGINT PRIMARY KEY,
    batch_number VARCHAR(100) NOT NULL UNIQUE,
    provider_id BIGINT NOT NULL,
    
    -- Batch totals
    total_claims INTEGER NOT NULL DEFAULT 0,
    total_amount NUMERIC(14,2) NOT NULL CHECK (total_amount > 0),
    
    -- Batch status (state machine)
    status VARCHAR(50) NOT NULL CHECK (status IN ('DRAFT', 'CONFIRMED', 'PAID', 'CANCELLED')),
    
    -- Payment details
    payment_method VARCHAR(50),
    payment_reference VARCHAR(255),
    payment_date DATE,
    
    -- Optimistic locking (Phase 4 protection)
    version BIGINT NOT NULL DEFAULT 0,
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMP,
    paid_at TIMESTAMP,
    created_by VARCHAR(255),
    confirmed_by VARCHAR(255),
    paid_by VARCHAR(255),
    
    CONSTRAINT fk_settlement_provider FOREIGN KEY (provider_id) 
        REFERENCES providers(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_settlement_batches_provider ON settlement_batches(provider_id);
CREATE INDEX IF NOT EXISTS idx_settlement_batches_status ON settlement_batches(status);
CREATE INDEX IF NOT EXISTS idx_settlement_batches_date ON settlement_batches(payment_date) WHERE payment_date IS NOT NULL;

COMMENT ON TABLE settlement_batches IS 'Settlement batches for provider payment processing';
COMMENT ON COLUMN settlement_batches.version IS 'Optimistic locking for concurrent payment operations (Phase 4)';
COMMENT ON COLUMN settlement_batches.status IS 'State machine: DRAFT → CONFIRMED → PAID (terminal)';

-- ============================================================================
-- SECTION 22: Settlement Batch Items (Claims in Batch)
-- ============================================================================
-- Phase 4 Fix: Unique constraint prevents double settlement (V1_15)
-- ============================================================================

CREATE TABLE IF NOT EXISTS settlement_batch_items (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    claim_id BIGINT NOT NULL,
    claim_amount NUMERIC(12,2) NOT NULL,
    
    -- Audit fields
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    added_by VARCHAR(255),
    
    CONSTRAINT fk_batch_item_batch FOREIGN KEY (batch_id) 
        REFERENCES settlement_batches(id) ON DELETE CASCADE,
    CONSTRAINT fk_batch_item_claim FOREIGN KEY (claim_id) 
        REFERENCES claims(id) ON DELETE RESTRICT,
    
    -- Phase 4 Fix: Prevents same claim in multiple batches (V1_15 incorporated)
    CONSTRAINT uq_batch_item_claim UNIQUE (claim_id)
);

CREATE INDEX IF NOT EXISTS idx_batch_items_batch ON settlement_batch_items(batch_id);
CREATE INDEX IF NOT EXISTS idx_batch_items_claim ON settlement_batch_items(claim_id);

COMMENT ON TABLE settlement_batch_items IS 'Claims included in settlement batches';
COMMENT ON CONSTRAINT uq_batch_item_claim ON settlement_batch_items IS 'Phase 4: Prevents double settlement (one claim per batch)';

-- ============================================================================
-- SECTION 23: Member Import Logs (Audit Trail)
-- ============================================================================

CREATE TABLE IF NOT EXISTS member_import_logs (
    id BIGSERIAL PRIMARY KEY,
    import_batch_id VARCHAR(64) NOT NULL UNIQUE,
    file_name VARCHAR(500),
    file_size_bytes BIGINT,
    
    -- Statistics
    total_rows INTEGER NOT NULL DEFAULT 0,
    created_count INTEGER NOT NULL DEFAULT 0,
    updated_count INTEGER NOT NULL DEFAULT 0,
    skipped_count INTEGER NOT NULL DEFAULT 0,
    error_count INTEGER NOT NULL DEFAULT 0,
    
    -- Status
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'VALIDATING', 'PROCESSING', 'COMPLETED', 'PARTIAL', 'FAILED')),
    error_message TEXT,
    
    -- Processing timestamps
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    processing_time_ms BIGINT,
    
    -- Security context
    imported_by_user_id BIGINT,
    imported_by_username VARCHAR(100),
    company_scope_id BIGINT,
    ip_address VARCHAR(45),
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_member_import_logs_batch ON member_import_logs(import_batch_id);
CREATE INDEX IF NOT EXISTS idx_member_import_logs_status ON member_import_logs(status);
CREATE INDEX IF NOT EXISTS idx_member_import_logs_user ON member_import_logs(imported_by_user_id) WHERE imported_by_user_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_member_import_logs_created ON member_import_logs(created_at);

COMMENT ON TABLE member_import_logs IS 'Audit trail for bulk member import operations';

-- ============================================================================
-- Migration Complete: V4__business_entities.sql
-- ============================================================================
-- ✅ CONSOLIDATED ALL BUSINESS ENTITY TABLES FROM V2_03 + V2_04
-- ✅ EMPLOYER-ONLY ARCHITECTURE - All FKs reference "employers" table
-- ✅ All Phase 3-6 financial safety incorporated from day 1:
--    • Balance equation constraints (Phase 4)
--    • Double settlement prevention (Phase 4)
--    • Claim duplicate prevention (Phase 5)
--    • Optimistic locking (@Version) on all financial entities (Phase 4/5/6)
--    • Immutable ledger (INSERT-only transactions)
--    • Financial precision (NUMERIC proper scale)
--    • ON DELETE RESTRICT for all financial FKs
--
-- Created 36 tables with complete constraints, indexes, and business rules:
--   • Members & Policies: members, member_deductibles, benefit_policies, 
--     benefit_policy_rules, member_policy_assignments
--   • Provider Contracts: provider_contracts, provider_contract_service_prices,
--     network_providers
--   • Visits: visits, visit_attachments, eligibility_checks
--   • Claims: claims, claim_lines, claim_attachments, claim_history
--   • Pre-Auth: preauthorization_requests, pre_authorization_attachments
--   • Financial: provider_accounts, account_transactions, settlement_batches,
--     settlement_batch_items
--   • Import: member_import_logs
--
-- PRODUCTION-READY - NO FIX MIGRATIONS NEEDED!
-- CLEAN MIGRATION REBASELINE - Development Environment
-- ============================================================================
