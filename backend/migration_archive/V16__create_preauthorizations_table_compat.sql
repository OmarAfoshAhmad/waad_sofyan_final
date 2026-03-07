-- ============================================================================
-- V16: Compatibility table for pre_authorizations
-- ============================================================================
-- Some runtime JPQL joins require table name pre_authorizations.
-- This creates a compatible table shape to avoid SQL runtime failures.

CREATE TABLE IF NOT EXISTS pre_authorizations (
    id BIGSERIAL PRIMARY KEY,
    active BOOLEAN DEFAULT true,
    approved_amount NUMERIC(15,2),
    approved_at TIMESTAMP,
    approved_by VARCHAR(255),
    contract_price NUMERIC(15,2),
    copay_amount NUMERIC(15,2),
    copay_percentage NUMERIC(10,2),
    coverage_percent_snapshot INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    currency VARCHAR(20),
    diagnosis_code VARCHAR(100),
    diagnosis_description TEXT,
    expected_service_date DATE,
    expiry_date DATE,
    insurance_covered_amount NUMERIC(15,2),
    medical_service_id BIGINT,
    member_id BIGINT,
    notes TEXT,
    patient_copay_percent_snapshot INTEGER,
    pre_auth_number VARCHAR(100),
    priority VARCHAR(50),
    provider_id BIGINT,
    reference_number VARCHAR(100),
    rejection_reason TEXT,
    request_date TIMESTAMP,
    requires_pa BOOLEAN,
    reserved_amount NUMERIC(15,2),
    service_category_id BIGINT,
    service_category_name VARCHAR(255),
    service_code VARCHAR(100),
    service_name VARCHAR(255),
    service_type VARCHAR(100),
    status VARCHAR(50),
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT,
    visit_id BIGINT
);

CREATE INDEX IF NOT EXISTS idx_preauth_member_id ON pre_authorizations(member_id);
CREATE INDEX IF NOT EXISTS idx_preauth_provider_id ON pre_authorizations(provider_id);
CREATE INDEX IF NOT EXISTS idx_preauth_status ON pre_authorizations(status);

-- ============================================================================
-- Migration Complete: V16
-- ============================================================================
