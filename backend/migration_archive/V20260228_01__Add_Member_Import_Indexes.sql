-- ============================================================
-- V20260228_01: Member Import Optimization
-- Index for case-insensitive name matching
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_members_full_name_lower ON members(LOWER(full_name));

-- Ensure unique constraints have underlying indexes (standard in Postgres, but explicit for clarity)
CREATE INDEX IF NOT EXISTS idx_members_barcode_lookup ON members(barcode) WHERE barcode IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_members_card_number_lookup ON members(card_number);
