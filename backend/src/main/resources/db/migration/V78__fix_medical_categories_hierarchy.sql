-- =================================================================================
-- V78: Fix Medical Categories Hierarchy
-- Description: Assigns proper parent_id to the flat SUB-INPAT categories
--              so they correctly appear as subcategories of "الإيواء" in the UI.
-- =================================================================================

UPDATE medical_categories
SET parent_id = (SELECT id FROM medical_categories WHERE code = 'CAT-IP'),
    name = REPLACE(name, 'الإيواء - ', ''),
    name_ar = REPLACE(name_ar, 'الإيواء - ', '')
WHERE code IN (
    'SUB-INPAT-GENERAL',
    'SUB-INPAT-HOME-NURSING',
    'SUB-INPAT-PHYSIO',
    'SUB-INPAT-WORK-INJ',
    'SUB-INPAT-PSYCH',
    'SUB-INPAT-DELIVERY'
);

-- Clean up and rebuild roots table
DELETE FROM medical_category_roots;

INSERT INTO medical_category_roots (category_id, root_id)
SELECT id, id FROM medical_categories WHERE parent_id IS NULL AND active = true
ON CONFLICT DO NOTHING;

INSERT INTO medical_category_roots (category_id, root_id)
SELECT c.id, c.parent_id
FROM medical_categories c
WHERE c.parent_id IS NOT NULL AND c.active = true
ON CONFLICT DO NOTHING;
