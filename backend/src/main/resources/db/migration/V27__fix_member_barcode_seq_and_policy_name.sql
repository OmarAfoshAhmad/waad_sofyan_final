-- ============================================================
-- V27: Two fixes that V26 did NOT apply because it was already
--      marked as "success" in flyway_schema_history with old
--      content and validate-on-migrate=false ignores the change.
--
-- FIX 1 — member_barcode_seq
--   The sequence exists in V1 but only for databases initialized
--   after that line was added. Older databases still lack it,
--   causing every member import to fail with:
--     "relation member_barcode_seq does not exist"
--
-- FIX 2 — benefit_policies.policy_name
--   V26 (old content) only made policy_name nullable.
--   This migration completes the cleanup: drops the column
--   entirely and enforces NOT NULL on the 'name' column that
--   the Hibernate entity actually maps.
-- ============================================================

-- ── FIX 1: member_barcode_seq ─────────────────────────────────

CREATE SEQUENCE IF NOT EXISTS member_barcode_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- ── FIX 2: benefit_policies column cleanup ────────────────────

-- Drop the unused legacy column (V26 only made it nullable)
ALTER TABLE benefit_policies DROP COLUMN IF EXISTS policy_name;

-- Patch any rows that slipped through with a null/empty name
UPDATE benefit_policies
    SET name = 'وثيقة بدون اسم'
    WHERE name IS NULL OR trim(name) = '';

-- Enforce NOT NULL to match @Column(nullable=false) in the entity
ALTER TABLE benefit_policies ALTER COLUMN name SET NOT NULL;
