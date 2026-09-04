-- V217 left three columns nullable with no way to tell "this claim's
-- historical context was never resolved and is genuinely unknown" apart
-- from "this claim just happens to have NULLs" -- the same ambiguity a bare
-- NULL always carries. This adds the explicit status the reviewer of
-- CLAIMS_POLICY_SNAPSHOT_BACKFILL_GAPS.md asked for: claims.policy_id and
-- friends answer WHICH policy/assignment; historical_context_status answers
-- WHETHER that answer is trustworthy enough to build a policy lock, a
-- report, or a recalculation on.
--
-- RESOLVED is permanent once entered -- a locked-in fact, mirroring the
-- financial-snapshot-immutability pattern already in this table.
-- LEGACY_UNRESOLVED exists ONLY for rows this migration itself finds
-- genuinely unresolved; nothing written after this migration may ever
-- start there -- see the INSERT guard at the bottom.
ALTER TABLE claims ADD COLUMN historical_context_status VARCHAR(20);

UPDATE claims
   SET historical_context_status = CASE
       WHEN policy_id IS NOT NULL AND policy_assignment_id IS NOT NULL
            AND employer_assignment_id IS NOT NULL THEN 'RESOLVED'
       ELSE 'LEGACY_UNRESOLVED'
   END;

ALTER TABLE claims ALTER COLUMN historical_context_status SET NOT NULL;

ALTER TABLE claims
    ADD CONSTRAINT chk_claims_historical_context_status
        CHECK (historical_context_status IN ('RESOLVED', 'LEGACY_UNRESOLVED'));

-- The status and the three columns must agree: RESOLVED requires all three,
-- LEGACY_UNRESOLVED requires that at least one is actually missing (a claim
-- with a complete snapshot has no honest reason to claim to be unresolved).
ALTER TABLE claims
    ADD CONSTRAINT chk_claims_historical_context_consistency
        CHECK (
            (historical_context_status = 'RESOLVED'
                AND policy_id IS NOT NULL
                AND policy_assignment_id IS NOT NULL
                AND employer_assignment_id IS NOT NULL)
            OR
            (historical_context_status = 'LEGACY_UNRESOLVED'
                AND NOT (policy_id IS NOT NULL AND policy_assignment_id IS NOT NULL
                         AND employer_assignment_id IS NOT NULL))
        );

COMMENT ON COLUMN claims.historical_context_status IS
    'RESOLVED: policy_id/policy_assignment_id/employer_assignment_id are trustworthy and permanent. '
    'LEGACY_UNRESOLVED: pre-V217 claim this migration could not attribute without guessing; may only '
    'transition to RESOLVED via a reviewed, validated correction -- never assigned by new claim creation.';

-- ============================================================
-- Supersedes V217's claims_historical_context_guard: adds the
-- RESOLVED<->LEGACY_UNRESOLVED transition rule and validates a
-- LEGACY_UNRESOLVED -> RESOLVED correction's consistency at the moment it
-- happens, since the CHECK constraint above can express "all three present"
-- but not "these three actually belong together and cover service_date".
-- ============================================================
CREATE OR REPLACE FUNCTION claims_historical_context_guard()
RETURNS trigger AS $$
DECLARE
    pa_policy_id BIGINT;
    pa_member_id BIGINT;
    ea_member_id BIGINT;
    ea_employer_id BIGINT;
    policy_employer_id BIGINT;
