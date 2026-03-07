-- ============================================================================
-- V82: Drop Legacy Medical Catalog & Global Pricing Infrastructure
-- ============================================================================
-- Phase 1 of Unified Medical Catalog migration.
--
-- Drops:
--   1. provider_contract_service_prices  (V4 legacy, superseded by V46)
--   2. provider_service_price_import_logs (V48)
--   3. provider_service_prices            (V3 global pricing — never used at runtime)
--   4. canonical_medical_services         (V3 — merged into medical_services.is_master)
--   5. medical_codes                      (V3 Level-3 taxonomy — superseded by cpt_codes/icd_codes)
--
-- Cleans orphan columns:
--   - canonical_service_id from benefit_policy_rules, claim_lines,
--     preauthorization_requests, medical_package_services
--
-- CASCADE removes all FK constraints referencing the dropped tables.
-- Dev environment — no production data preservation required.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- STEP 1: Drop legacy provider_contract_service_prices
--         (V4 original — superseded by provider_contract_pricing_items in V46)
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS provider_contract_service_prices CASCADE;

-- ----------------------------------------------------------------------------
-- STEP 2: Drop global provider pricing tables
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS provider_service_price_import_logs CASCADE;
DROP TABLE IF EXISTS provider_service_prices CASCADE;

-- ----------------------------------------------------------------------------
-- STEP 3: Drop canonical_medical_services
--         CASCADE removes FK constraints on:
--           - benefit_policy_rules.canonical_service_id
--           - claim_lines.canonical_service_id
--           - preauthorization_requests.canonical_service_id
--           - medical_package_services.canonical_service_id
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS canonical_medical_services CASCADE;

-- ----------------------------------------------------------------------------
-- STEP 4: Drop medical_codes (Level-3 taxonomy, CPT/ICD now in separate tables)
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS medical_codes CASCADE;

-- ----------------------------------------------------------------------------
-- STEP 5: Remove orphan canonical_service_id columns
--         FK constraints already removed by CASCADE above.
--         Dropping columns also drops their associated indexes automatically.
-- ----------------------------------------------------------------------------
ALTER TABLE benefit_policy_rules
    DROP COLUMN IF EXISTS canonical_service_id;

ALTER TABLE claim_lines
    DROP COLUMN IF EXISTS canonical_service_id;

-- V4 legacy pre-auth table (pre_authorizations in V16 never had this column)
ALTER TABLE preauthorization_requests
    DROP COLUMN IF EXISTS canonical_service_id;

ALTER TABLE medical_package_services
    DROP COLUMN IF EXISTS canonical_service_id;

-- ----------------------------------------------------------------------------
-- STEP 6: Drop orphan sequence
-- ----------------------------------------------------------------------------
DROP SEQUENCE IF EXISTS canonical_service_seq;

-- ============================================================================
-- V82 Complete — Legacy medical catalog infrastructure removed.
-- medical_services and medical_categories tables are PRESERVED.
-- Next: V83 will rebuild unified catalog with reference-compatible structure.
-- ============================================================================
