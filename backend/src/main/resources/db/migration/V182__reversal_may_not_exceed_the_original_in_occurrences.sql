-- ============================================================
-- V182: the occurrence dimension gets the ceiling the amount already had.
--
-- V174's validate_bucket_consumption_reversal caps compensating movements
-- against the original's approved_amount:
--
--     already_reversed + NEW.approved_amount > original.approved_amount  -> reject
--
-- times_consumed had no such rule. A ledger that refuses to give back more
-- money than it holds, while allowing more visits to be returned than were
-- ever taken, is only half a ledger -- and the occurrence side is where the
-- application code was found releasing gross instead of outstanding, so the
-- gap was reachable rather than theoretical.
--
-- Same shape, same lock, same message style as the amount rule, because it is
-- the same rule in the other unit.
-- ============================================================

CREATE OR REPLACE FUNCTION validate_bucket_consumption_reversal()
RETURNS trigger AS $$
DECLARE
    original benefit_bucket_consumptions%ROWTYPE;
    already_reversed NUMERIC(15,2);
    already_reversed_times INTEGER;
BEGIN
    IF NEW.status <> 'REVERSED' THEN
        RETURN NEW;
    END IF;

    IF NEW.reversal_of_id IS NULL THEN
        RAISE EXCEPTION 'A REVERSED row must name the movement it compensates (reversal_of_id is null)';
    END IF;

    SELECT * INTO original FROM benefit_bucket_consumptions WHERE id = NEW.reversal_of_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Reversal target % does not exist', NEW.reversal_of_id;
    END IF;

    IF original.status = 'REVERSED' THEN
        RAISE EXCEPTION 'A compensating movement cannot itself be reversed (target %)', NEW.reversal_of_id;
    END IF;

    IF original.member_id IS DISTINCT FROM NEW.member_id
       OR original.policy_id IS DISTINCT FROM NEW.policy_id
       OR original.bucket_id IS DISTINCT FROM NEW.bucket_id
       OR original.period_start IS DISTINCT FROM NEW.period_start
       OR original.period_end IS DISTINCT FROM NEW.period_end
       OR original.limit_scope IS DISTINCT FROM NEW.limit_scope
       OR original.source_type IS DISTINCT FROM NEW.source_type THEN
        RAISE EXCEPTION 'A compensating movement must describe the same member, policy, scope, source and period as its original (target %)',
            NEW.reversal_of_id;
    END IF;

    -- FOR UPDATE above serializes concurrent reversals of the same original,
    -- so neither total can be raced past the original.
    SELECT COALESCE(SUM(approved_amount), 0), COALESCE(SUM(times_consumed), 0)
      INTO already_reversed, already_reversed_times
    FROM benefit_bucket_consumptions
    WHERE reversal_of_id = NEW.reversal_of_id AND status = 'REVERSED';

    IF already_reversed + NEW.approved_amount > original.approved_amount THEN
        RAISE EXCEPTION 'Reversing % would exceed the original amount % (already reversed %)',
            NEW.approved_amount, original.approved_amount, already_reversed;
    END IF;

    -- The same ceiling in occurrences. Giving back more visits than were
    -- taken would free ceiling the member never used, and the error would
    -- surface only as a limit that quietly grew.
    IF already_reversed_times + COALESCE(NEW.times_consumed, 0)
            > COALESCE(original.times_consumed, 0) THEN
        RAISE EXCEPTION 'Reversing % occurrence(s) would exceed the original count % (already reversed %)',
            COALESCE(NEW.times_consumed, 0), COALESCE(original.times_consumed, 0),
            already_reversed_times;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
