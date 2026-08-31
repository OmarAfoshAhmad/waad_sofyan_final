DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM benefit_policy_rules
        WHERE claim_context_code IS NULL OR btrim(claim_context_code) = ''
    ) THEN
        RAISE EXCEPTION 'V205: benefit rules without claim_context_code exist';
    END IF;
END $$;

DROP INDEX IF EXISTS uq_bpr_policy_category_context_active;

CREATE UNIQUE INDEX uq_bpr_policy_category_claim_context_active
    ON benefit_policy_rules(benefit_policy_id, medical_category_id, claim_context_code)
    WHERE deleted = false;

COMMENT ON INDEX uq_bpr_policy_category_claim_context_active IS
    'A benefit category may have distinct rules and buckets in exact business contexts such as INPATIENT, MATERNITY and PREGNANCY_COMPLICATIONS.';
