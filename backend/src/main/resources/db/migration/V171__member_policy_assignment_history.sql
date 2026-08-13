-- ============================================================
-- V171: make member policy assignment temporal and auditable.
--
-- member_policy_assignments has existed since V13 with the right shape
-- (member, policy, start date, nullable end date) but was completely dead:
-- the only reference to it anywhere in main/java was a DELETE statement in
-- the member hard-delete path. Nothing ever wrote an assignment, and
-- nothing read one.
--
-- Meanwhile BOTH policy consumers resolved the member's policy from the
-- single mutable pointer members.benefit_policy_id:
--   - EligibilityEngineServiceImpl.buildContext: member.getBenefitPolicy(),
--     with no reference at all to the request's serviceDate.
--   - BenefitPolicyCoverageService.validateMemberHasActivePolicy: the same
--     pointer, falling back to a serviceDate lookup ONLY when the pointer
--     was null -- and then silently PERSISTING that lookup's result onto
--     the member (a read path writing state), including a "latest active
--     policy" guess for internal staff.
--
-- So a backdated eligibility check or claim was evaluated against TODAY's
-- policy, and processing an old claim for a policy-less member could
-- permanently repoint that member at a past policy. This violates
-- "time-based selection by service date" and "single source of truth".
--
-- From here: this table is the record of which policy applied to a member
-- over which period, ranges are half-open [start, end) matching the
-- provider-contract-price convention (V156), overlaps are impossible by
-- construction, and rows are never deleted or rewritten (only closed).
-- ============================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE member_policy_assignments
    ADD COLUMN IF NOT EXISTS assignment_reason  VARCHAR(500),
    ADD COLUMN IF NOT EXISTS assignment_source  VARCHAR(30),
    ADD COLUMN IF NOT EXISTS assigned_by        BIGINT,
    ADD COLUMN IF NOT EXISTS member_full_name   VARCHAR(200),
    ADD COLUMN IF NOT EXISTS member_card_number VARCHAR(50);

-- Identity snapshot + no member FK, for the same reason V170 applied to
-- member_status_history: an assignment record must survive the physical
-- deletion of the member it describes. A RESTRICT FK plus a
-- never-delete rule would otherwise make hard delete impossible for any
-- member who ever had a policy -- exactly the deadlock V169 created and
-- V170 had to undo.
UPDATE member_policy_assignments a
SET member_full_name = m.full_name,
    member_card_number = m.card_number
FROM members m
WHERE m.id = a.member_id
  AND (a.member_full_name IS NULL OR a.member_card_number IS NULL);

DO $$
DECLARE
    fk_name text;
BEGIN
    SELECT c.conname INTO fk_name
    FROM pg_constraint c
    JOIN pg_class t ON t.oid = c.conrelid
    JOIN pg_namespace n ON n.oid = t.relnamespace
    WHERE t.relname = 'member_policy_assignments'
      AND n.nspname = 'public'
      AND c.contype = 'f'
      AND pg_get_constraintdef(c.oid) LIKE 'FOREIGN KEY (member_id)%'
    LIMIT 1;

    IF fk_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE member_policy_assignments DROP CONSTRAINT %I', fk_name);
    END IF;
END $$;

COMMENT ON COLUMN member_policy_assignments.member_id IS
    'Immutable identifier snapshot; deliberately has no FK so assignment history survives hard delete';

-- Half-open [start, end): an end equal to the start is an empty period and
-- is not a valid assignment. V13's original CHECK allowed end = start,
-- which only makes sense for an inclusive-end model.
ALTER TABLE member_policy_assignments DROP CONSTRAINT IF EXISTS chk_assignment_dates;
ALTER TABLE member_policy_assignments
    ADD CONSTRAINT chk_assignment_dates_half_open
    CHECK (assignment_end_date IS NULL OR assignment_end_date > assignment_start_date);

ALTER TABLE member_policy_assignments
    ADD CONSTRAINT chk_assignment_source
    CHECK (assignment_source IS NULL OR assignment_source IN
        ('MANUAL', 'IMPORT', 'BACKFILL', 'EMPLOYER_DEFAULT', 'SYSTEM'));

-- Backfill one open-ended assignment per member that currently points at a
-- policy. The start date is INFERRED, not recorded: no history of policy
-- changes was ever kept, so the earliest date we can honestly claim is the
-- member's own start/creation date. assignment_source='BACKFILL' marks
-- every such row as inferred rather than observed -- a resolution that
-- lands on a BACKFILL row is answering "as far as we know", and callers
-- that need certainty must treat it accordingly.
INSERT INTO member_policy_assignments (
    member_id, policy_id, assignment_start_date, assignment_end_date,
    assignment_reason, assignment_source, member_full_name, member_card_number, created_at)
SELECT m.id, m.benefit_policy_id,
       COALESCE(m.start_date, m.created_at::date, DATE '1900-01-01'),
       NULL,
       'ترحيل تلقائي: لم يكن هناك سجل تعيينات قبل V171',
       'BACKFILL', m.full_name, m.card_number, now()
FROM members m
WHERE m.benefit_policy_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM member_policy_assignments a WHERE a.member_id = m.id);

-- No two assignments for the same member may cover the same day.
ALTER TABLE member_policy_assignments
    ADD CONSTRAINT uk_member_policy_assignment_no_overlap
    EXCLUDE USING gist (
        member_id WITH =,
        daterange(assignment_start_date, assignment_end_date, '[)') WITH &&
    );

CREATE INDEX IF NOT EXISTS idx_policy_assignments_member_start
    ON member_policy_assignments(member_id, assignment_start_date DESC);

-- Append-only with controlled closure: a row is never deleted, and never
-- rewritten except to close it (set assignment_end_date). Anything else --
-- repointing an assignment at a different policy, moving its start date,
-- reassigning it to another member -- would silently rewrite what was
-- already used to adjudicate real claims.
CREATE OR REPLACE FUNCTION member_policy_assignment_guard()
RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'member_policy_assignments is append-only: DELETE is not allowed';
    END IF;

    IF NEW.member_id IS DISTINCT FROM OLD.member_id
       OR NEW.policy_id IS DISTINCT FROM OLD.policy_id
       OR NEW.assignment_start_date IS DISTINCT FROM OLD.assignment_start_date THEN
        RAISE EXCEPTION 'member_policy_assignments: only assignment_end_date may be updated';
    END IF;

    IF OLD.assignment_end_date IS NOT NULL
       AND NEW.assignment_end_date IS DISTINCT FROM OLD.assignment_end_date THEN
        RAISE EXCEPTION 'member_policy_assignments: an already-closed assignment cannot be re-dated';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_member_policy_assignment_no_delete ON member_policy_assignments;
CREATE TRIGGER trg_member_policy_assignment_no_delete
BEFORE DELETE ON member_policy_assignments
FOR EACH ROW EXECUTE FUNCTION member_policy_assignment_guard();

DROP TRIGGER IF EXISTS trg_member_policy_assignment_update_guard ON member_policy_assignments;
CREATE TRIGGER trg_member_policy_assignment_update_guard
BEFORE UPDATE ON member_policy_assignments
FOR EACH ROW EXECUTE FUNCTION member_policy_assignment_guard();
