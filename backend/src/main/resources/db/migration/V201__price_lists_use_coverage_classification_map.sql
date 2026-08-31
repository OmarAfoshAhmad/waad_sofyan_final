INSERT INTO medical_categories(code, name, name_ar, name_en, parent_id,
                               coverage_percent, deleted, active, created_at, updated_at)
VALUES
    ('CAT-COV-OUTPATIENT', 'عيادات خارجية', 'عيادات خارجية', 'Outpatient', NULL, NULL, false, true, NOW(), NOW()),
    ('CAT-COV-INPATIENT', 'إيواء', 'إيواء', 'Inpatient', NULL, NULL, false, true, NOW(), NOW()),
    ('CAT-COV-DIAG-FEES', 'أشعة وتحاليل ورسوم أطباء', 'أشعة وتحاليل ورسوم أطباء',
     'Diagnostics, Laboratory and Professional Fees', NULL, NULL, false, true, NOW(), NOW())
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name, name_ar = EXCLUDED.name_ar, name_en = EXCLUDED.name_en,
    active = true, deleted = false, updated_at = NOW();

UPDATE claim_context_source_aliases
SET medical_category_code = CASE normalized_alias
    WHEN 'ايواء' THEN 'CAT-COV-INPATIENT'
    WHEN 'عيادات خارجيه' THEN 'CAT-COV-OUTPATIENT'
    WHEN 'اشعه تحاليل رسوم اطباء' THEN 'CAT-COV-DIAG-FEES'
    ELSE medical_category_code
END
WHERE provider_id IS NULL
  AND normalized_alias IN ('ايواء', 'عيادات خارجيه', 'اشعه تحاليل رسوم اطباء');

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM claim_context_source_aliases
        WHERE active = true AND requires_review = false AND medical_category_code IS NULL
    ) THEN
        RAISE EXCEPTION 'An auto-approved price-list alias has no coverage category';
    END IF;
END $$;

COMMENT ON COLUMN claim_context_source_aliases.medical_category_code IS
    'Coverage classification selected from the provider source label; no detailed medical dictionary is required.';
