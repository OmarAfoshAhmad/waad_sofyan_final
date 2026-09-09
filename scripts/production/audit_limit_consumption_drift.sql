\set ON_ERROR_STOP on

-- WAAD/TBA production safety audit
-- Purpose:
--   Detect claim lines saved while amount-limit consumption ignored earlier
--   claims in the same policy/member/benefit-limit period.
--
-- Safe: read-only. No writes.
--
-- Run on production:
--   docker exec -i waadapp-db psql -U postgres -d tba_waad_system \
--     -v batch_code='JFZ26-08-00002' \
--     -f /tmp/audit_limit_consumption_drift.sql
--
-- Optional filters:
--   -v batch_code='JFZ26-08-00002'
--   -v claim_numbers='CLM-1201,CLM-1351,CLM-1501,CLM-1551'
--   -v policy_id='801'
--
-- Notes:
--   * Uses binding claim_line_limit_snapshots because those are the persisted
--     explanation of which limit capped each line.
--   * Orders by service_date, claim id, line id, snapshot id. This matches
--     the chronological cap-consumption expectation for entered claims.

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

BEGIN READ ONLY;

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
latest_snapshots AS (
  SELECT claim_line_id, MAX(calculation_version) AS calculation_version
  FROM claim_line_limit_snapshots
  GROUP BY claim_line_id
),
binding_limits AS (
  SELECT
    s.id AS snapshot_id,
    s.claim_id,
    s.claim_line_id,
    s.calculation_version,
    s.limit_semantic_key,
    s.policy_id,
    s.bucket_id,
    s.member_policy_assignment_id,
    s.period_start,
    s.period_end,
    s.effective_limit,
    s.consumed_before AS saved_consumed_before,
    s.reserved_before AS saved_reserved_before,
    s.available_before AS saved_available_before,
    s.available_after AS saved_available_after,
    s.line_settlement_base,
    s.line_inside_limit AS saved_inside_limit,
    s.limit_consumption AS saved_limit_consumption,
    s.patient_limit_excess AS saved_patient_limit_excess,
    c.claim_number,
    c.status,
    c.service_date,
    c.member_id,
    c.claim_batch_id,
    cb.batch_code,
    cb.batch_month,
    cb.batch_year,
    l.line_number,
    l.service_code,
    l.service_name,
    l.coverage_percent_snapshot,
    l.patient_copay_percent_snapshot,
    l.total_price,
    l.refused_amount AS saved_line_refused,
    l.limit_refused AS saved_line_limit_refused,
    l.company_share AS saved_company_share,
    l.patient_share AS saved_patient_share
  FROM claim_line_limit_snapshots s
  JOIN latest_snapshots ls
    ON ls.claim_line_id = s.claim_line_id
   AND ls.calculation_version = s.calculation_version
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
expected AS (
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
diffs AS (
  SELECT
    e.*,
    GREATEST(0, COALESCE(e.line_settlement_base, 0) - e.expected_inside_limit)::numeric(15,2)
      AS expected_limit_refused,
    e.expected_inside_limit::numeric(15,2) AS expected_limit_consumption,
    GREATEST(0, e.expected_available_before - e.expected_inside_limit)::numeric(15,2)
      AS expected_available_after,
    ROUND(e.expected_inside_limit * COALESCE(e.coverage_percent_snapshot, 0) / 100.0, 2)::numeric(15,2)
      AS expected_company_before_manual_refusal,
    ROUND(e.expected_inside_limit * (100 - COALESCE(e.coverage_percent_snapshot, 0)) / 100.0, 2)::numeric(15,2)
      AS expected_patient_share
  FROM expected e
)
SELECT
  batch_code,
  claim_number,
  status,
  service_date,
  line_number,
  service_code,
  service_name,
  limit_semantic_key,
  effective_limit,
  saved_consumed_before,
  expected_consumed_before,
  saved_available_before,
  expected_available_before,
  saved_inside_limit,
  expected_inside_limit,
  saved_line_limit_refused,
  expected_limit_refused,
  saved_company_share,
  expected_company_before_manual_refusal,
  saved_patient_share,
  expected_patient_share,
  (expected_limit_refused - COALESCE(saved_line_limit_refused, 0))::numeric(15,2) AS refused_delta
FROM diffs
WHERE ABS(COALESCE(saved_inside_limit,0) - expected_inside_limit) > 0.009
   OR ABS(COALESCE(saved_line_limit_refused,0) - expected_limit_refused) > 0.009
ORDER BY service_date, claim_id, claim_line_id;

ROLLBACK;
