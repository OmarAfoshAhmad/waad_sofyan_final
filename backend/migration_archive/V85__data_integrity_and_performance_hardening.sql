-- ============================================================
-- V85: Data Integrity & Performance Hardening
-- Env   : PostgreSQL 16
-- Scope : Additive only — no column drops, no renames, no logic changes
-- Date  : 2026-02-18
-- ============================================================
-- Schema findings vs spec adjustments are documented inline.
-- All blocks are idempotent: re-running this migration is safe.
-- ============================================================

-- ============================================================
-- SECTION A — UNIQUE CONSTRAINTS
-- ============================================================

-- ------------------------------------------------------------
-- A1: provider_service_mappings — provider-level deduplication
-- NOTE: The spec referenced columns (provider_id, raw_service_id) that do not
-- exist in this table.  The table has provider_raw_service_id as FK into
-- provider_raw_services, which already carries UNIQUE(provider_id, raw_name).
-- The existing constraint "uq_psm_raw_service" UNIQUE(provider_raw_service_id)
-- is therefore sufficient — one mapping per raw entry is already enforced.
-- ACTION: No new constraint needed.
-- ------------------------------------------------------------

-- ------------------------------------------------------------
-- A2: ent_service_aliases — alias uniqueness
-- NOTE: The spec referenced column alias_name; actual column is alias_text.
-- A global unique on alias_text alone would be overly restrictive (the same
-- alias word may legitimately appear in different locales for different services).
-- The existing "uq_alias_text_per_service_locale" UNIQUE(medical_service_id,
-- alias_text, locale) already prevents exact duplicates per service+locale.
-- ACTION: No new constraint needed.
-- ------------------------------------------------------------

-- ------------------------------------------------------------
-- A3: medical_service_categories — prevent duplicate service+category pairs
-- NOTE: Spec used medical_service_id / medical_category_id; actual columns are
-- service_id / category_id.
-- Existing constraint covers (service_id, context, is_primary) only — does NOT
-- prevent the same (service_id, category_id) combo in different contexts.
-- ------------------------------------------------------------
DO $$
BEGIN
    ALTER TABLE medical_service_categories
        ADD CONSTRAINT uk_msc_service_category
        UNIQUE (service_id, category_id);
    RAISE NOTICE 'Added uk_msc_service_category';
EXCEPTION
    WHEN duplicate_table THEN
        RAISE NOTICE 'uk_msc_service_category already exists, skipping.';
    WHEN duplicate_object THEN
        RAISE NOTICE 'uk_msc_service_category already exists, skipping.';
END;
$$;

-- ------------------------------------------------------------
-- A4: benefit_policy_rules — structural XOR safety
-- NOTE: Spec referenced medical_package_id which does not exist in this table.
-- Adapted constraint: at most ONE of (medical_service_id, medical_category_id)
-- may be non-null per rule.  General rules (both NULL) remain valid.
-- Data pre-check: 0 violations found before applying.
-- ------------------------------------------------------------
DO $$
BEGIN
    ALTER TABLE benefit_policy_rules
        ADD CONSTRAINT chk_bpr_exactly_one_target
        CHECK (
            (medical_service_id IS NOT NULL)::int
          + (medical_category_id IS NOT NULL)::int <= 1
        );
    RAISE NOTICE 'Added chk_bpr_exactly_one_target';
EXCEPTION
    WHEN duplicate_object THEN
        RAISE NOTICE 'chk_bpr_exactly_one_target already exists, skipping.';
END;
$$;

-- ------------------------------------------------------------
-- A5: provider_contracts — enforce pricing_model enum values
-- NOTE: Spec listed FIXED_PRICE; actual Java enum is: FIXED, DISCOUNT,
-- TIERED, NEGOTIATED.  Constraint uses canonical enum values from
-- ProviderContract.PricingModel.  Existing data has only 'FIXED' → valid.
-- ------------------------------------------------------------
DO $$
BEGIN
    ALTER TABLE provider_contracts
        ADD CONSTRAINT chk_pricing_model
        CHECK (pricing_model IN ('FIXED', 'DISCOUNT', 'TIERED', 'NEGOTIATED'));
    RAISE NOTICE 'Added chk_pricing_model';
EXCEPTION
    WHEN duplicate_object THEN
        RAISE NOTICE 'chk_pricing_model already exists, skipping.';
END;
$$;

-- ============================================================
-- SECTION B — INDEX HARDENING
-- ============================================================

-- ------------------------------------------------------------
-- provider_service_mappings — mapping_status filter
-- NOTE: Column is mapping_status (not status). Spec name idx_psm_status adapted.
-- Existing indexes: idx_psm_medical_service (medical_service_id) ✓
-- Existing: uq_psm_raw_service (provider_raw_service_id) ✓
-- SKIP: idx_psm_provider_id — no provider_id column in this table.
-- SKIP: idx_psm_medical_service_id — covered by idx_psm_medical_service.
-- ------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_psm_mapping_status
    ON provider_service_mappings (mapping_status);

-- ------------------------------------------------------------
-- provider_raw_services
-- SKIP: idx_raw_provider_id — already covered by idx_prs_provider.
-- SKIP: idx_raw_status     — already covered by idx_prs_status.
-- ------------------------------------------------------------

-- ------------------------------------------------------------
-- Unified Catalog — medical_services
-- SKIP: idx_medical_services_code     — covered by idx_medical_services_code_runtime (on code).
-- SKIP: idx_medical_services_is_master — already exists with partial WHERE deleted=false.
-- ------------------------------------------------------------

-- ------------------------------------------------------------
-- medical_categories
-- SKIP: idx_medical_categories_parent — covered by idx_medical_categories_parent_id.
-- ------------------------------------------------------------

-- ------------------------------------------------------------
-- Contract Pricing — active contract compound lookup
-- NOTE: Uses active boolean column (distinct from contract_status='ACTIVE').
-- Existing idx_provider_contracts_active uses contract_status; this adds a
-- complementary partial index on the boolean active flag.
-- SKIP: idx_pricing_item_contract — covered by idx_pricing_contract_id (on contract_id).
-- SKIP: idx_pricing_item_service  — covered by idx_pricing_service_id (on medical_service_id).
-- ------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_contract_active_bool
    ON provider_contracts (provider_id, employer_id)
    WHERE active = TRUE;

-- ------------------------------------------------------------
-- Eligibility & Claims Optimization
-- SKIP: idx_claim_lines_service  — covered by idx_claim_line_service (on medical_service_id).
-- SKIP: idx_eligibility_member   — covered by idx_eligibility_member + idx_eligibility_member_id.
-- ADD:  idx_pre_auth_medical_service — no existing index on pre_authorizations(medical_service_id).
-- ------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_pre_auth_medical_service
    ON pre_authorizations (medical_service_id)
    WHERE medical_service_id IS NOT NULL;

-- ============================================================
-- END OF V85
-- ============================================================
