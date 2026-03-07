-- ============================================================================
-- V107: Comprehensive Medical Taxonomy Backfill
-- ============================================================================
-- Purpose:
--   Ensure EVERY active master medical service is linked to a root category.
--   This resolves the "unclassified services" issue in the Catalog UI.
--
-- Strategy:
--   1. Identify all master services (is_master=TRUE) that lack a primary
--      mapping in medical_service_categories.
--   2. Use the specialty_id -> category_id mapping to create the missing links.
--   3. Apply context-specific overrides for cosmetic/outpatient services
--      that were historically grouped under surgical specialties.
-- ============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- STEP 1: Ensure all specialties have a default category
-- ─────────────────────────────────────────────────────────────────────────────
-- Some newly added specialties might have missed the V90 backfill.
-- Defaulting missing ones to 'CAT-OUTPAT' if not already set.
UPDATE medical_specialties
SET    category_id = (SELECT id FROM medical_categories WHERE code = 'CAT-OUTPAT' LIMIT 1)
WHERE  category_id IS NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- STEP 2: The Main Backfill (Sweeper)
-- ─────────────────────────────────────────────────────────────────────────────
-- Inserts a primary 'ANY' context link for any service missing a mapping.
INSERT INTO medical_service_categories (service_id, category_id, context, is_primary, active)
SELECT
    ms.id,
    sp.category_id,
    'ANY',
    TRUE,
    TRUE
FROM medical_services ms
JOIN medical_specialties sp ON sp.id = ms.specialty_id
WHERE ms.deleted = FALSE
  AND ms.is_master = TRUE
  AND ms.specialty_id IS NOT NULL
  AND sp.category_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 
      FROM medical_service_categories msc 
      WHERE msc.service_id = ms.id
  );

-- ─────────────────────────────────────────────────────────────────────────────
-- STEP 3: Context-Specific Overrides (Optional/Refinement)
-- ─────────────────────────────────────────────────────────────────────────────
-- If certain services in SP-PLAST (Plastic Surgery) are clearly outpatient
-- (Fillers, etc.), we ensure they have an OUTPATIENT context link too.
-- For now, we stick to the primary ANY link created above, which follows
-- the Specialty -> Category mapping. (SP-PLAST -> CAT-OPER).

-- ─────────────────────────────────────────────────────────────────────────────
-- STEP 4: Double Check SRV-001 (Legacy Fix)
-- ─────────────────────────────────────────────────────────────────────────────
-- Ensure the manual fix for SRV-001 from V90 is respected/reinforced.
UPDATE medical_services
SET    specialty_id = (SELECT id FROM medical_specialties WHERE code = 'SP-NURSING' LIMIT 1)
WHERE  code = 'SRV-001'
  AND  specialty_id IS NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- STEP 5: Integrity Enforcement
-- ─────────────────────────────────────────────────────────────────────────────
-- Ensure all mappings marked as primary for ANY context are active.
UPDATE medical_service_categories
SET active = TRUE
WHERE context = 'ANY' AND is_primary = TRUE AND active = FALSE;
