-- Prevent silent lost updates when two users edit/activate/suspend/terminate the same
-- provider contract concurrently (e.g. during a bulk action while someone else edits it).
ALTER TABLE provider_contracts
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
