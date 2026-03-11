-- =============================================================================
-- V115: Fix provider account balance corrections
-- =============================================================================
-- Business rule: Provider share = ROUND(approvedAmount × (1 - discountPercent), 2)
--   Company share (حصة الشركة)  = 10%   → ROUND(48.75 × 0.10, 2) = 4.88
--   Provider share (نصيب المرفق) = 90%  → ROUND(48.75 × 0.90, 2) = 43.88
--   Correction per claim = 48.75 − 43.88 = 4.87  (exact 2dp arithmetic)
--
-- Starting balance: 105.75 (before all corrections)
-- Corrections for provider_id=1 (دار الشفاء):
--   DEBIT 1: orphan CREDIT from deleted claim #18   → 8.25  → balance: 97.50
--   DEBIT 2: CLM-49 over-credit (48.75 − 43.88)    → 4.87  → balance: 92.63
--   DEBIT 3: CLM-50 over-credit (48.75 − 43.88)    → 4.87  → balance: 87.76
--   Total corrections: 17.99  →  final balance: 87.76
-- =============================================================================

DO $$
DECLARE
    v_account_id  BIGINT;
    v_curr_bal    NUMERIC(14,2);
BEGIN
    SELECT id, running_balance
      INTO v_account_id, v_curr_bal
      FROM provider_accounts
     WHERE provider_id = 1;

    IF v_account_id IS NULL THEN
        RAISE NOTICE 'No account for provider_id=1, skipping.';
        RETURN;
    END IF;

    IF v_curr_bal <> 105.75 THEN
        RAISE NOTICE 'Unexpected balance % (expected 105.75), skipping — already corrected?', v_curr_bal;
        RETURN;
    END IF;

    -- ---- Correction 1: reverse orphan credit from deleted claim #18 ----
    -- balance: 105.75 → 97.50  (105.75 − 8.25 = 97.50)
    INSERT INTO account_transactions (
        provider_account_id, transaction_type, amount,
        balance_before, balance_after,
        reference_type, reference_number,
        description, transaction_date
    ) VALUES (
        v_account_id, 'DEBIT', 8.25,
        105.75, 97.50,
        'ADJUSTMENT', 'CORR-ORPHAN-CLM18',
        'تصحيح: عكس اعتماد مطالبة #18 المحذوفة (حركة يتيمة)',
        CURRENT_DATE
    );

    -- ---- Correction 2: CLM-49 over-credit (provider share 43.88, not 48.75) ----
    -- balance: 97.50 → 92.63  (97.50 − 4.87 = 92.63)
    INSERT INTO account_transactions (
        provider_account_id, transaction_type, amount,
        balance_before, balance_after,
        reference_type, reference_id, reference_number,
        description, transaction_date
    ) VALUES (
        v_account_id, 'DEBIT', 4.87,
        97.50, 92.63,
        'ADJUSTMENT', 49, 'CORR-CLM49-DISCOUNT',
        'تصحيح: خصم حصة الشركة 10% من اعتماد CLM-49 (48.75 → 43.88)',
        CURRENT_DATE
    );

    -- ---- Correction 3: CLM-50 over-credit (provider share 43.88, not 48.75) ----
    -- balance: 92.63 → 87.76  (92.63 − 4.87 = 87.76)
    INSERT INTO account_transactions (
        provider_account_id, transaction_type, amount,
        balance_before, balance_after,
        reference_type, reference_id, reference_number,
        description, transaction_date
    ) VALUES (
        v_account_id, 'DEBIT', 4.87,
        92.63, 87.76,
        'ADJUSTMENT', 50, 'CORR-CLM50-DISCOUNT',
        'تصحيح: خصم حصة الشركة 10% من اعتماد CLM-50 (48.75 → 43.88)',
        CURRENT_DATE
    );

    -- ---- Update provider_accounts ----
    -- running_balance = total_approved - total_paid
    -- Reduce total_approved by 17.99 (sum of 3 correction debits)
    -- running_balance: 105.75 − 17.99 = 87.76
    UPDATE provider_accounts
       SET running_balance = 87.76,
           total_approved  = total_approved - 17.99,
           updated_at      = NOW()
     WHERE id = v_account_id;

    RAISE NOTICE 'V115 applied. New provider balance: 87.76';
END;
$$;
