\set ON_ERROR_STOP on

-- One-claim production repair:
--   Batch: JFZ26-09-00003
--   Claim: CLM-851
--   Line : PL-319FD1019F4E / C-C200 مستلزمات عملية زرع القرنية
--
-- Reason:
--   The line was approved with only 11,000.00 inside the general policy limit
--   and 500.00 refused by limit. Chronological limit audit shows 11,500.00
--   should be inside the limit and 0.00 should be refused.
--
-- Correct financial result:
--   gross/settlement_base      11,500.00
--   inside limit               11,500.00
--   beneficiary share 25%       2,875.00
--   insurer gross share 75%     8,625.00
--   provider discount 10%         862.50
--   insurer final payment       7,762.50
--   refused/provider balance        0.00
--
-- Safety:
--   Default is dry-run. To apply:
--     psql ... -v apply=true -f /tmp/repair_clm_851_limit_consumption.sql
--
-- This script is idempotent:
--   * It refuses to run unless the target row still has the exact old values.
--   * Ledger movements use stable idempotency keys.
--   * Provider account adjustment uses a stable description marker.

\if :{?apply}
\else
  \set apply 'false'
\endif

BEGIN;

CREATE TEMP TABLE _repair_clm_851 ON COMMIT DROP AS
SELECT
  c.id AS claim_id,
  c.claim_number,
  c.status,
  c.provider_id,
  c.policy_id AS claim_policy_id,
  c.member_id AS claim_member_id,
  cb.batch_code,
  l.id AS claim_line_id,
  l.calculation_version AS old_calculation_version,
  s.id AS old_snapshot_id,
  s.policy_id AS snapshot_policy_id,
  c.member_id AS snapshot_member_id,
  s.limit_semantic_key,
  s.period_start,
  s.period_end,
  s.effective_limit,
  bbc.id AS old_consumption_id,
  pa.id AS provider_account_id,
  pa.running_balance AS account_balance_before,
  pa.total_approved AS account_total_approved_before
FROM claims c
JOIN claim_batches cb ON cb.id = c.claim_batch_id
JOIN claim_lines l ON l.claim_id = c.id
JOIN claim_line_limit_snapshots s
  ON s.claim_line_id = l.id
 AND s.is_binding = true
 AND s.calculation_version = (
   SELECT MAX(s2.calculation_version)
   FROM claim_line_limit_snapshots s2
   WHERE s2.claim_line_id = l.id
 )
LEFT JOIN benefit_bucket_consumptions bbc
  ON bbc.claim_id = c.id
 AND bbc.claim_line_id = l.id
 AND bbc.status = 'COMMITTED'
 AND bbc.source_type = 'CLAIM'
 AND bbc.limit_scope = 'POLICY_GENERAL'
 AND bbc.approved_amount = 11000.00
LEFT JOIN provider_accounts pa ON pa.provider_id = c.provider_id
WHERE cb.batch_code = 'JFZ26-09-00003'
  AND c.claim_number = 'CLM-851'
  AND c.id = 851
  AND l.id = 55
  AND c.status = 'APPROVED'
  AND COALESCE(l.total_price, 0) = 11500.00
  AND COALESCE(l.settlement_base, 0) = 11500.00
  AND COALESCE(l.inside_limit, 0) = 11000.00
  AND COALESCE(l.limit_refused, 0) = 500.00
  AND COALESCE(l.company_share, 0) = 7425.00
  AND COALESCE(l.patient_share, 0) = 2750.00
  AND COALESCE(l.provider_discount_percent, 0) = 10.00
  AND COALESCE(l.provider_contract_discount, 0) = 825.00;

\echo 'Target row:'
SELECT * FROM _repair_clm_851;

DO $$
DECLARE
  n integer;
BEGIN
  SELECT COUNT(*) INTO n FROM _repair_clm_851;
  IF n <> 1 THEN
    RAISE EXCEPTION 'Expected exactly one CLM-851 target row with old values; found %', n;
  END IF;
END $$;

\if :apply

