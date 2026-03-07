-- ============================================================================
-- V83: Unified Medical Catalog — Phase 2
-- ============================================================================
-- Aligns medical_services and medical_categories with reference catalog model.
-- Adds: bilingual name columns, is_master flag, cost field, soft-delete columns.
-- Creates: medical_service_categories junction table, ent_service_aliases.
-- ============================================================================

-- ============================================================================
-- SECTION 1: medical_services — add unified catalog columns
-- ============================================================================

-- Arabic name (reference repo pattern; legacy service_name_ar kept for compat)
ALTER TABLE medical_services
    ADD COLUMN IF NOT EXISTS name_ar  VARCHAR(255);

-- English name (explicit bilingual support)
ALTER TABLE medical_services
    ADD COLUMN IF NOT EXISTS name_en  VARCHAR(255);

-- Reference cost (unified catalog pricing anchor)
-- Distinct from base_price (kept for backward compat/reporting)
ALTER TABLE medical_services
    ADD COLUMN IF NOT EXISTS cost     NUMERIC(15,2);

-- Master catalog flag — true = canonical reference entry, false = alias/variant
ALTER TABLE medical_services
    ADD COLUMN IF NOT EXISTS is_master BOOLEAN NOT NULL DEFAULT false;

-- Soft delete
ALTER TABLE medical_services
    ADD COLUMN IF NOT EXISTS deleted    BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE medical_services
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

ALTER TABLE medical_services
    ADD COLUMN IF NOT EXISTS deleted_by BIGINT;

-- Backfill: existing Arabic name → name_ar
UPDATE medical_services
SET name_ar = service_name_ar
WHERE name_ar IS NULL AND service_name_ar IS NOT NULL;

-- Backfill: existing name → name_en (Latin systems had English in name)
UPDATE medical_services
SET name_en = name
WHERE name_en IS NULL;

-- Partial index: enforce code uniqueness only among non-deleted rows
CREATE UNIQUE INDEX IF NOT EXISTS uq_medical_services_code_active
    ON medical_services(code)
    WHERE deleted = false;

-- Index: master catalog lookups
CREATE INDEX IF NOT EXISTS idx_medical_services_is_master
    ON medical_services(is_master)
    WHERE deleted = false;

-- ============================================================================
-- SECTION 2: medical_categories — add unified catalog columns
-- ============================================================================

-- Arabic name
ALTER TABLE medical_categories
    ADD COLUMN IF NOT EXISTS name_ar  VARCHAR(200);

-- English name
ALTER TABLE medical_categories
    ADD COLUMN IF NOT EXISTS name_en  VARCHAR(200);

-- Soft delete
ALTER TABLE medical_categories
    ADD COLUMN IF NOT EXISTS deleted    BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE medical_categories
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

ALTER TABLE medical_categories
    ADD COLUMN IF NOT EXISTS deleted_by BIGINT;

-- Backfill: category_name_ar → name_ar
UPDATE medical_categories
SET name_ar = category_name_ar
WHERE name_ar IS NULL AND category_name_ar IS NOT NULL;

-- Backfill: name → name_en
UPDATE medical_categories
SET name_en = name
WHERE name_en IS NULL;

-- ============================================================================
-- SECTION 3: medical_service_categories — multi-context junction table
-- ============================================================================
-- Allows one service to appear in multiple categories across different
-- clinical contexts (OUTPATIENT, INPATIENT, EMERGENCY, ANY).
-- ============================================================================

CREATE SEQUENCE IF NOT EXISTS medical_service_category_seq
    START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS medical_service_categories (
    id          BIGINT      PRIMARY KEY DEFAULT nextval('medical_service_category_seq'),
    service_id  BIGINT      NOT NULL,
    category_id BIGINT      NOT NULL,
    context     VARCHAR(20) NOT NULL DEFAULT 'ANY'
                    CHECK (context IN ('OUTPATIENT', 'INPATIENT', 'EMERGENCY', 'ANY')),
    is_primary  BOOLEAN     NOT NULL DEFAULT false,

    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(255),

    CONSTRAINT fk_msc_service  FOREIGN KEY (service_id)
        REFERENCES medical_services(id) ON DELETE CASCADE,
    CONSTRAINT fk_msc_category FOREIGN KEY (category_id)
        REFERENCES medical_categories(id) ON DELETE RESTRICT,

    -- Only one primary mapping per service per context
    CONSTRAINT uq_msc_primary_per_context
        UNIQUE (service_id, context, is_primary)
        DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX IF NOT EXISTS idx_msc_service_id  ON medical_service_categories(service_id);
CREATE INDEX IF NOT EXISTS idx_msc_category_id ON medical_service_categories(category_id);
CREATE INDEX IF NOT EXISTS idx_msc_context     ON medical_service_categories(context);

COMMENT ON TABLE medical_service_categories IS
    'Multi-context service↔category mappings (one service, many categories/contexts)';

-- ============================================================================
-- SECTION 4: ent_service_aliases — searchable display aliases
-- ============================================================================
-- Stores alternative names / colloquial Arabic terms for services.
-- Used for search-as-you-type and data-entry autocomplete.
-- ============================================================================

CREATE SEQUENCE IF NOT EXISTS ent_service_alias_seq
    START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS ent_service_aliases (
    id                 BIGINT       PRIMARY KEY DEFAULT nextval('ent_service_alias_seq'),
    medical_service_id BIGINT       NOT NULL,
    alias_text         VARCHAR(255) NOT NULL,
    locale             VARCHAR(10)  NOT NULL DEFAULT 'ar',

    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by         VARCHAR(255),

    CONSTRAINT fk_alias_service FOREIGN KEY (medical_service_id)
        REFERENCES medical_services(id) ON DELETE CASCADE,

    -- Same alias text must not repeat for the same service+locale
    CONSTRAINT uq_alias_text_per_service_locale
        UNIQUE (medical_service_id, alias_text, locale)
);

CREATE INDEX IF NOT EXISTS idx_aliases_service_id  ON ent_service_aliases(medical_service_id);
CREATE INDEX IF NOT EXISTS idx_aliases_text        ON ent_service_aliases(alias_text);
CREATE INDEX IF NOT EXISTS idx_aliases_locale      ON ent_service_aliases(locale);

COMMENT ON TABLE ent_service_aliases IS
    'Alternative/colloquial names for medical services — supports bilingual search';

-- ============================================================================
-- SECTION 5: medical_package_services — migrate FK to medical_service_id
-- ============================================================================
-- canonical_service_id was dropped in V82 (CASCADE removed FK constraint).
-- The table already has medical_service_id from V30 backfill.
-- Ensure the FK constraint exists.
-- ============================================================================

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_mps_medical_service'
          AND conrelid = 'medical_package_services'::regclass
    ) THEN
        -- Add FK only if medical_service_id column exists
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'medical_package_services'
              AND column_name = 'medical_service_id'
        ) THEN
            ALTER TABLE medical_package_services
                ADD CONSTRAINT fk_mps_medical_service
                FOREIGN KEY (medical_service_id)
                REFERENCES medical_services(id) ON DELETE RESTRICT;
        END IF;
    END IF;
END $$;

-- ============================================================================
-- Migration Complete: V83
-- ============================================================================
-- Unified catalog columns added to medical_services + medical_categories.
-- Junction table medical_service_categories enables multi-context mapping.
-- Alias table ent_service_aliases enables bilingual fuzzy search.
-- ============================================================================
