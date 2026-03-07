-- ============================================================
-- V096: Add missing columns that were added to entities/V0xx
--       after the DB was already initialized.
--
-- All statements use IF NOT EXISTS so this migration is safe
-- to run even if the columns were later added manually.
-- ============================================================

-- 1. benefit_policies.out_of_pocket_max
--    Added to Claim / cost-calculation logic but missing from DB if V040
--    was applied before the column was added to the CREATE TABLE statement.
ALTER TABLE benefit_policies
    ADD COLUMN IF NOT EXISTS out_of_pocket_max DECIMAL(15, 2) DEFAULT 0.00;

-- 2. claims.active
--    Present in Claim.java entity (@Column name = "active") but never
--    included in V070__schema_claims.sql.
ALTER TABLE claims
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT true;

-- 3. members.active
--    Present in Member.java entity and in V050, but guard in case the
--    DB was initialised from an older version of V050.
ALTER TABLE members
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT true;
