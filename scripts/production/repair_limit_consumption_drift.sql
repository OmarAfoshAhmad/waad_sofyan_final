\set ON_ERROR_STOP on

-- WAAD/TBA production repair for pre-fix benefit amount-limit drift.
--
-- Default mode is DRY-RUN. It rolls back unless explicitly called with:
--   -v apply=true
--
-- Run dry-run on production:
--   docker cp scripts/production/repair_limit_consumption_drift.sql waadapp-db:/tmp/repair_limit_consumption_drift.sql
--   docker exec -i waadapp-db psql -U postgres -d tba_waad_system \
--     -v batch_code='JFZ26-08-00002' \
--     -f /tmp/repair_limit_consumption_drift.sql
--
-- Apply only editable/non-final claims:
--   docker exec -i waadapp-db psql -U postgres -d tba_waad_system \
--     -v batch_code='JFZ26-08-00002' \
--     -v apply=true \
--     -f /tmp/repair_limit_consumption_drift.sql
--
-- Optional:
--   -v claim_numbers='CLM-1201,CLM-1351,CLM-1501,CLM-1551'
--   -v policy_id='801'
--
-- Guardrail:
--   This script refuses to update APPROVED/BATCHED/SETTLED/PAID claims. Those
--   require a proper reversal/correction cycle because provider accounts and
--   immutable ledgers may already be affected.
--
-- What it repairs:
--   * current claim_lines financial display fields affected by amount caps
--   * parent claims totals
--
-- What it does NOT rewrite:
--   * immutable claim_line_limit_snapshots
--   * committed benefit_bucket_consumptions
--   * provider account transactions
--
-- Therefore, use it for submitted/draft review data that was entered before
-- the limit tracking fix and has not been financially approved.

\if :{?apply}
\else
  \set apply 'false'
\endif

\if :{?batch_code}
\else
  \set batch_code ''
\endif

\if :{?claim_numbers}
\else
  \set claim_numbers ''
\endif

\if :{?policy_id}
\else
  \set policy_id ''
\endif

BEGIN;

