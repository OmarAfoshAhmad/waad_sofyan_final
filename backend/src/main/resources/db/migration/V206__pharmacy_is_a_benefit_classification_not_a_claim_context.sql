DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM claims WHERE claim_context_code = 'PHARMACY') THEN
        RAISE EXCEPTION 'Cannot remove PHARMACY claim context: claims already reference it';
    END IF;
    IF EXISTS (SELECT 1 FROM benefit_policy_rules WHERE claim_context_code = 'PHARMACY') THEN
        RAISE EXCEPTION 'Cannot remove PHARMACY claim context: benefit rules still reference it';
    END IF;
    IF EXISTS (SELECT 1 FROM provider_contract_pricing_items WHERE claim_context_code = 'PHARMACY') THEN
        RAISE EXCEPTION 'Cannot remove PHARMACY claim context: pricing items still reference it';
    END IF;
END $$;

DELETE FROM claim_context_source_aliases WHERE claim_context_code = 'PHARMACY';
DELETE FROM claim_contexts WHERE code = 'PHARMACY';
