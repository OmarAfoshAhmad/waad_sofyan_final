-- Preserve append-only member status history after a physical member delete.
-- V169 used ON DELETE CASCADE while also rejecting DELETE on the history
-- table; the two rules made hard-delete impossible after one transition.

ALTER TABLE member_status_history
    ADD COLUMN IF NOT EXISTS member_full_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS member_card_number VARCHAR(50);

UPDATE member_status_history h
SET member_full_name = m.full_name,
    member_card_number = m.card_number
FROM members m
WHERE m.id = h.member_id
  AND (h.member_full_name IS NULL OR h.member_card_number IS NULL);

DO $$
DECLARE
    fk_name text;
BEGIN
    SELECT c.conname INTO fk_name
    FROM pg_constraint c
    JOIN pg_class t ON t.oid = c.conrelid
    JOIN pg_namespace n ON n.oid = t.relnamespace
    WHERE t.relname = 'member_status_history'
      AND n.nspname = 'public'
      AND c.contype = 'f'
      AND pg_get_constraintdef(c.oid) LIKE 'FOREIGN KEY (member_id)%'
    LIMIT 1;

    IF fk_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE member_status_history DROP CONSTRAINT %I', fk_name);
    END IF;
END $$;

COMMENT ON COLUMN member_status_history.member_id IS
    'Immutable identifier snapshot; deliberately has no FK so audit history survives hard delete';
