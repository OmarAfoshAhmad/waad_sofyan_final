-- ============================================================
-- The policy's general annual ceiling gets its own ledger rows.
--
-- Until now its two halves came from two places: what a member had SPENT was
-- summed out of claim_lines, while what was HELD against the same ceiling
-- lived in this table. One subtraction, two sources, and only one of them the
-- ledger.
--
-- The gap between them had a shape: anything general that is not a claim
-- could not be expressed in the claim-table half at all. An imported opening
-- balance is exactly that, which is why the old import had to fabricate a
-- claim to carry it -- putting rows in the claims table for services nobody
-- rendered. V173 gave that movement an honest source_type; this gives it
-- somewhere to be counted.
--
-- ORDER MATTERS. The backfill runs while the application still reads
-- claim_lines, so it can be verified against the very numbers it must
-- reproduce. Only after the parity block passes does the read switch over --
-- and if it does not pass, deployment stops here rather than silently
-- changing every member's remaining balance.
-- ============================================================

-- One row per claim line that consumed the general ceiling, mirroring exactly
-- what commitClaim now writes: same idempotency key, same calendar-year
-- window, same amount.
--
-- The policy comes from the member's assignment covering the service date --
-- the same resolution the claim path itself uses -- and NOT from the line's
-- bucket rows. Reading it from a bucket row would silently require one to
-- exist, and a line can legitimately consume the general ceiling while
-- consuming no bucket at all: its rule may map only to the legacy mirror
-- bucket, or to buckets that are all inactive, or to none. commitClaim posts
-- the general movement before it walks any bucket precisely because the two
-- are independent, and a backfill that coupled them would leave exactly those
-- lines' consumption unrepresented -- handing their members back limit they
-- had already spent.
INSERT INTO benefit_bucket_consumptions (
    policy_id, member_id, bucket_id, claim_id, claim_line_id,
    source_type, limit_scope, status,
    period_start, period_end, approved_amount, times_consumed,
    calculation_version, idempotency_key, committed_at, created_at)
SELECT
    p.id,
    c.member_id,
    NULL,
    c.id,
    cl.id,
    'CLAIM',
    'POLICY_GENERAL',
    'COMMITTED',
    make_date(EXTRACT(YEAR FROM c.service_date)::int, 1, 1),
    make_date(EXTRACT(YEAR FROM c.service_date)::int, 12, 31),
    cl.limit_consumption,
    0,
    cl.calculation_version,
    'CLAIM:' || c.id || ':LINE:' || cl.id || ':GENERAL:V' || cl.calculation_version,
    now(),
    now()
FROM claim_lines cl
JOIN claims c ON c.id = cl.claim_id
JOIN LATERAL (
        SELECT a.policy_id
          FROM member_policy_assignments a
         WHERE a.member_id = c.member_id
           AND a.assignment_start_date <= c.service_date
           AND (a.assignment_end_date IS NULL OR a.assignment_end_date > c.service_date)
         ORDER BY a.assignment_start_date DESC, a.id DESC
         LIMIT 1) asg ON TRUE
JOIN benefit_policies p ON p.id = asg.policy_id
WHERE cl.current_line = true
  AND c.active = true
  AND c.status IN ('APPROVED', 'SETTLED', 'BATCHED')
  AND cl.limit_consumption IS NOT NULL
  AND cl.limit_consumption > 0
  AND c.service_date IS NOT NULL
  AND p.annual_limit IS NOT NULL
  AND p.annual_limit > 0
  AND NOT EXISTS (
        SELECT 1 FROM benefit_bucket_consumptions existing
         WHERE existing.claim_line_id = cl.id
           AND existing.limit_scope = 'POLICY_GENERAL');

-- PARITY, and it has to be able to FAIL.
--
-- The obvious version of this check is circular: comparing the ledger against
-- the claim lines that HAVE a ledger row compares a set against itself and
-- passes by construction, whatever the backfill missed. So the source side
-- below is computed over every line the OLD reader counted, with no reference
-- to what was written -- which is the only way a line the backfill skipped
-- can show up as a difference.
--
-- It also measures what the application will actually read: net of
-- compensating movements, grouped the way sumGeneralScopeCommitted filters.
-- A parity that sums gross while the reader nets would pass on a member whose
-- balance had just changed.
DO $$
DECLARE mismatch RECORD;
BEGIN
    SELECT * INTO mismatch FROM (
        SELECT
            src.member_id,
            src.period_start,
            coalesce(led.ledger_total, 0) AS ledger_total,
            src.claim_total
        FROM (
            -- Exactly the population the old reader summed.
            SELECT c.member_id,
                   make_date(EXTRACT(YEAR FROM c.service_date)::int, 1, 1) AS period_start,
                   sum(cl.limit_consumption) AS claim_total
              FROM claim_lines cl
              JOIN claims c ON c.id = cl.claim_id
             WHERE cl.current_line = true
               AND c.active = true
               AND c.status IN ('APPROVED', 'SETTLED', 'BATCHED')
               AND cl.limit_consumption IS NOT NULL
               AND cl.limit_consumption > 0
               AND c.service_date IS NOT NULL
             GROUP BY 1, 2
        ) src
        LEFT JOIN (
            SELECT led0.member_id,
                   led0.period_start,
                   sum(led0.approved_amount - coalesce(rev.reversed_amount, 0)) AS ledger_total
              FROM benefit_bucket_consumptions led0
              LEFT JOIN (
                    SELECT reversal_of_id, sum(approved_amount) AS reversed_amount
                      FROM benefit_bucket_consumptions
                     WHERE status = 'REVERSED' AND reversal_of_id IS NOT NULL
                     GROUP BY reversal_of_id
              ) rev ON rev.reversal_of_id = led0.id
             WHERE led0.limit_scope = 'POLICY_GENERAL' AND led0.status = 'COMMITTED'
             GROUP BY 1, 2
        ) led ON led.member_id = src.member_id AND led.period_start = src.period_start
        WHERE coalesce(led.ledger_total, 0) IS DISTINCT FROM src.claim_total
        LIMIT 1
    ) diff;

    IF FOUND THEN
        RAISE EXCEPTION
            'V189 parity failed: member % period % reads % from the ledger but % from claim lines',
            mismatch.member_id, mismatch.period_start, mismatch.ledger_total, mismatch.claim_total;
    END IF;
END $$;

-- No index is added here: V174 already created
-- idx_bucket_consumption_general_scope on exactly
-- (member_id, policy_id, period_start, period_end, status)
-- WHERE limit_scope = 'POLICY_GENERAL', which is the access path both of
-- these reads use. It was built for rows the ledger could not yet hold.