-- 1) Append-only limit ledger correction: reverse old 11,000 and commit 11,500.
INSERT INTO benefit_bucket_consumptions (
  claim_id,
  claim_line_id,
  policy_id,
  member_id,
  bucket_id,
  period_start,
  period_end,
  approved_amount,
  times_consumed,
  status,
  calculation_version,
  idempotency_key,
  reversal_of_id,
  reversal_reason,
  source_type,
  limit_scope,
  created_at,
  reversed_at
)
SELECT
  claim_id,
  claim_line_id,
  snapshot_policy_id,
  snapshot_member_id,
  NULL,
  period_start,
  period_end,
  11000.00,
  0,
  'REVERSED',
  1,
  'prod-repair-CLM-851-limit-ledger-reverse-78',
  old_consumption_id,
  'CLAIM_CORRECTION',
  'CLAIM',
  'POLICY_GENERAL',
  now(),
  now()
FROM _repair_clm_851
WHERE old_consumption_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM benefit_bucket_consumptions
    WHERE idempotency_key = 'prod-repair-CLM-851-limit-ledger-reverse-78'
  );

INSERT INTO benefit_bucket_consumptions (
  claim_id,
  claim_line_id,
  policy_id,
  member_id,
  bucket_id,
  period_start,
  period_end,
  approved_amount,
  times_consumed,
  status,
  calculation_version,
  idempotency_key,
  source_type,
  limit_scope,
  created_at,
  committed_at
)
SELECT
  claim_id,
  claim_line_id,
  snapshot_policy_id,
  snapshot_member_id,
  NULL,
  period_start,
  period_end,
  11500.00,
  0,
  'COMMITTED',
  2,
  'prod-repair-CLM-851-limit-ledger-commit-v2',
  'CLAIM',
  'POLICY_GENERAL',
  now(),
  now()
FROM _repair_clm_851
WHERE old_consumption_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM benefit_bucket_consumptions
    WHERE idempotency_key = 'prod-repair-CLM-851-limit-ledger-commit-v2'
  );

-- 2) Append a new explanatory snapshot instead of editing the old immutable one.
INSERT INTO claim_line_limit_snapshots (
  claim_id,
  claim_line_id,
  calculation_version,
  benefit_scope_type,
  beneficiary_scope_type,
  limit_semantic_key,
  bucket_id,
  policy_id,
  benefit_rule_id,
  benefit_group_id,
  source_type,
  source_id,
  source_version,
  member_policy_assignment_id,
  structure_revision,
  period_type,
  period_start,
  period_end,
  effective_limit,
  consumed_before,
  reserved_before,
  available_before,
  line_settlement_base,
  line_inside_limit,
  limit_consumption,
  patient_limit_excess,
  available_after,
  is_binding,
  consumption_order,
  created_at
)
SELECT
  s.claim_id,
  s.claim_line_id,
  2,
  s.benefit_scope_type,
  s.beneficiary_scope_type,
  s.limit_semantic_key,
  s.bucket_id,
  s.policy_id,
  s.benefit_rule_id,
  s.benefit_group_id,
  s.source_type,
  s.source_id,
  s.source_version,
  s.member_policy_assignment_id,
  s.structure_revision,
  s.period_type,
  s.period_start,
  s.period_end,
  s.effective_limit,
  46000.00,
  0.00,
  14000.00,
  11500.00,
  11500.00,
  11500.00,
  0.00,
  2500.00,
  true,
  s.consumption_order,
  now()
FROM claim_line_limit_snapshots s
JOIN _repair_clm_851 r ON r.old_snapshot_id = s.id
WHERE NOT EXISTS (
  SELECT 1
  FROM claim_line_limit_snapshots existing
  WHERE existing.claim_line_id = s.claim_line_id
    AND existing.calculation_version = 2
    AND existing.limit_semantic_key = s.limit_semantic_key
);

-- 3) Correct current line financial snapshot.
UPDATE claim_lines l
SET
  calculation_version = 2,
  binding_available_limit = 14000.00,
  inside_limit = 11500.00,
  limit_consumption = 11500.00,
  binding_remaining_limit = 2500.00,
  patient_limit_excess = 0.00,
  limit_refused = 0.00,
  company_share = 7762.50,
  approved_amount = 7762.50,
  insurer_gross_share = 8625.00,
  provider_discount_percent = 10.00,
  provider_contract_discount = 862.50,
  provider_net_before_rejection = 7762.50,
  provider_rejected_amount_v2 = 0.00,
  insurer_final_payment = 7762.50,
  patient_share = 2875.00,
  patient_coverage_share = 2875.00,
  patient_total_responsibility = 2875.00,
  refused_amount = 0.00,
  rejection_reason = NULL,
  rejection_reason_code = NULL
FROM _repair_clm_851 r
WHERE l.id = r.claim_line_id;

