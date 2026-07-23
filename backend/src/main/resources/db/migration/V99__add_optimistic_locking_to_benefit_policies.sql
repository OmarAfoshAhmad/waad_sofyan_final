-- Prevent silent lost updates when two users edit the same policy or rule.
ALTER TABLE benefit_policies
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE benefit_policy_rules
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
