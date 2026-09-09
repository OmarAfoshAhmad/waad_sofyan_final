\set ON_ERROR_STOP on

-- One-claim production repair:
--   Batch: JFZ26-08-00002
--   Member: اوس عادل علي عبدالعاطي الفاخري
--   Claim: CLM-1351 / reference JFZ26-08-00002/0010
--
-- Business rule selected by operator:
--   Limit consumption for these submitted paper claims follows entry/reference
--   order, not service_date order.
--
-- Prior submitted references before /0010:
--   /0008 = 16.00
--   /0009 = 40.00
--   prior consumption = 56.00
--
-- Correct result:
--   general limit        60,000.00
--   available before     59,944.00
--   gross requested      69,000.00
--   inside limit         59,944.00
--   limit refused         9,056.00
--   company 75%          44,958.00
--   beneficiary 25%      14,986.00
--
-- Safety:
--   Default is dry-run. To apply:
--     psql ... -v apply=true -f /tmp/repair_aous_submitted_limit_by_reference.sql
--
-- This script only updates SUBMITTED claim/line display-financial fields.
-- It refuses to run unless the target row still has the exact old values.

\if :{?apply}
\else
  \set apply 'false'
\endif

BEGIN;

CREATE TEMP TABLE _repair_aous_1351 ON COMMIT DROP AS
SELECT
  c.id AS claim_id,
  c.claim_number,
  c.status,
  c.member_id,
  c.policy_id,
  cb.batch_code,
  l.id AS claim_line_id,
  l.service_code,
  l.service_name
FROM claims c
JOIN claim_batches cb ON cb.id = c.claim_batch_id
JOIN claim_lines l ON l.claim_id = c.id
WHERE cb.batch_code = 'JFZ26-08-00002'
  AND c.id = 1351
  AND c.claim_number = 'CLM-1351'
  AND c.member_id = 31909
  AND c.policy_id = 851
  AND c.status = 'SUBMITTED'
  AND c.active = true
  AND l.id = 101
  AND l.service_code = 'PL-319FD1019F4E'
  AND COALESCE(l.total_price, 0) = 69000.00
  AND COALESCE(l.settlement_base, 0) = 69000.00
  AND COALESCE(l.binding_available_limit, 0) = 60000.00
  AND COALESCE(l.inside_limit, 0) = 60000.00
  AND COALESCE(l.limit_consumption, 0) = 60000.00
  AND COALESCE(l.limit_refused, 0) = 9000.00
  AND COALESCE(l.company_share, 0) = 45000.00
  AND COALESCE(l.patient_share, 0) = 15000.00
  AND COALESCE(l.refused_amount, 0) = 9000.00
  AND COALESCE(l.provider_discount_percent, 0) = 0.00
  AND COALESCE(l.provider_contract_discount, 0) = 0.00;

\echo 'Target row:'
SELECT * FROM _repair_aous_1351;

DO $$
DECLARE
  n integer;
BEGIN
  SELECT COUNT(*) INTO n FROM _repair_aous_1351;
  IF n <> 1 THEN
    RAISE EXCEPTION 'Expected exactly one submitted Aous CLM-1351 row with old values; found %', n;
  END IF;
END $$;

\if :apply

UPDATE claim_lines l
SET
  binding_available_limit = 59944.00,
  inside_limit = 59944.00,
  limit_consumption = 59944.00,
  binding_remaining_limit = 0.00,
  patient_limit_excess = 9056.00,
  limit_refused = 9056.00,
  company_share = 44958.00,
  approved_amount = 44958.00,
  insurer_gross_share = 44958.00,
  provider_discount_percent = 0.00,
  provider_contract_discount = 0.00,
  provider_net_before_rejection = 44958.00,
  provider_rejected_amount_v2 = 9056.00,
  insurer_final_payment = 44958.00,
  patient_share = 14986.00,
  patient_coverage_share = 14986.00,
  patient_total_responsibility = 14986.00,
  refused_amount = 9056.00,
  rejection_reason = 'تجاوز سقف المبلغ المسموح به',
  rejection_reason_code = 'USAGE_AMOUNT_LIMIT_EXCEEDED'
FROM _repair_aous_1351 r
WHERE l.id = r.claim_line_id;

UPDATE claims c
SET
  requested_amount = 69000.00,
  approved_amount = 44958.00,
  patient_share = 14986.00,
  patient_copay = 14986.00,
  refused_amount = 9056.00,
  net_provider_amount = 44958.00,
  provider_refusal_balance = 9056.00,
  updated_at = now()
FROM _repair_aous_1351 r
WHERE c.id = r.claim_id;

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
  c.provider_refusal_balance
FROM claims c
WHERE c.id = 1351;

\echo 'Line after repair/dry-run:'
SELECT
  l.id,
  l.total_price,
  l.settlement_base,
  l.binding_available_limit,
  l.inside_limit,
  l.limit_consumption,
  l.binding_remaining_limit,
  l.limit_refused,
  l.company_share,
  l.patient_share,
  l.refused_amount,
  l.provider_rejected_amount_v2,
  l.insurer_final_payment
FROM claim_lines l
WHERE l.id = 101;

\if :apply
  COMMIT;
  \echo 'COMMIT completed for Aous CLM-1351 submitted repair.'
\else
  ROLLBACK;
  \echo 'DRY-RUN only. Re-run with -v apply=true to apply this submitted-claim repair.'
\endif