CREATE TEMP TABLE _limit_drift_repair ON COMMIT DROP AS
WITH params AS (
  SELECT
    NULLIF(:'batch_code', '') AS batch_code,
    NULLIF(:'claim_numbers', '') AS claim_numbers,
    NULLIF(:'policy_id', '')::bigint AS policy_id
),
target_claims AS (
  SELECT c.*
  FROM claims c
  LEFT JOIN claim_batches cb ON cb.id = c.claim_batch_id
  CROSS JOIN params p
  WHERE c.active = true
    AND (p.batch_code IS NULL OR cb.batch_code = p.batch_code)
    AND (p.policy_id IS NULL OR c.policy_id = p.policy_id)
    AND (
      p.claim_numbers IS NULL
      OR c.claim_number = ANY (regexp_split_to_array(p.claim_numbers, '\s*,\s*'))
    )
),
binding_limits AS (
  SELECT
    s.id AS snapshot_id,
    s.claim_id,
    s.claim_line_id,
    s.limit_semantic_key,
    s.policy_id,
    s.period_start,
    s.period_end,
    s.effective_limit,
    s.consumed_before AS saved_consumed_before,
    s.reserved_before AS saved_reserved_before,
    s.available_before AS saved_available_before,
    s.line_settlement_base,
    s.line_inside_limit AS saved_inside_limit,
    s.limit_consumption AS saved_limit_consumption,
    c.claim_number,
    c.status,
    c.service_date,
    c.member_id,
    c.claim_batch_id,
    cb.batch_code,
    l.line_number,
    l.coverage_percent_snapshot,
    l.refused_amount AS old_refused_amount,
    l.limit_refused AS old_limit_refused,
    l.company_share AS old_company_share,
    l.patient_share AS old_patient_share,
    COALESCE(l.manual_refused_amount, 0)::numeric(15,2) AS manual_refused_amount,
    COALESCE(l.price_excess_refused, 0)::numeric(15,2) AS price_excess_refused,
    COALESCE(l.provider_contract_discount, 0)::numeric(15,2) AS provider_contract_discount
  FROM claim_line_limit_snapshots s
  JOIN target_claims c ON c.id = s.claim_id
  JOIN claim_lines l ON l.id = s.claim_line_id
  LEFT JOIN claim_batches cb ON cb.id = c.claim_batch_id
  WHERE s.is_binding = true
    AND COALESCE(l.current_line, true) = true
    AND s.effective_limit IS NOT NULL
    AND s.effective_limit > 0
),
sequenced AS (
  SELECT
    b.*,
    COALESCE(
      SUM(b.saved_limit_consumption) OVER (
        PARTITION BY b.policy_id, b.member_id, b.limit_semantic_key, b.period_start, COALESCE(b.period_end, DATE '9999-12-31')
        ORDER BY b.service_date, b.claim_id, b.claim_line_id, b.snapshot_id
        ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING
      ),
      0
    )::numeric(15,2) AS expected_consumed_before
  FROM binding_limits b
),
calc AS (
  SELECT
    s.*,
    GREATEST(0, s.effective_limit - s.expected_consumed_before - COALESCE(s.saved_reserved_before, 0))::numeric(15,2)
      AS expected_available_before,
    LEAST(
      COALESCE(s.line_settlement_base, 0),
      GREATEST(0, s.effective_limit - s.expected_consumed_before - COALESCE(s.saved_reserved_before, 0))
    )::numeric(15,2) AS expected_inside_limit
  FROM sequenced s
),
line_calc AS (
  SELECT
    c.*,
    GREATEST(0, COALESCE(c.line_settlement_base, 0) - c.expected_inside_limit)::numeric(15,2)
      AS new_limit_refused,
    c.expected_inside_limit::numeric(15,2) AS new_limit_consumption,
    GREATEST(0, c.expected_available_before - c.expected_inside_limit)::numeric(15,2)
      AS new_binding_remaining_limit,
    ROUND(c.expected_inside_limit * COALESCE(c.coverage_percent_snapshot, 0) / 100.0, 2)::numeric(15,2)
      AS company_before_manual_refusal,
    ROUND(c.expected_inside_limit * (100 - COALESCE(c.coverage_percent_snapshot, 0)) / 100.0, 2)::numeric(15,2)
      AS new_patient_share
  FROM calc c
),
final_calc AS (
  SELECT
    l.*,
    LEAST(l.company_before_manual_refusal, l.manual_refused_amount)::numeric(15,2) AS new_manual_refused_effective,
    GREATEST(0, l.company_before_manual_refusal - LEAST(l.company_before_manual_refusal, l.manual_refused_amount))::numeric(15,2)
      AS new_company_share,
    (
      l.price_excess_refused
      + l.new_limit_refused
      + LEAST(l.company_before_manual_refusal, l.manual_refused_amount)
    )::numeric(15,2) AS new_refused_amount
  FROM line_calc l
)
SELECT *
FROM final_calc
WHERE ABS(COALESCE(saved_consumed_before,0) - expected_consumed_before) > 0.009
   OR ABS(COALESCE(saved_available_before,0) - expected_available_before) > 0.009
   OR ABS(COALESCE(saved_inside_limit,0) - expected_inside_limit) > 0.009
   OR ABS(COALESCE(old_limit_refused,0) - new_limit_refused) > 0.009;

\echo 'Affected claim-line candidates:'
SELECT
  batch_code,
  claim_number,
  status,
  service_date,
  line_number,
  old_limit_refused,
  new_limit_refused,
  old_company_share,
  new_company_share,
  old_patient_share,
  new_patient_share,
  old_refused_amount,
  new_refused_amount
FROM _limit_drift_repair
ORDER BY service_date, claim_id, claim_line_id;

\echo 'Final-status rows that will NOT be updated by this script:'
SELECT claim_number, status, count(*) AS affected_lines
FROM _limit_drift_repair
WHERE status IN ('APPROVED','BATCHED','SETTLED','PAID')
GROUP BY claim_number, status
ORDER BY claim_number;

DO $$
DECLARE
  final_count integer;
