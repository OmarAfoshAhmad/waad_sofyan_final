INSERT INTO medical_categories(code, name, name_ar, name_en, parent_id,
                               coverage_percent, deleted, active, created_at, updated_at)
VALUES ('CAT-DENT-COSMETIC', 'أسنان تجميلي غير مغطى', 'أسنان تجميلي غير مغطى',
        'Cosmetic dental - not covered', NULL, 0, false, true, NOW(), NOW())
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name, name_ar = EXCLUDED.name_ar, name_en = EXCLUDED.name_en,
    coverage_percent = 0, active = true, deleted = false, updated_at = NOW();

UPDATE claim_context_source_aliases
SET medical_category_code = 'CAT-DENT-COSMETIC'
WHERE provider_id IS NULL
  AND normalized_alias IN ('اسنان تجميلي', 'الاسنان التجميلي', 'طب اسنان تجميلي');

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM claim_lines cl
        JOIN price_list_classification_items i ON i.posted_pricing_item_id = cl.pricing_item_id
        WHERE lower(btrim(i.source_classification)) IN ('اسنان تجميلي', 'أسنان تجميلي')
    ) THEN
        RAISE EXCEPTION 'Cannot reclassify cosmetic dental pricing: historical claim lines reference it';
    END IF;
END $$;

UPDATE price_list_classification_items i
SET medical_category_id = c.id,
    medical_category_code = c.code,
    medical_category_name = c.name_ar,
    classification_reason = 'تصنيف مصدر صريح: أسنان تجميلي غير مغطى',
    updated_at = NOW()
FROM medical_categories c
WHERE c.code = 'CAT-DENT-COSMETIC'
  AND lower(btrim(i.source_classification)) IN ('اسنان تجميلي', 'أسنان تجميلي');

UPDATE provider_contract_pricing_items p
SET medical_category_id = c.id,
    category_name = c.name_ar,
    updated_at = NOW()
FROM price_list_classification_items i, medical_categories c
WHERE i.posted_pricing_item_id = p.id
  AND c.code = 'CAT-DENT-COSMETIC'
  AND lower(btrim(i.source_classification)) IN ('اسنان تجميلي', 'أسنان تجميلي');

COMMENT ON COLUMN price_list_classification_items.source_classification IS
    'Provider-owned classification. Cosmetic dental must remain distinct from covered advanced dental.';
