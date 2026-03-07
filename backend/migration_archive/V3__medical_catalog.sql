-- ============================================================================
-- V3: Medical Catalog and Pricing Schema
-- ============================================================================
-- CLEAN MIGRATION REBASELINE - Development Environment
-- 
-- Creates:
--   - Medical Categories (Level 1 taxonomy)
--   - Medical Services (Level 2 taxonomy)
--   - Medical Codes (Level 3 taxonomy - CPT, ICD10)
--   - Canonical Medical Services (unified catalog)
--   - Provider Service Prices (with FK relationships from day 1)
--   - Medical Packages
-- 
-- Incorporates: Wave 2 pricing fixes (FK not string codes, BigDecimal precision)
-- ============================================================================

-- ============================================================================
-- SECTION 1: Medical Sequences
-- ============================================================================

CREATE SEQUENCE IF NOT EXISTS medical_category_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS medical_service_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS medical_code_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS canonical_service_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS medical_package_seq START WITH 1 INCREMENT BY 50;

-- ============================================================================
-- SECTION 2: Medical Taxonomy - Level 1 (Categories)
-- ============================================================================

CREATE TABLE IF NOT EXISTS medical_categories (
    id BIGINT PRIMARY KEY,
    category_name VARCHAR(255) NOT NULL,
    category_name_ar VARCHAR(255),
    category_code VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    
    -- Audit fields
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_medical_categories_code ON medical_categories(category_code);
CREATE INDEX IF NOT EXISTS idx_medical_categories_active ON medical_categories(active);

COMMENT ON TABLE medical_categories IS 'Top-level medical service categories (Level 1 taxonomy)';

-- ============================================================================
-- SECTION 3: Medical Taxonomy - Level 2 (Services)
-- ============================================================================

CREATE TABLE IF NOT EXISTS medical_services (
    id BIGINT PRIMARY KEY,
    category_id BIGINT NOT NULL,
    service_name VARCHAR(255) NOT NULL,
    service_name_ar VARCHAR(255),
    service_code VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    
    -- Audit fields
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    
    CONSTRAINT fk_medical_service_category FOREIGN KEY (category_id) 
        REFERENCES medical_categories(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_medical_services_category ON medical_services(category_id);
CREATE INDEX IF NOT EXISTS idx_medical_services_code ON medical_services(service_code);
CREATE INDEX IF NOT EXISTS idx_medical_services_active ON medical_services(active);

COMMENT ON TABLE medical_services IS 'Medical services within categories (Level 2 taxonomy)';

-- ============================================================================
-- SECTION 4: Medical Taxonomy - Level 3 (Detailed Codes)
-- ============================================================================

CREATE TABLE IF NOT EXISTS medical_codes (
    id BIGINT PRIMARY KEY,
    service_id BIGINT NOT NULL,
    code_value VARCHAR(50) NOT NULL UNIQUE,
    code_name VARCHAR(255) NOT NULL,
    code_name_ar VARCHAR(255),
    description TEXT,
    code_type VARCHAR(50) CHECK (code_type IN ('CPT', 'ICD10', 'CUSTOM')),
    
    -- Audit fields
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    
    CONSTRAINT fk_medical_code_service FOREIGN KEY (service_id) 
        REFERENCES medical_services(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_medical_codes_service ON medical_codes(service_id);
CREATE INDEX IF NOT EXISTS idx_medical_codes_value ON medical_codes(code_value);
CREATE INDEX IF NOT EXISTS idx_medical_codes_type ON medical_codes(code_type);
CREATE INDEX IF NOT EXISTS idx_medical_codes_active ON medical_codes(active);

COMMENT ON TABLE medical_codes IS 'Detailed medical codes (CPT, ICD10, etc) within services (Level 3 taxonomy)';

-- ============================================================================
-- SECTION 5: Canonical Medical Services (Unified Catalog)
-- ============================================================================

CREATE TABLE IF NOT EXISTS canonical_medical_services (
    id BIGINT PRIMARY KEY,
    canonical_service_code VARCHAR(100) NOT NULL UNIQUE,
    service_name VARCHAR(500) NOT NULL,
    service_name_ar VARCHAR(500),
    
    -- Taxonomy classification
    category_level_1 VARCHAR(255),
    category_level_2 VARCHAR(255),
    category_level_3 VARCHAR(255),
    
    -- Service metadata
    service_type VARCHAR(100),
    unit_of_measure VARCHAR(50),
    
    -- Full-text search optimization
    search_keywords TEXT,
    
    -- Audit fields
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_canonical_code ON canonical_medical_services(canonical_service_code);
CREATE INDEX IF NOT EXISTS idx_canonical_level1 ON canonical_medical_services(category_level_1) WHERE active = true;
CREATE INDEX IF NOT EXISTS idx_canonical_level2 ON canonical_medical_services(category_level_2) WHERE active = true;
CREATE INDEX IF NOT EXISTS idx_canonical_active ON canonical_medical_services(active);

-- Full-text search index for service lookup
CREATE INDEX IF NOT EXISTS idx_canonical_search ON canonical_medical_services 
    USING gin(to_tsvector('english', service_name || ' ' || COALESCE(search_keywords, '')));

COMMENT ON TABLE canonical_medical_services IS 'Unified medical services catalog for pricing and claims';
COMMENT ON COLUMN canonical_medical_services.canonical_service_code IS 'Unique code identifying this service across system';

-- ============================================================================
-- SECTION 6: Provider Service Prices
-- ============================================================================
-- WAVE 2 FIX: Uses FK relationship (canonical_service_id) NOT string code
-- BigDecimal precision (NUMERIC(10,2)) from day 1
-- ============================================================================

CREATE TABLE IF NOT EXISTS provider_service_prices (
    id BIGSERIAL PRIMARY KEY,
    provider_id BIGINT NOT NULL,
    canonical_service_id BIGINT NOT NULL,
    
    -- Pricing
    unit_price NUMERIC(10,2) NOT NULL,
    
    -- Effective date range
    effective_from DATE NOT NULL,
    effective_to DATE,
    
    -- Status
    active BOOLEAN NOT NULL DEFAULT true,
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    
    CONSTRAINT fk_provider_price_provider FOREIGN KEY (provider_id) 
        REFERENCES providers(id) ON DELETE CASCADE,
    CONSTRAINT fk_provider_price_canonical FOREIGN KEY (canonical_service_id) 
        REFERENCES canonical_medical_services(id) ON DELETE RESTRICT,
    CONSTRAINT chk_price_positive CHECK (unit_price > 0),
    CONSTRAINT chk_price_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX IF NOT EXISTS idx_provider_prices_provider ON provider_service_prices(provider_id);
CREATE INDEX IF NOT EXISTS idx_provider_prices_canonical ON provider_service_prices(canonical_service_id);
CREATE INDEX IF NOT EXISTS idx_provider_prices_dates ON provider_service_prices(effective_from, effective_to);
CREATE INDEX IF NOT EXISTS idx_provider_prices_active ON provider_service_prices(active) WHERE active = true;

-- Composite index for price lookup queries
CREATE INDEX IF NOT EXISTS idx_provider_prices_lookup ON provider_service_prices(provider_id, canonical_service_id, active) 
    WHERE active = true;

COMMENT ON TABLE provider_service_prices IS 'Provider-specific pricing for canonical medical services';
COMMENT ON CONSTRAINT fk_provider_price_canonical ON provider_service_prices IS 'Wave 2 fix: FK relationship not string code';

-- ============================================================================
-- SECTION 7: Provider Service Price Import Log
-- ============================================================================

CREATE TABLE IF NOT EXISTS provider_service_price_import_log (
    id BIGSERIAL PRIMARY KEY,
    provider_id BIGINT,
    file_name VARCHAR(500),
    total_rows INTEGER,
    successful_rows INTEGER,
    failed_rows INTEGER,
    error_details TEXT,
    import_status VARCHAR(50) CHECK (import_status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED')),
    
    -- Audit fields
    imported_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    imported_by VARCHAR(255),
    
    CONSTRAINT fk_price_import_provider FOREIGN KEY (provider_id) 
        REFERENCES providers(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_price_import_provider ON provider_service_price_import_log(provider_id);
CREATE INDEX IF NOT EXISTS idx_price_import_status ON provider_service_price_import_log(import_status);
CREATE INDEX IF NOT EXISTS idx_price_import_date ON provider_service_price_import_log(imported_at);

COMMENT ON TABLE provider_service_price_import_log IS 'Audit trail for bulk price imports';

-- ============================================================================
-- SECTION 8: Medical Packages
-- ============================================================================

CREATE TABLE IF NOT EXISTS medical_packages (
    id BIGINT PRIMARY KEY,
    package_code VARCHAR(100) NOT NULL UNIQUE,
    package_name VARCHAR(500) NOT NULL,
    package_name_ar VARCHAR(500),
    description TEXT,
    
    -- Audit fields
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_medical_packages_code ON medical_packages(package_code);
CREATE INDEX IF NOT EXISTS idx_medical_packages_active ON medical_packages(active);

COMMENT ON TABLE medical_packages IS 'Pre-defined packages of medical services (e.g., annual checkup package)';

-- ============================================================================
-- SECTION 9: Medical Package Services (Package Items)
-- ============================================================================

CREATE TABLE IF NOT EXISTS medical_package_services (
    id BIGSERIAL PRIMARY KEY,
    package_id BIGINT NOT NULL,
    canonical_service_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 1,
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    
    CONSTRAINT fk_package_service_package FOREIGN KEY (package_id) 
        REFERENCES medical_packages(id) ON DELETE CASCADE,
    CONSTRAINT fk_package_service_canonical FOREIGN KEY (canonical_service_id) 
        REFERENCES canonical_medical_services(id) ON DELETE RESTRICT,
    CONSTRAINT chk_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX IF NOT EXISTS idx_package_services_package ON medical_package_services(package_id);
CREATE INDEX IF NOT EXISTS idx_package_services_canonical ON medical_package_services(canonical_service_id);

COMMENT ON TABLE medical_package_services IS 'Services included in medical packages';

-- ============================================================================
-- Migration Complete: V3
-- ============================================================================
-- Created: Medical taxonomy (3 levels), Canonical services, Provider pricing
-- Hardened: FK relationships from day 1 (Wave 2 fix incorporated)
-- Precision: BigDecimal (NUMERIC) for all financial fields
-- Ready for: Business entities (V4)
-- ============================================================================
