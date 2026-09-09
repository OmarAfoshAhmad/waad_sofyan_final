\set ON_ERROR_STOP on

-- One-claim production repair:
--   Batch: JFZ26-08-00002
--   Member: آية طارق سعيد
--   Claim: CLM-1551 / reference JFZ26-08-00002/0014
--
-- Business rule:
--   MRI services share the same benefit cap for the beneficiary, not separate
--   service-level caps when they belong to the same MRI benefit group.
--
-- Observed submitted references:
--   /0013 = 750.00 MRI consumption
--   /0014 = 800.00 MRI claim that was previously accepted in full
--
-- Correct result for /0014:
--   MRI shared limit      1,500.00
--   prior consumption       750.00
--   available before        750.00
--   gross requested         800.00
--   inside limit            750.00
--   limit refused            50.00
--   company 75%             562.50
--   beneficiary 25%         187.50
--
-- Safety:
--   Default is dry-run. To apply:
--     psql ... -v apply=true -f /tmp/repair_aya_mri_submitted_shared_limit.sql
--
-- This script only updates the second SUBMITTED Aya MRI claim/line display-
-- financial fields. It refuses to run unless the target row still has the exact
-- old values shown in production before the repair.

\if :{?apply}
\else
  \set apply 'false'
\endif

BEGIN;

CREATE TEMP TABLE _repair_aya_mri_1551 ON COMMIT DROP AS
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
  AND c.id = 1551
  AND c.claim_number = 'CLM-1551'
  AND c.member_id = 36543
  AND c.policy_id = 851
  AND c.status = 'SUBMITTED'
  AND c.active = true
  AND l.id = 108
  AND l.service_code = 'PL-FBBFA5EBA047'
  AND COALESCE(l.total_price, 0) = 800.00
  AND COALESCE(l.settlement_base, 0) = 800.00
  AND COALESCE(l.binding_available_limit, 0) = 1500.00
  AND COALESCE(l.inside_limit, 0) = 800.00
  AND COALESCE(l.limit_consumption, 0) = 800.00
  AND COALESCE(l.limit_refused, 0) = 0.00
  AND COALESCE(l.company_share, 0) = 600.00
  AND COALESCE(l.patient_share, 0) = 200.00
  AND COALESCE(l.refused_amount, 0) = 0.00
  AND COALESCE(l.provider_discount_percent, 0) = 0.00
  AND COALESCE(l.provider_contract_discount, 0) = 0.00;

\echo 'Target row:'
SELECT * FROM _repair_aya_mri_1551;

\echo 'Related Aya MRI claims before repair/dry-run:'
SELECT
  c.id AS claim_id,
  c.claim_number,
  cb.batch_code,
  c.service_date,
  c.requested_amount,
  c.approved_amount,
  c.patient_share,
  c.refused_amount,
  c.net_provider_amount,
  c.provider_refusal_balance,
  l.id AS line_id,
  l.service_code,
  l.service_name,
  l.total_price,
  l.binding_available_limit,
  l.inside_limit,
  l.limit_consumption,
  l.limit_refused,
  l.company_share,
  l.patient_share
FROM claims c
JOIN claim_batches cb ON cb.id = c.claim_batch_id
JOIN claim_lines l ON l.claim_id = c.id
WHERE cb.batch_code = 'JFZ26-08-00002'
  AND c.member_id = 36543
  AND c.policy_id = 851
  AND c.id IN (1501, 1551)
ORDER BY c.id, l.id;

DO $$
DECLARE
  n integer;
BEGIN
  SELECT COUNT(*) INTO n FROM _repair_aya_mri_1551;
  IF n <> 1 THEN
    RAISE EXCEPTION 'Expected exactly one submitted Aya CLM-1551 MRI row with old values; found %', n;
  END IF;
END $$;

\if :apply

UPDATE claim_lines l
SET
  binding_available_limit = 750.00,
  inside_limit = 750.00,
  limit_consumption = 750.00,
  binding_remaining_limit = 0.00,
  patient_limit_excess = 50.00,
  limit_refused = 50.00,
  company_share = 562.50,
  approved_amount = 562.50,
  insurer_gross_share = 562.50,
  provider_discount_percent = 0.00,
  provider_contract_discount = 0.00,
  provider_net_before_rejection = 562.50,
  provider_rejected_amount_v2 = 50.00,
  insurer_final_payment = 562.50,
  patient_share = 187.50,
  patient_coverage_share = 187.50,
  patient_total_responsibility = 187.50,
  refused_amount = 50.00,
  rejection_reason = 'تجاوز سقف المبلغ المسموح به',
  rejection_reason_code = 'USAGE_AMOUNT_LIMIT_EXCEEDED'
FROM _repair_aya_mri_1551 r
WHERE l.id = r.claim_line_id;

UPDATE claims c
SET
  requested_amount = 800.00,
  approved_amount = 562.50,
  patient_share = 187.50,
  patient_copay = 187.50,
  refused_amount = 50.00,
  net_provider_amount = 562.50,
  provider_refusal_balance = 50.00,
  updated_at = now()
FROM _repair_aya_mri_1551 r
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
WHERE c.id = 1551;

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
WHERE l.id = 108;

\if :apply
  COMMIT;
  \echo 'COMMIT completed for Aya CLM-1551 MRI shared-limit submitted repair.'
\else
  ROLLBACK;
  \echo 'DRY-RUN only. Re-run with -v apply=true to apply this submitted-claim repair.'
\endif
