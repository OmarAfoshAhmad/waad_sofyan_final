-- ============================================================================
-- V108: Enhance Provider Raw Services Metadata
-- ============================================================================
-- Purpose: 
--   Add columns to store raw classification/specialty data supplied by 
--   providers during price list imports. This aids in manual mapping.
-- ============================================================================

ALTER TABLE provider_raw_services 
    ADD COLUMN IF NOT EXISTS provider_category VARCHAR(255),
    ADD COLUMN IF NOT EXISTS provider_specialty VARCHAR(255);

COMMENT ON COLUMN provider_raw_services.provider_category IS 'Raw classification/category string from provider Excel';
COMMENT ON COLUMN provider_raw_services.provider_specialty IS 'Raw specialty string from provider Excel';
