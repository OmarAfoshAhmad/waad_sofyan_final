-- ============================================================
-- V36: Add sub_category_name and specialty columns to provider_contract_pricing_items
-- Required for enhanced Excel price-list import with category resolution
-- ============================================================

ALTER TABLE provider_contract_pricing_items
    ADD COLUMN IF NOT EXISTS sub_category_name VARCHAR(255);

ALTER TABLE provider_contract_pricing_items
    ADD COLUMN IF NOT EXISTS specialty VARCHAR(255);

