-- Prevent imports, maintenance scripts, and direct SQL from creating an active
-- member without a benefit policy. Inactive legacy records remain permitted.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_active_member_requires_policy'
          AND conrelid = 'members'::regclass
    ) THEN
        ALTER TABLE members
            ADD CONSTRAINT chk_active_member_requires_policy
            CHECK (active IS NOT TRUE OR benefit_policy_id IS NOT NULL)
            NOT VALID;
    END IF;
END $$;

ALTER TABLE members
    VALIDATE CONSTRAINT chk_active_member_requires_policy;
