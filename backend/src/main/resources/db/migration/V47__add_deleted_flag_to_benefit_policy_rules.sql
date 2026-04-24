-- Separate soft-delete state from active/inactive status for benefit policy rules.
ALTER TABLE benefit_policy_rules
    ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;

-- Backward compatibility: existing inactive rows were previously treated as "deleted".
UPDATE benefit_policy_rules
SET deleted = TRUE
WHERE active = FALSE
  AND deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_bpr_deleted ON benefit_policy_rules(deleted);
