-- ============================================================================
-- V90: Catalog Link Backfill + Structural Hardening
-- ============================================================================
-- 1. Add category_id to medical_specialties
-- 2. Populate specialty → category mapping for all 44 specialties
-- 3. Add active column to medical_service_categories
-- 4. Fix SRV-001 missing specialty_id
-- 5. Backfill medical_service_categories for all 530 services
-- 6. SET NOT NULL on specialty_id (medical_services) + category_id (specialties)
-- 7. Add performance indexes
-- Target DB: v89
-- ============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- STEP 1: Add category_id to medical_specialties (nullable first)
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE medical_specialties
    ADD COLUMN IF NOT EXISTS category_id BIGINT;

-- ─────────────────────────────────────────────────────────────────────────────
-- STEP 2: Populate specialty → category mapping for all 44 specialties
-- ─────────────────────────────────────────────────────────────────────────────

-- CAT-INPAT: ICU, Emergency, Dialysis, Accommodation
UPDATE medical_specialties
SET    category_id = (SELECT id FROM medical_categories WHERE code = 'CAT-INPAT' LIMIT 1)
WHERE  code IN ('SP-ICU', 'SP-EMERG', 'SP-DIALYSIS', 'SP-ACCOMM')
  AND  category_id IS NULL;

-- CAT-OPER: All surgical specialties + Anesthesia
UPDATE medical_specialties
SET    category_id = (SELECT id FROM medical_categories WHERE code = 'CAT-OPER' LIMIT 1)
WHERE  code IN (
    'SP-GEN-SURG', 'SP-ORTHO', 'SP-NEURO-SURG', 'SP-CARDIO-SURG',
    'SP-VASC', 'SP-UROL', 'SP-PEDS-SURG', 'SP-PLAST',
    'SP-ENT-SURG', 'SP-OPHTH-SURG', 'SP-GI-SURG',
    'SP-MAXFAC', 'SP-OBS-SURG', 'SP-ANES'
)
  AND  category_id IS NULL;

-- CAT-OUTPAT: All outpatient clinical disciplines
UPDATE medical_specialties
SET    category_id = (SELECT id FROM medical_categories WHERE code = 'CAT-OUTPAT' LIMIT 1)
WHERE  code IN (
    'SP-NURSING', 'SP-CARDIO', 'SP-NEUROL', 'SP-GASTRO', 'SP-PULM',
    'SP-NEPHRO', 'SP-ENDO', 'SP-RHEUM', 'SP-DERMA', 'SP-ENT',
    'SP-OPHTH', 'SP-OBS-GYN', 'SP-PEDS', 'SP-ONCOL', 'SP-PSYCH',
    'SP-REPRO', 'SP-INFECT', 'SP-CARD-DIAG', 'SP-NEURO-DIAG',
    'SP-AUDIO', 'SP-CHEMO'
)
  AND  category_id IS NULL;

-- CAT-LAB: Laboratory & Pathology
UPDATE medical_specialties
SET    category_id = (SELECT id FROM medical_categories WHERE code = 'CAT-LAB' LIMIT 1)
WHERE  code IN ('SP-LAB', 'SP-PATH')
  AND  category_id IS NULL;

-- CAT-RAD: Radiology & Medical Imaging
UPDATE medical_specialties
SET    category_id = (SELECT id FROM medical_categories WHERE code = 'CAT-RAD' LIMIT 1)
WHERE  code = 'SP-RAD'
  AND  category_id IS NULL;

-- CAT-DENT-PREV: Dentistry
UPDATE medical_specialties
SET    category_id = (SELECT id FROM medical_categories WHERE code = 'CAT-DENT-PREV' LIMIT 1)
WHERE  code = 'SP-DENT'
  AND  category_id IS NULL;

-- CAT-PHYSIO: Physical Therapy
UPDATE medical_specialties
SET    category_id = (SELECT id FROM medical_categories WHERE code = 'CAT-PHYSIO' LIMIT 1)
WHERE  code = 'SP-PHYSIO'
  AND  category_id IS NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- STEP 3: Enforce NOT NULL on medical_specialties.category_id
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE medical_specialties
    ALTER COLUMN category_id SET NOT NULL;

ALTER TABLE medical_specialties
    ADD CONSTRAINT fk_specialty_category
        FOREIGN KEY (category_id) REFERENCES medical_categories(id) ON DELETE RESTRICT;

-- ─────────────────────────────────────────────────────────────────────────────
-- STEP 4: Add active column to medical_service_categories
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE medical_service_categories
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

-- ─────────────────────────────────────────────────────────────────────────────
-- STEP 5: Fix SRV-001 missing specialty_id (assign to SP-NURSING)
-- ─────────────────────────────────────────────────────────────────────────────
UPDATE medical_services
SET    specialty_id = (SELECT id FROM medical_specialties WHERE code = 'SP-NURSING' LIMIT 1)
WHERE  code = 'SRV-001'
  AND  specialty_id IS NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- STEP 6: Backfill medical_service_categories for all services
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO medical_service_categories (service_id, category_id, context, is_primary, active)
SELECT
    s.id,
    sp.category_id,
    'ANY',
    TRUE,
    TRUE
FROM medical_services s
JOIN medical_specialties sp ON sp.id = s.specialty_id
WHERE s.deleted = FALSE
  AND s.specialty_id IS NOT NULL
  AND sp.category_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM medical_service_categories msc
      WHERE  msc.service_id = s.id
  );

-- ─────────────────────────────────────────────────────────────────────────────
-- STEP 7: Set specialty_id NOT NULL on medical_services
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE medical_services
    ALTER COLUMN specialty_id SET NOT NULL;

ALTER TABLE medical_services
    DROP CONSTRAINT IF EXISTS chk_service_has_specialty;

ALTER TABLE medical_services
    ADD CONSTRAINT chk_service_has_specialty
        CHECK (specialty_id IS NOT NULL);

-- ─────────────────────────────────────────────────────────────────────────────
-- STEP 8: Performance indexes
-- ─────────────────────────────────────────────────────────────────────────────

-- Services → Specialty (hierarchical query)
CREATE INDEX IF NOT EXISTS idx_services_specialty
    ON medical_services(specialty_id)
    WHERE deleted = FALSE;

-- Specialties → Category
CREATE INDEX IF NOT EXISTS idx_specialties_category
    ON medical_specialties(category_id)
    WHERE deleted = FALSE;

-- Service name_ar lower-case lookup (for deduplication in imports)
CREATE INDEX IF NOT EXISTS idx_service_name_ar_lower
    ON medical_services(LOWER(name_ar))
    WHERE deleted = FALSE;

-- Specialty name_ar lower-case lookup
CREATE INDEX IF NOT EXISTS idx_specialty_name_ar_lower
    ON medical_specialties(LOWER(name_ar))
    WHERE deleted = FALSE;

-- Category code lookup
CREATE INDEX IF NOT EXISTS idx_categories_code
    ON medical_categories(code)
    WHERE deleted = FALSE;
