-- Links special-expense definitions (work injury, evacuation, companion, etc.)
-- to the same policy group/bucket model used by medical coverage limits.
ALTER TABLE benefit_groups
    ADD COLUMN IF NOT EXISTS benefit_definition_id BIGINT
        REFERENCES benefit_definitions(id) ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_benefit_group_definition
    ON benefit_groups(policy_id, benefit_definition_id)
    WHERE benefit_definition_id IS NOT NULL;
