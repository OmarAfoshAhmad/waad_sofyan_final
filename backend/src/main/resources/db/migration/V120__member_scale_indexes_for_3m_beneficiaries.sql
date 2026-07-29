-- Production-scale indexes for the unified members module.
-- Target size: millions of beneficiaries.
--
-- Notes:
-- 1) These indexes are intentionally idempotent.
-- 2) On an already-large production database, create the heaviest GIN indexes
--    during a maintenance window, or manually with CREATE INDEX CONCURRENTLY
--    outside Flyway's transaction.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Exact lookup paths used by eligibility, claims, imports, and provider portal.
CREATE INDEX IF NOT EXISTS idx_members_card_number_upper
    ON members (UPPER(card_number))
    WHERE card_number IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_members_barcode_upper
    ON members (UPPER(barcode))
    WHERE barcode IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_members_national_number_upper
    ON members (UPPER(national_number))
    WHERE national_number IS NOT NULL;

-- Main list/search filters. Keep active/status/employer together because the UI
-- almost always scopes by active state and often by employer.
CREATE INDEX IF NOT EXISTS idx_members_employer_active_status_id
    ON members (employer_id, active, status, id DESC);

CREATE INDEX IF NOT EXISTS idx_members_employer_active_policy_id
    ON members (employer_id, active, benefit_policy_id, id DESC)
    WHERE benefit_policy_id IS NOT NULL;

-- Principal/dependent navigation and family loading.
CREATE INDEX IF NOT EXISTS idx_members_parent_active_relationship
    ON members (parent_id, active, relationship)
    WHERE parent_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_members_employer_active_parent_id
    ON members (employer_id, active, parent_id, id DESC);

-- Dashboard and recent records.
CREATE INDEX IF NOT EXISTS idx_members_active_created_at_desc
    ON members (active, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_members_active_join_date
    ON members (active, join_date);

-- Arabic/English contains and fuzzy search. These support LIKE/ILIKE '%term%'
-- and similarity searches over large datasets.
CREATE INDEX IF NOT EXISTS idx_members_full_name_trgm
    ON members USING GIN (LOWER(full_name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_members_card_number_trgm
    ON members USING GIN (LOWER(card_number) gin_trgm_ops)
    WHERE card_number IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_members_barcode_trgm
    ON members USING GIN (LOWER(barcode) gin_trgm_ops)
    WHERE barcode IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_members_national_number_trgm
    ON members USING GIN (LOWER(national_number) gin_trgm_ops)
    WHERE national_number IS NOT NULL;
