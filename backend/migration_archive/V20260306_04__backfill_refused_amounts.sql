-- Backfill refused_amount for existing rejected claims
-- And fix any inconsistencies in approved_amount for rejected claims

-- 1. For claims in REJECTED status, the refused_amount should equal requested_amount
UPDATE claims 
SET refused_amount = requested_amount,
    approved_amount = 0,
    patient_copay = 0,
    net_provider_amount = 0
WHERE status = 'REJECTED' 
  AND (refused_amount IS NULL OR refused_amount = 0 OR approved_amount > 0);

-- 2. For claim lines in rejected claims, approved_amount should be 0
UPDATE claim_lines
SET approved_amount = 0
WHERE claim_id IN (SELECT id FROM claims WHERE status = 'REJECTED');

-- 3. For claims in BATCHED/SETTLED/APPROVED, ensure refused_amount is calculated if missing
-- refused_amount = requested_amount - approved_amount (simplified fallback)
UPDATE claims
SET refused_amount = COALESCE(requested_amount, 0) - COALESCE(approved_amount, 0)
WHERE status IN ('APPROVED', 'BATCHED', 'SETTLED')
  AND (refused_amount IS NULL OR refused_amount = 0)
  AND COALESCE(requested_amount, 0) > COALESCE(approved_amount, 0);
