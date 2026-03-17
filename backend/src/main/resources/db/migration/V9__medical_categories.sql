-- ============================================================
-- V9: جداول التصنيف الطبي
-- ============================================================

CREATE TABLE IF NOT EXISTS medical_categories (
    id          BIGINT PRIMARY KEY DEFAULT nextval('medical_category_seq'),

    category_name    VARCHAR(255) NOT NULL,
    category_name_ar VARCHAR(255),
    category_code    VARCHAR(50) NOT NULL UNIQUE,

    code        VARCHAR(50) NOT NULL UNIQUE,
    name        VARCHAR(200) NOT NULL,
    name_ar     VARCHAR(200),
    name_en     VARCHAR(200),
    parent_id   BIGINT,

    context     VARCHAR(20) NOT NULL DEFAULT 'ANY'
        CHECK (context IN ('INPATIENT','OUTPATIENT','OPERATING_ROOM','EMERGENCY','SPECIAL','ANY')),

    description TEXT,

    deleted     BOOLEAN NOT NULL DEFAULT false,
    deleted_at  TIMESTAMP,
    deleted_by  BIGINT,

    active          BOOLEAN DEFAULT true,
    coverage_percent DECIMAL(5,2),

    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),

    CONSTRAINT fk_medical_category_parent FOREIGN KEY (parent_id)
        REFERENCES medical_categories(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_medical_categories_code         ON medical_categories(code);
CREATE INDEX IF NOT EXISTS idx_medical_categories_active       ON medical_categories(active);
CREATE INDEX IF NOT EXISTS idx_medical_categories_parent_id    ON medical_categories(parent_id);
CREATE INDEX IF NOT EXISTS idx_medical_categories_deleted      ON medical_categories(deleted) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_medical_categories_deleted_code ON medical_categories(deleted, code);

CREATE TABLE IF NOT EXISTS medical_category_roots (
    category_id BIGINT NOT NULL,
    root_id     BIGINT NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (category_id, root_id),
    CONSTRAINT fk_mcr_category FOREIGN KEY (category_id) REFERENCES medical_categories(id) ON DELETE CASCADE,
    CONSTRAINT fk_mcr_root     FOREIGN KEY (root_id)     REFERENCES medical_categories(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_mcr_root_id ON medical_category_roots(root_id);
