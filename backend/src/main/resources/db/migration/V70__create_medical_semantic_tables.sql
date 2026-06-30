-- Migration to create Medical Semantic Classification tables

-- 1. medical_semantic_rules
CREATE TABLE IF NOT EXISTS medical_semantic_rules (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(255) NOT NULL,
    language VARCHAR(10) NOT NULL,
    keyword_pattern VARCHAR(1000) NOT NULL,
    regex_enabled BOOLEAN DEFAULT FALSE,
    body_system VARCHAR(50),
    medical_specialty VARCHAR(50),
    procedure_type VARCHAR(50),
    procedure_complexity VARCHAR(50),
    likely_encounter_type VARCHAR(50),
    suggested_category_code VARCHAR(100),
    confidence_boost DOUBLE PRECISION,
    requires_review BOOLEAN DEFAULT FALSE,
    review_reason VARCHAR(1000),
    priority INT DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_by VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. medical_synonyms
CREATE TABLE IF NOT EXISTS medical_synonyms (
    id BIGSERIAL PRIMARY KEY,
    term VARCHAR(255) NOT NULL,
    normalized_term VARCHAR(255) NOT NULL,
    language VARCHAR(10) NOT NULL,
    term_type VARCHAR(50),
    mapped_concept VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE
);

-- 3. Add semantic columns to coverage_simulation_items
ALTER TABLE coverage_simulation_items 
ADD COLUMN IF NOT EXISTS medical_meaning_ar VARCHAR(500),
ADD COLUMN IF NOT EXISTS procedure_type VARCHAR(50),
ADD COLUMN IF NOT EXISTS body_system VARCHAR(50),
ADD COLUMN IF NOT EXISTS classification_confidence DOUBLE PRECISION,
ADD COLUMN IF NOT EXISTS classification_source VARCHAR(100),
ADD COLUMN IF NOT EXISTS explanation_ar VARCHAR(1000);
