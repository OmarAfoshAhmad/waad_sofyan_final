-- V103: Ensure benefit_policy_id exists in benefit_policy_rules
-- Renames policy_id -> benefit_policy_id only if benefit_policy_id does not yet exist

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'benefit_policy_rules' AND column_name = 'benefit_policy_id'
    ) THEN
        ALTER TABLE benefit_policy_rules RENAME COLUMN policy_id TO benefit_policy_id;
    END IF;
END $$;