-- 4) Correct claim totals.
UPDATE claims c
SET
  requested_amount = 11500.00,
  approved_amount = 7762.50,
  patient_share = 2875.00,
  patient_copay = 2875.00,
  refused_amount = 0.00,
  net_provider_amount = 7762.50,
  provider_refusal_balance = 0.00,
  company_discount_amount = 862.50,
  updated_at = now()
FROM _repair_clm_851 r
WHERE c.id = r.claim_id;

-- 5) If this approved claim already credited the provider account, append a
--    CREDIT ADJUSTMENT for the net-provider delta: 7,762.50 - 7,425.00 = 337.50.
UPDATE provider_accounts pa
SET
  running_balance = pa.running_balance + 337.50,
  total_approved = pa.total_approved + 337.50,
  last_transaction_at = now(),
  updated_at = now()
FROM _repair_clm_851 r
WHERE pa.id = r.provider_account_id
  AND EXISTS (
    SELECT 1
    FROM account_transactions at
    WHERE at.provider_account_id = pa.id
      AND at.reference_type = 'CLAIM_APPROVAL'
      AND at.reference_id = r.claim_id
  )
  AND NOT EXISTS (
    SELECT 1
    FROM account_transactions at
    WHERE at.provider_account_id = pa.id
      AND at.reference_type = 'ADJUSTMENT'
      AND at.reference_id = r.claim_id
      AND at.description LIKE '%CLM-851 limit-consumption correction%'
  );

INSERT INTO account_transactions (
  provider_account_id,
  transaction_type,
  amount,
  balance_before,
  balance_after,
  reference_type,
  reference_id,
  reference_version,
  description,
  transaction_date,
  created_at,
  created_by
)
SELECT
  r.provider_account_id,
  'CREDIT',
  337.50,
  r.account_balance_before,
  r.account_balance_before + 337.50,
  'ADJUSTMENT',
  r.claim_id,
  2,
  'CLM-851 limit-consumption correction: net provider amount 7425.00 -> 7762.50',
  CURRENT_DATE,
  now(),
  NULL
FROM _repair_clm_851 r
WHERE r.provider_account_id IS NOT NULL
  AND EXISTS (
    SELECT 1
    FROM account_transactions at
    WHERE at.provider_account_id = r.provider_account_id
      AND at.reference_type = 'CLAIM_APPROVAL'
      AND at.reference_id = r.claim_id
  )
  AND NOT EXISTS (
    SELECT 1
    FROM account_transactions at
    WHERE at.provider_account_id = r.provider_account_id
      AND at.reference_type = 'ADJUSTMENT'
      AND at.reference_id = r.claim_id
      AND at.description LIKE '%CLM-851 limit-consumption correction%'
  );

\endif

\echo 'Claim after repair/dry-run:'
SELECT
  c.id,
  c.claim_number,
  c.status,
  c.requested_amount,
  c.approved_amount,
  c.patient_share,
  c.refused_amount,
  c.net_provider_amount,
  c.provider_refusal_balance,
  c.company_discount_amount
FROM claims c
WHERE c.id = 851;

\echo 'Line after repair/dry-run:'
SELECT
  l.id,
  l.calculation_version,
  l.total_price,
  l.settlement_base,
  l.binding_available_limit,
  l.inside_limit,
  l.limit_consumption,
  l.limit_refused,
  l.company_share,
  l.patient_share,
  l.refused_amount,
  l.provider_contract_discount,
  l.insurer_final_payment
FROM claim_lines l
WHERE l.id = 55;

\echo 'Benefit ledger for claim:'
SELECT
  id,
  claim_id,
  claim_line_id,
  limit_scope,
  approved_amount,
  status,
  calculation_version,
  idempotency_key,
  reversal_of_id,
  reversal_reason
FROM benefit_bucket_consumptions
WHERE claim_id = 851
ORDER BY id;

\echo 'Provider account transactions for claim:'
SELECT
  at.id,
  at.provider_account_id,
  at.transaction_type,
  at.amount,
  at.balance_before,
  at.balance_after,
  at.reference_type,
  at.reference_id,
  at.reference_version,
  at.description
FROM account_transactions at
WHERE at.reference_id = 851
ORDER BY at.id;

\if :apply
  COMMIT;
  \echo 'COMMIT completed for CLM-851 repair.'
\else
  ROLLBACK;
  \echo 'DRY-RUN only. Re-run with -v apply=true to apply this single-claim repair.'
\endif
