-- ============================================================
-- V26: Two fixes:
--
-- FIX 1 — benefit_policies: adopt 'name' as the authoritative
--   policy name column and remove the unused 'policy_name'.
--   Root cause in V11: 'policy_name' NOT NULL but entity only
--   writes to 'name', so every INSERT fails.
--
-- FIX 2 — member_barcode_seq missing:
--   V1 defines this sequence but was applied to databases that
--   predate that line. Flyway never re-runs V1 so the sequence
--   is absent, causing every member import to fail with
--   "relation member_barcode_seq does not exist".
-- ============================================================

-- ── FIX 1: benefit_policies ──────────────────────────────────

-- 1a. Remove legacy unused column
ALTER TABLE benefit_policies DROP COLUMN IF EXISTS policy_name;

-- 1b. Patch any existing rows that have a null/empty name
UPDATE benefit_policies
    SET name = 'وثيقة بدون اسم'
    WHERE name IS NULL OR trim(name) = '';

-- 1c. Enforce NOT NULL on 'name' to match @Column(nullable=false)
ALTER TABLE benefit_policies ALTER COLUMN name SET NOT NULL;

-- 1d. Make effective_date nullable (entity does not map this field)
ALTER TABLE benefit_policies ALTER COLUMN effective_date DROP NOT NULL;

-- ── FIX 2: member_barcode_seq ─────────────────────────────────

-- Create the sequence if it was missing from the live database.
-- IF NOT EXISTS guarantees this is a no-op on databases that
-- already have it (applied V1 with the sequence present).
CREATE SEQUENCE IF NOT EXISTS member_barcode_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
