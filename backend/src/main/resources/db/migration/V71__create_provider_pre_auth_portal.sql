-- V71__create_provider_pre_auth_portal.sql

-- 1. Modify pre_authorizations table to add new columns and alter status length
ALTER TABLE pre_authorizations
    ALTER COLUMN status TYPE VARCHAR(25),
    ALTER COLUMN service_code DROP NOT NULL,
    ALTER COLUMN contract_price DROP NOT NULL,
    ALTER COLUMN requires_pa DROP NOT NULL,
    ALTER COLUMN diagnosis_code DROP NOT NULL;

ALTER TABLE pre_authorizations
    ADD COLUMN IF NOT EXISTS contract_id BIGINT,
    ADD COLUMN IF NOT EXISTS policy_id BIGINT,
    ADD COLUMN IF NOT EXISTS requested_total_amount NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS contract_total_amount NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS manual_total_amount NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS approved_total_amount NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS patient_share NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS company_share NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS chief_complaint VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS treatment_plan VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS clinical_notes VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS diagnosis_text VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS rejection_reason_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS decision_notes VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS decision_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS decision_by VARCHAR(100);

-- 2. Create pre_authorization_lines table
CREATE TABLE IF NOT EXISTS pre_authorization_lines (
    id BIGSERIAL PRIMARY KEY,
    pre_authorization_id BIGINT NOT NULL REFERENCES pre_authorizations(id) ON DELETE CASCADE,
    provider_service_id BIGINT,
    provider_service_code VARCHAR(50),
    service_name VARCHAR(500),
    source_type VARCHAR(50) DEFAULT 'CONTRACTED',
    contract_price NUMERIC(15,2),
    manual_price NUMERIC(15,2),
    requested_amount NUMERIC(15,2),
    approved_amount NUMERIC(15,2),
    variance_amount NUMERIC(15,2),
    variance_percentage NUMERIC(5,2),
    price_variance_status VARCHAR(50),
    requires_price_review BOOLEAN DEFAULT FALSE,
    price_override_reason VARCHAR(1000),
    insurance_category_code VARCHAR(50),
    medical_specialty VARCHAR(100),
    procedure_type VARCHAR(50),
    encounter_type VARCHAR(50),
    coverage_status VARCHAR(50),
    coverage_reason VARCHAR(500),
    coverage_percentage INTEGER,
    patient_share NUMERIC(15,2),
    company_share NUMERIC(15,2),
    requires_pre_approval BOOLEAN DEFAULT TRUE,
    requires_review BOOLEAN DEFAULT FALSE,
    decision_status VARCHAR(50) DEFAULT 'PENDING',
    decision_reason_code VARCHAR(50),
    decision_notes VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP
);

-- 3. Modify pre_authorization_attachments to add line_id
ALTER TABLE pre_authorization_attachments
    ADD COLUMN IF NOT EXISTS line_id BIGINT;

-- 4. Create pre_auth_rejection_reasons table
CREATE TABLE IF NOT EXISTS pre_auth_rejection_reasons (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    arabic_label VARCHAR(200) NOT NULL,
    english_label VARCHAR(200),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    description VARCHAR(500)
);

-- Seed initial rejection reasons
INSERT INTO pre_auth_rejection_reasons (code, arabic_label, english_label) VALUES 
('NOT_COVERED', 'غير مغطى ضمن الوثيقة', 'Not covered by policy') ON CONFLICT DO NOTHING;
INSERT INTO pre_auth_rejection_reasons (code, arabic_label, english_label) VALUES 
('COSMETIC', 'إجراء تجميلي', 'Cosmetic procedure') ON CONFLICT DO NOTHING;
INSERT INTO pre_auth_rejection_reasons (code, arabic_label, english_label) VALUES 
('LIMIT_EXCEEDED', 'تجاوز الحد الأقصى', 'Limit exceeded') ON CONFLICT DO NOTHING;
INSERT INTO pre_auth_rejection_reasons (code, arabic_label, english_label) VALUES 
('MEDICALLY_UNJUSTIFIED', 'غير مبرر طبياً', 'Medically unjustified') ON CONFLICT DO NOTHING;
INSERT INTO pre_auth_rejection_reasons (code, arabic_label, english_label) VALUES 
('PRICE_REJECTED', 'مبالغة في السعر المرجعي', 'Price rejected') ON CONFLICT DO NOTHING;
