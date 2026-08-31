INSERT INTO medical_category_contexts
    (category_id, context_type, is_default, is_active, created_at, updated_at)
SELECT id, 'INPATIENT', false, true, NOW(), NOW()
FROM medical_categories
WHERE code IN ('CAT-DRUG-GENERAL', 'CAT-AMBULANCE', 'CAT-HOME-NURSING')
ON CONFLICT (category_id, context_type) DO UPDATE SET
    is_active = true,
    updated_at = NOW();

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM medical_category_contexts c
        JOIN medical_categories m ON m.id = c.category_id
        WHERE m.code IN ('CAT-DRUG-GENERAL', 'CAT-AMBULANCE', 'CAT-HOME-NURSING')
          AND c.context_type = 'INPATIENT' AND c.is_active) <> 3 THEN
        RAISE EXCEPTION 'Jaliana inpatient benefit contexts were not configured completely';
    END IF;
END $$;
