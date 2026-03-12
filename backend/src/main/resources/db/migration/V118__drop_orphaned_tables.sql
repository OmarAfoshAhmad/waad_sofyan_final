-- ============================================================
-- V118: Drop orphaned tables and sequences
-- ============================================================
-- These objects belong to modules that have been fully removed from
-- the application (no Java entity, no controller, no service exists).
--
-- Modules removed:
--   * Provider Service Mapping Center (V031) → 3 tables
--   * Provider Payments (V081 partial) → 1 table (settlement_batch_id
--     column already dropped by V117; table itself still exists)
--
-- Orphaned sequences:
--   * settlement_payment_reference_seq — referenced only by the deleted
--     ProviderPayment payment_reference auto-generation logic.
--     (settlement_batch_seq was already dropped by V117)
-- ============================================================

-- ----------------------------------------------------------
-- 1. Drop Provider Mapping Center tables (V031)
--    Drop in FK-dependency order (children before parent).
-- ----------------------------------------------------------

-- provider_service_mappings FK → provider_raw_services
DROP TABLE IF EXISTS provider_service_mappings CASCADE;

-- provider_mapping_audit    FK → provider_raw_services (nullable, but safer)
DROP TABLE IF EXISTS provider_mapping_audit CASCADE;

-- provider_raw_services — parent of the two above
DROP TABLE IF EXISTS provider_raw_services CASCADE;

-- ----------------------------------------------------------
-- 2. Drop provider_payments (V081)
--    settlement_batch_id column already removed by V117.
--    No Java entity remains (ProviderPaymentController deleted).
-- ----------------------------------------------------------
DROP TABLE IF EXISTS provider_payments CASCADE;

-- ----------------------------------------------------------
-- 3. Drop orphaned sequence
-- ----------------------------------------------------------

-- settlement_payment_reference_seq was used only for payment_reference
-- auto-generation in the deleted ProviderPayment entity.
DROP SEQUENCE IF EXISTS settlement_payment_reference_seq;

-- ============================================================
-- NOTE: The following indexes from V090 already no longer exist
-- because they were auto-dropped when settlement_batches was
-- dropped by V117:
--   idx_settlement_batches_provider_date_status
--   idx_settlement_batch_payment
--   idx_settlements_active
--
-- No action required for them here.
-- ============================================================
