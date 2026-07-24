-- ============================================================
-- V108: Fix benefit_policy_rules schema validation
-- Add missing benefit_policy_id column if it doesn't exist
-- ============================================================

-- Check and add benefit_policy_id column if missing
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'benefit_policy_rules'
        AND column_name = 'benefit_policy_id'
    ) THEN
        ALTER TABLE benefit_policy_rules
        ADD COLUMN benefit_policy_id BIGINT NOT NULL DEFAULT 0;

        -- Add foreign key constraint if it doesn't exist
        ALTER TABLE benefit_policy_rules
        ADD CONSTRAINT fk_rule_policy_id FOREIGN KEY (benefit_policy_id)
            REFERENCES benefit_policies(id) ON DELETE CASCADE;

        -- Add index for performance
        CREATE INDEX IF NOT EXISTS idx_bpr_policy_id ON benefit_policy_rules(benefit_policy_id);
    END IF;
END $$;

-- Ensure all NOT NULL constraints are properly set
DO $$
BEGIN
    -- Update any NULL values to 0 (safe default)
    UPDATE benefit_policy_rules SET benefit_policy_id = 0 WHERE benefit_policy_id IS NULL;
END $$;

-- Drop duplicate constraint if exists
DO $$
BEGIN
    BEGIN
        ALTER TABLE benefit_policy_rules DROP CONSTRAINT fk_rule_policy;
    EXCEPTION WHEN others THEN
        NULL;
    END;
END $$;

-- Ensure the correct constraint exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'benefit_policy_rules'
        AND constraint_name = 'fk_rule_policy_id'
    ) THEN
        ALTER TABLE benefit_policy_rules
        ADD CONSTRAINT fk_rule_policy_id FOREIGN KEY (benefit_policy_id)
            REFERENCES benefit_policies(id) ON DELETE CASCADE;
    END IF;
END $$;

-- Log completion
SELECT 'V108: benefit_policy_rules schema fixed' as status;