BEGIN
  SELECT count(*) INTO final_count
  FROM _limit_drift_repair
  WHERE status IN ('APPROVED','BATCHED','SETTLED','PAID');

  IF final_count > 0 THEN
    RAISE NOTICE 'There are % final financial rows. They are reported only, not updated.', final_count;
  END IF;
END $$;

UPDATE claim_lines l
SET
  binding_available_limit = r.expected_available_before,
  inside_limit = r.expected_inside_limit,
  limit_consumption = r.new_limit_consumption,
  binding_remaining_limit = r.new_binding_remaining_limit,
  patient_limit_excess = r.new_limit_refused,
  limit_refused = r.new_limit_refused,
  patient_share = r.new_patient_share,
  patient_coverage_share = r.new_patient_share,
  patient_total_responsibility = r.new_patient_share,
  company_share = r.new_company_share,
  approved_amount = r.new_company_share,
  insurer_gross_share = r.company_before_manual_refusal,
  insurer_final_payment = r.new_company_share,
  refused_amount = r.new_refused_amount,
  provider_rejected_amount_v2 = r.new_refused_amount,
  rejection_reason = CASE
    WHEN r.new_limit_refused > 0 THEN 'تجاوز سقف المبلغ المسموح به'
    ELSE l.rejection_reason
  END,
  rejection_reason_code = CASE
    WHEN r.new_limit_refused > 0 THEN 'USAGE_AMOUNT_LIMIT_EXCEEDED'
    ELSE l.rejection_reason_code
  END
FROM _limit_drift_repair r
WHERE l.id = r.claim_line_id
  AND r.status NOT IN ('APPROVED','BATCHED','SETTLED','PAID')
  AND :'apply' = 'true';

\echo 'Updated editable claim lines:'
SELECT count(*) AS updated_line_count
FROM _limit_drift_repair
WHERE status NOT IN ('APPROVED','BATCHED','SETTLED','PAID')
  AND :'apply' = 'true';

WITH totals AS (
  SELECT
    c.id AS claim_id,
    COALESCE(SUM(COALESCE(l.total_price, l.requested_total, 0)), 0)::numeric(15,2) AS requested_amount,
    COALESCE(SUM(COALESCE(l.company_share, 0)), 0)::numeric(15,2) AS approved_amount,
    COALESCE(SUM(COALESCE(l.patient_share, 0)), 0)::numeric(15,2) AS patient_share,
    COALESCE(SUM(COALESCE(l.refused_amount, 0)), 0)::numeric(15,2) AS refused_amount,
    COALESCE(SUM(COALESCE(l.provider_rejected_amount_v2, l.refused_amount, 0)), 0)::numeric(15,2) AS provider_refusal_balance
  FROM claims c
  JOIN claim_lines l ON l.claim_id = c.id AND COALESCE(l.current_line, true) = true
  WHERE c.id IN (
    SELECT DISTINCT claim_id
    FROM _limit_drift_repair
    WHERE status NOT IN ('APPROVED','BATCHED','SETTLED','PAID')
  )
  GROUP BY c.id
)
UPDATE claims c
SET
  requested_amount = t.requested_amount,
  approved_amount = t.approved_amount,
  patient_share = t.patient_share,
  patient_copay = t.patient_share,
  refused_amount = t.refused_amount,
  net_provider_amount = t.approved_amount,
  provider_refusal_balance = t.provider_refusal_balance,
  updated_at = now()
FROM totals t
WHERE c.id = t.claim_id
  AND :'apply' = 'true';

\echo 'Claim totals after calculation:'
SELECT
  c.claim_number,
  c.status,
  c.requested_amount,
  c.approved_amount,
  c.patient_share,
  c.refused_amount,
  c.net_provider_amount,
  c.provider_refusal_balance
FROM claims c
WHERE c.id IN (SELECT DISTINCT claim_id FROM _limit_drift_repair)
ORDER BY c.service_date, c.id;

\if :apply
  COMMIT;
  \echo 'COMMIT completed because apply=true.'
\else
  ROLLBACK;
  \echo 'DRY-RUN only. Rolled back. Re-run with -v apply=true to apply editable/non-final rows.'
\endif
