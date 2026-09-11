CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_members_full_name_canonical_trgm
    ON members USING GIN (waad_search_normalize(full_name) gin_trgm_ops)
    WHERE full_name IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_members_card_number_canonical_trgm
    ON members USING GIN (waad_search_normalize(card_number) gin_trgm_ops)
    WHERE card_number IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_members_barcode_canonical_trgm
    ON members USING GIN (waad_search_normalize(barcode) gin_trgm_ops)
    WHERE barcode IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_members_national_number_canonical_trgm
    ON members USING GIN (waad_search_normalize(national_number) gin_trgm_ops)
    WHERE national_number IS NOT NULL;
