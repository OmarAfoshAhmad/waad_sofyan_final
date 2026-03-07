-- ============================================================
-- V020: Medical catalog — categories (Level 1)
-- ============================================================
-- Depends on: nothing (self-referencing parent_id only)

CREATE TABLE IF NOT EXISTS medical_categories (
    id              BIGINT PRIMARY KEY DEFAULT nextval('medical_category_seq'),

    -- Legacy name columns (kept for backward compatibility)
    category_name    VARCHAR(255) NOT NULL,
    category_name_ar VARCHAR(255),
    category_code    VARCHAR(50)  NOT NULL UNIQUE,

    -- Unified catalog columns
    code     VARCHAR(50)  NOT NULL UNIQUE,
    name     VARCHAR(200) NOT NULL,
    name_ar  VARCHAR(200),
    name_en  VARCHAR(200),
    parent_id BIGINT,

    -- Care-setting context
    context  VARCHAR(20) NOT NULL DEFAULT 'ANY'
        CHECK (context IN ('INPATIENT','OUTPATIENT','OPERATING_ROOM','EMERGENCY','SPECIAL','ANY')),

    description TEXT,

    -- Soft delete
    deleted     BOOLEAN NOT NULL DEFAULT false,
    deleted_at  TIMESTAMP,
    deleted_by  BIGINT,

    active      BOOLEAN DEFAULT true,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),

    CONSTRAINT fk_medical_category_parent FOREIGN KEY (parent_id)
        REFERENCES medical_categories(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_medical_categories_code       ON medical_categories(code);
CREATE INDEX IF NOT EXISTS idx_medical_categories_active     ON medical_categories(active);
CREATE INDEX IF NOT EXISTS idx_medical_categories_parent_id  ON medical_categories(parent_id);
CREATE INDEX IF NOT EXISTS idx_medical_categories_deleted    ON medical_categories(deleted) WHERE deleted = false;
