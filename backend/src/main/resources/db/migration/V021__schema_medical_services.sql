-- ============================================================
-- V021: Medical services, multi-context categories, aliases (Level 2)
-- ============================================================
-- Depends on: V020 (medical_categories)

-- ----------------------------------------------------------
-- SECTION 1: Medical services (canonical catalog entries)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS medical_services (
    id              BIGINT PRIMARY KEY DEFAULT nextval('medical_service_seq'),
    category_id     BIGINT NOT NULL,

    -- Legacy bilingual name columns
    service_name    VARCHAR(255) NOT NULL,
    service_name_ar VARCHAR(255),
    service_code    VARCHAR(50)  NOT NULL UNIQUE,

    -- Unified catalog columns
    name     VARCHAR(255),
    name_ar  VARCHAR(255),
    name_en  VARCHAR(255),
    code     VARCHAR(50),
    cost     NUMERIC(15,2),

    -- Flags
    is_master   BOOLEAN NOT NULL DEFAULT false,
    requires_pa BOOLEAN NOT NULL DEFAULT false,

    -- Soft delete
    deleted     BOOLEAN NOT NULL DEFAULT false,
    deleted_at  TIMESTAMP,
    deleted_by  BIGINT,

    description TEXT,
    active      BOOLEAN DEFAULT true,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),

    CONSTRAINT fk_medical_service_category FOREIGN KEY (category_id)
        REFERENCES medical_categories(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_medical_services_category  ON medical_services(category_id);
CREATE INDEX IF NOT EXISTS idx_medical_services_code      ON medical_services(service_code);
CREATE INDEX IF NOT EXISTS idx_medical_services_active    ON medical_services(active);
CREATE INDEX IF NOT EXISTS idx_medical_services_is_master ON medical_services(is_master) WHERE deleted = false;

-- Partial unique: code is unique only among non-deleted rows
CREATE UNIQUE INDEX IF NOT EXISTS uq_medical_services_code_active ON medical_services(code)
    WHERE deleted = false;

-- ----------------------------------------------------------
-- SECTION 2: Service–category multi-context junction
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS medical_service_categories (
    id          BIGINT PRIMARY KEY DEFAULT nextval('medical_service_category_seq'),
    service_id  BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    context     VARCHAR(20) NOT NULL DEFAULT 'ANY'
        CHECK (context IN ('OUTPATIENT','INPATIENT','EMERGENCY','ANY')),
    is_primary  BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(255),

    CONSTRAINT fk_msc_service  FOREIGN KEY (service_id)  REFERENCES medical_services(id)   ON DELETE CASCADE,
    CONSTRAINT fk_msc_category FOREIGN KEY (category_id) REFERENCES medical_categories(id) ON DELETE RESTRICT,

    -- Only one primary mapping per service per context
    CONSTRAINT uq_msc_primary_per_context UNIQUE (service_id, context, is_primary)
        DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX IF NOT EXISTS idx_msc_service_id  ON medical_service_categories(service_id);
CREATE INDEX IF NOT EXISTS idx_msc_category_id ON medical_service_categories(category_id);
CREATE INDEX IF NOT EXISTS idx_msc_context     ON medical_service_categories(context);

-- ----------------------------------------------------------
-- SECTION 3: Service display aliases (autocomplete/search)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS ent_service_aliases (
    id                 BIGINT PRIMARY KEY DEFAULT nextval('ent_service_alias_seq'),
    medical_service_id BIGINT NOT NULL,
    alias_text         VARCHAR(255) NOT NULL,
    locale             VARCHAR(10)  NOT NULL DEFAULT 'ar',
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by         VARCHAR(255),

    CONSTRAINT fk_alias_service FOREIGN KEY (medical_service_id)
        REFERENCES medical_services(id) ON DELETE CASCADE,
    CONSTRAINT uq_alias_text_per_service_locale UNIQUE (medical_service_id, alias_text, locale)
);

CREATE INDEX IF NOT EXISTS idx_aliases_service_id ON ent_service_aliases(medical_service_id);
CREATE INDEX IF NOT EXISTS idx_aliases_text        ON ent_service_aliases(alias_text);
CREATE INDEX IF NOT EXISTS idx_aliases_locale      ON ent_service_aliases(locale);
