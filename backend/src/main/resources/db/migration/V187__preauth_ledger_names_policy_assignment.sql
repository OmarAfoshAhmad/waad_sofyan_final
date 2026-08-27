-- A conversion is a fourth way a hold ends, and the ledger has to be able to
-- say so. V173 fixed the vocabulary of reversal_reason to five values; adding
-- PREAUTH_CONVERSION_RELEASE to the Java enum alone would leave every
-- pre-authorized claim failing its CHECK at the moment it posts.
ALTER TABLE benefit_bucket_consumptions
    DROP CONSTRAINT chk_bucket_consumption_reversal_reason;

ALTER TABLE benefit_bucket_consumptions
    ADD CONSTRAINT chk_bucket_consumption_reversal_reason
        CHECK (reversal_reason IS NULL OR reversal_reason IN
            ('PREAUTH_RELEASE', 'PREAUTH_EXPIRY', 'PREAUTH_CANCELLATION',
             'PREAUTH_CONVERSION_RELEASE', 'CLAIM_REVERSAL', 'CLAIM_CORRECTION'));

-- A reservation is a dated promise. preauth_id alone implies an assignment
-- only by application convention; record the exact enrollment period so a
-- backdated conversion cannot reclaim a hold from another assignment.
--
-- The FK is not decoration: without it the column can name an assignment that
-- was never written, and the value that distinguishes two enrollment periods
-- would be unverifiable exactly when it matters.
ALTER TABLE benefit_bucket_consumptions
    ADD COLUMN member_policy_assignment_id BIGINT
        REFERENCES member_policy_assignments(id) ON DELETE RESTRICT;

DO $$
DECLARE bad_count BIGINT;
BEGIN
    SELECT count(*) INTO bad_count
      FROM benefit_bucket_consumptions c
     WHERE c.source_type = 'PREAUTH'
       AND c.status = 'RESERVED'
       AND (SELECT count(DISTINCT s.member_policy_assignment_id)
              FROM preauth_decision_snapshots s
             WHERE s.preauth_id = c.preauth_id
               AND s.member_policy_assignment_id IS NOT NULL) <> 1;
    IF bad_count > 0 THEN
        RAISE EXCEPTION 'V187 cannot identify one policy assignment for % existing PREAUTH reservation(s)', bad_count;
    END IF;
END $$;

-- The ledger is append-only. This is metadata backfill only; suspend the
-- UPDATE guard for these two bounded statements and prove it is restored.
ALTER TABLE benefit_bucket_consumptions DISABLE TRIGGER trg_no_update_bucket_consumptions;

UPDATE benefit_bucket_consumptions c
   SET member_policy_assignment_id = (
       SELECT min(s.member_policy_assignment_id)
         FROM preauth_decision_snapshots s
        WHERE s.preauth_id = c.preauth_id)
 WHERE c.source_type = 'PREAUTH' AND c.status = 'RESERVED';

UPDATE benefit_bucket_consumptions r
   SET member_policy_assignment_id = o.member_policy_assignment_id
  FROM benefit_bucket_consumptions o
 WHERE r.reversal_of_id = o.id AND r.source_type = 'PREAUTH';

ALTER TABLE benefit_bucket_consumptions ENABLE TRIGGER trg_no_update_bucket_consumptions;

ALTER TABLE benefit_bucket_consumptions
    ADD CONSTRAINT chk_preauth_consumption_has_policy_assignment
    CHECK (source_type <> 'PREAUTH' OR member_policy_assignment_id IS NOT NULL);

CREATE INDEX idx_bucket_consumption_own_reservation
    ON benefit_bucket_consumptions
       (preauth_id, member_id, member_policy_assignment_id, limit_scope,
        bucket_id, period_start, period_end)
    WHERE source_type = 'PREAUTH';

-- An assignment belonging to somebody else would satisfy both the FK and the
-- NOT NULL check while making the column meaningless: it is supposed to name
-- WHICH of this member's enrollment periods the hold sits in.
CREATE OR REPLACE FUNCTION validate_consumption_assignment_owner()
RETURNS trigger AS $$
DECLARE assignment_member BIGINT;
BEGIN
    IF NEW.member_policy_assignment_id IS NOT NULL THEN
        SELECT member_id INTO assignment_member
          FROM member_policy_assignments WHERE id = NEW.member_policy_assignment_id;
        IF assignment_member IS DISTINCT FROM NEW.member_id THEN
            RAISE EXCEPTION 'A consumption may only name its own member''s policy assignment (member %, assignment %)',
                NEW.member_id, NEW.member_policy_assignment_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validate_consumption_assignment_owner
BEFORE INSERT ON benefit_bucket_consumptions
FOR EACH ROW EXECUTE FUNCTION validate_consumption_assignment_owner();

CREATE OR REPLACE FUNCTION validate_preauth_reversal_assignment()
RETURNS trigger AS $$
DECLARE original_assignment BIGINT;
BEGIN
    IF NEW.status = 'REVERSED' AND NEW.source_type = 'PREAUTH' THEN
        SELECT member_policy_assignment_id INTO original_assignment
          FROM benefit_bucket_consumptions WHERE id = NEW.reversal_of_id;
        IF original_assignment IS DISTINCT FROM NEW.member_policy_assignment_id THEN
            RAISE EXCEPTION 'A PREAUTH release must keep the policy assignment of its reservation (target %)',
                NEW.reversal_of_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validate_preauth_reversal_assignment
BEFORE INSERT ON benefit_bucket_consumptions
FOR EACH ROW EXECUTE FUNCTION validate_preauth_reversal_assignment();

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
         WHERE tgrelid = 'benefit_bucket_consumptions'::regclass
           AND tgname = 'trg_no_update_bucket_consumptions'
           AND tgenabled <> 'D') THEN
        RAISE EXCEPTION 'V187 failed to restore the append-only UPDATE guard';
    END IF;
END $$;
