-- ============================================================================
-- V14: Align members table with Member.benefitPolicy mapping
-- ============================================================================
-- Adds members.benefit_policy_id expected by Member entity and claim/dashboard joins.

ALTER TABLE members
    ADD COLUMN IF NOT EXISTS benefit_policy_id BIGINT;

-- Backfill from member_policy_assignments (latest active assignment per member if available)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'member_policy_assignments'
    ) THEN
        UPDATE members m
        SET benefit_policy_id = src.policy_id
        FROM (
            SELECT DISTINCT ON (mpa.member_id)
                mpa.member_id,
                mpa.policy_id
            FROM member_policy_assignments mpa
            WHERE mpa.policy_id IS NOT NULL
              AND (mpa.assignment_end_date IS NULL OR mpa.assignment_end_date >= CURRENT_DATE)
            ORDER BY mpa.member_id, mpa.assignment_start_date DESC NULLS LAST, mpa.id DESC
        ) src
        WHERE m.id = src.member_id
          AND m.benefit_policy_id IS NULL;
    END IF;
END $$;

ALTER TABLE members
    ADD CONSTRAINT fk_members_benefit_policy
    FOREIGN KEY (benefit_policy_id) REFERENCES benefit_policies(id)
    ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_members_benefit_policy_id ON members(benefit_policy_id);

-- ============================================================================
-- Migration Complete: V14
-- ============================================================================
