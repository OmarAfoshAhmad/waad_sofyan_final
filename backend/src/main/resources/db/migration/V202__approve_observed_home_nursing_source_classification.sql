UPDATE claim_context_source_aliases
SET requires_review = false
WHERE provider_id IS NULL
  AND normalized_alias = 'تمريض منزلي'
  AND claim_context_code = 'INPATIENT'
  AND medical_category_code = 'CAT-HOME-NURSING';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM claim_context_source_aliases
        WHERE provider_id IS NULL
          AND normalized_alias = 'تمريض منزلي'
          AND claim_context_code = 'INPATIENT'
          AND medical_category_code = 'CAT-HOME-NURSING'
          AND active = true
          AND requires_review = false
    ) THEN
        RAISE EXCEPTION 'Home nursing source mapping is incomplete';
    END IF;
END $$;
