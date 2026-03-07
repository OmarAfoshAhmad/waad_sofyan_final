-- V100: Settlement & Provider Accounts Integrity Fix
--
-- Purpose:
--   1. Backfill settlement_batches.provider_id from provider_accounts for any rows
--      where provider_id might be out-of-sync with provider_account_id.
--   2. Ensure every provider in the providers table has a provider_account record
--      so that the accounts list page shows all providers (even with 0 balance).
--   3. Backfill running_balance / total_approved from approved claims that were
--      never credited due to missing event processing.
--
-- Safe to run multiple times (idempotent via COALESCE and WHERE conditions).

-- ============================================================
-- STEP 1: Backfill settlement_batches.provider_id
--         from provider_accounts where they diverge
-- ============================================================
UPDATE settlement_batches sb
SET provider_id = pa.provider_id
FROM provider_accounts pa
WHERE sb.provider_account_id = pa.id
  AND sb.provider_id IS DISTINCT FROM pa.provider_id;

-- ============================================================
-- STEP 2: Ensure every active provider has a provider_account
--         (create zero-balance accounts for providers without one)
-- ============================================================
INSERT INTO provider_accounts (provider_id, running_balance, total_approved, total_paid, status, created_at, updated_at)
SELECT
    p.id AS provider_id,
    0.00 AS running_balance,
    0.00 AS total_approved,
    0.00 AS total_paid,
    'ACTIVE' AS status,
    NOW() AS created_at,
    NOW() AS updated_at
FROM providers p
WHERE NOT EXISTS (
    SELECT 1 FROM provider_accounts pa WHERE pa.provider_id = p.id
);

-- ============================================================
-- STEP 3: Reconcile provider_accounts.total_approved
--         by summing claim net amount of all APPROVED/SETTLED claims
--         for any account whose total_approved is 0 but has settled claims.
-- ============================================================
DO $$
DECLARE
    claim_amount_expr TEXT;
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'claims'
          AND column_name = 'net_provider_amount'
    ) THEN
        claim_amount_expr := 'COALESCE(c.net_provider_amount, c.approved_amount)';
    ELSE
        claim_amount_expr := 'c.approved_amount';
    END IF;

    EXECUTE format($SQL$
        UPDATE provider_accounts pa
        SET
            total_approved  = COALESCE(claim_totals.approved_sum, 0),
            total_paid      = COALESCE(batch_totals.paid_sum, 0),
            running_balance = COALESCE(claim_totals.approved_sum, 0) - COALESCE(batch_totals.paid_sum, 0),
            updated_at      = NOW()
        FROM (
            SELECT
                c.provider_id,
                SUM(%s) AS approved_sum
            FROM claims c
            WHERE c.status IN ('APPROVED', 'SETTLED')
              AND %s IS NOT NULL
              AND %s > 0
            GROUP BY c.provider_id
        ) claim_totals
        LEFT JOIN (
            SELECT
                pa2.provider_id,
                SUM(sb.total_net_amount) AS paid_sum
            FROM settlement_batches sb
            JOIN provider_accounts pa2 ON sb.provider_account_id = pa2.id
            WHERE sb.status = 'PAID'
            GROUP BY pa2.provider_id
        ) batch_totals ON batch_totals.provider_id = claim_totals.provider_id
        WHERE pa.provider_id = claim_totals.provider_id
          AND pa.total_approved = 0
          AND claim_totals.approved_sum > 0
    $SQL$, claim_amount_expr, claim_amount_expr, claim_amount_expr);
END $$;

