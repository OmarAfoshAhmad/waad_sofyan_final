-- Coverage rules are intentionally limited to outpatient/inpatient contexts.
-- Emergency/ambulance benefits are handled as ordinary outpatient claim context
-- unless the user selects inpatient explicitly in the claim header.

UPDATE benefit_policy_rules
SET encounter_type = 'OUTPATIENT',
    updated_at = NOW()
WHERE encounter_type = 'EMERGENCY';

UPDATE medical_category_contexts
SET context_type = 'OUTPATIENT',
    is_default = TRUE,
    updated_at = NOW()
WHERE context_type = 'EMERGENCY';