BEGIN
    IF OLD.historical_context_status = 'RESOLVED' THEN
        -- Permanent: neither the status nor any of the three columns may
        -- change again, regardless of claim workflow status.
        IF NEW.historical_context_status IS DISTINCT FROM 'RESOLVED'
           OR NEW.policy_id IS DISTINCT FROM OLD.policy_id
           OR NEW.policy_assignment_id IS DISTINCT FROM OLD.policy_assignment_id
           OR NEW.employer_assignment_id IS DISTINCT FROM OLD.employer_assignment_id THEN
            RAISE EXCEPTION 'claims: historical context is permanent once RESOLVED (claim %)', OLD.id;
        END IF;
        RETURN NEW;
    END IF;

    -- OLD.historical_context_status = 'LEGACY_UNRESOLVED' from here on.
    IF NEW.historical_context_status = 'RESOLVED' THEN
        IF NEW.policy_id IS NULL OR NEW.policy_assignment_id IS NULL
           OR NEW.employer_assignment_id IS NULL THEN
            RAISE EXCEPTION 'claims: cannot resolve without all three historical context columns (claim %)',
                NEW.id;
        END IF;

        SELECT policy_id, member_id INTO pa_policy_id, pa_member_id
          FROM member_policy_assignments WHERE id = NEW.policy_assignment_id;
        SELECT member_id, employer_id INTO ea_member_id, ea_employer_id
          FROM member_employer_assignments WHERE id = NEW.employer_assignment_id;
        SELECT employer_id INTO policy_employer_id
          FROM benefit_policies WHERE id = NEW.policy_id;

        IF pa_policy_id IS DISTINCT FROM NEW.policy_id THEN
            RAISE EXCEPTION 'claims: policy_assignment_id % does not belong to policy_id % (claim %)',
                NEW.policy_assignment_id, NEW.policy_id, NEW.id;
        END IF;
        IF pa_member_id IS DISTINCT FROM NEW.member_id OR ea_member_id IS DISTINCT FROM NEW.member_id THEN
            RAISE EXCEPTION 'claims: assignment does not belong to this claim''s member (claim %)', NEW.id;
        END IF;
        IF ea_employer_id IS DISTINCT FROM policy_employer_id THEN
            RAISE EXCEPTION 'claims: employer_assignment_id names a different employer than policy_id (claim %)',
                NEW.id;
        END IF;
        IF NOT EXISTS (
            SELECT 1 FROM member_policy_assignments
             WHERE id = NEW.policy_assignment_id
               AND assignment_start_date <= NEW.service_date
               AND (assignment_end_date IS NULL OR assignment_end_date > NEW.service_date)
        ) THEN
            RAISE EXCEPTION 'claims: policy_assignment_id % does not cover service_date (claim %)',
                NEW.policy_assignment_id, NEW.id;
        END IF;
        IF NOT EXISTS (
            SELECT 1 FROM member_employer_assignments
             WHERE id = NEW.employer_assignment_id
               AND assignment_start_date <= NEW.service_date
               AND (assignment_end_date IS NULL OR assignment_end_date > NEW.service_date)
        ) THEN
            RAISE EXCEPTION 'claims: employer_assignment_id % does not cover service_date (claim %)',
                NEW.employer_assignment_id, NEW.id;
        END IF;
        RETURN NEW;
    END IF;

    -- Still LEGACY_UNRESOLVED -> LEGACY_UNRESOLVED: no partial edits, ever.
    -- The documented model has exactly one legal transition,
    -- LEGACY_UNRESOLVED -> RESOLVED, applied atomically with the full
    -- consistency check above. Allowing the three columns to drift while
    -- the row stays LEGACY_UNRESOLVED (as V217's original DRAFT/
    -- NEEDS_CORRECTION carve-out did) would let a claim's historical
    -- attribution be edited piecemeal, unvalidated, and never actually
    -- resolved -- exactly the ambiguity this status exists to name and
    -- close in one reviewed step.
    IF NEW.policy_id IS DISTINCT FROM OLD.policy_id
       OR NEW.policy_assignment_id IS DISTINCT FROM OLD.policy_assignment_id
       OR NEW.employer_assignment_id IS DISTINCT FROM OLD.employer_assignment_id THEN
        RAISE EXCEPTION
            'claims: a LEGACY_UNRESOLVED claim''s policy_id/policy_assignment_id/employer_assignment_id may only change as part of a single, validated transition to RESOLVED (claim %)',
            OLD.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- V217 already created trg_claims_historical_context_guard BEFORE UPDATE
-- pointing at this function name; CREATE OR REPLACE above is enough to
-- change its behavior without touching the trigger itself.

-- No INSERT may ever start at LEGACY_UNRESOLVED: that value exists solely
-- to describe rows this migration found already broken. A new claim's
-- context is always resolved by MemberContextResolver#resolveForOrFail
-- before ClaimMapper builds it (fail-closed, throws otherwise) -- so a
-- fresh INSERT claiming LEGACY_UNRESOLVED can only mean the application
-- layer was bypassed or changed incorrectly, and must be refused here too,
-- not trusted to stay correct by convention alone.
CREATE OR REPLACE FUNCTION claims_reject_new_legacy_unresolved()
RETURNS trigger AS $$
BEGIN
    IF NEW.historical_context_status = 'LEGACY_UNRESOLVED' THEN
        RAISE EXCEPTION
            'claims: LEGACY_UNRESOLVED may not be used by a new claim -- it is reserved for rows the V219 migration found already unresolved (service_date %)',
            NEW.service_date;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_claims_reject_new_legacy_unresolved ON claims;
CREATE TRIGGER trg_claims_reject_new_legacy_unresolved
BEFORE INSERT ON claims
FOR EACH ROW EXECUTE FUNCTION claims_reject_new_legacy_unresolved();
