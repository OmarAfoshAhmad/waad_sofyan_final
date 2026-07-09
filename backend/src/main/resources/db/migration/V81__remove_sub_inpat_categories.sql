-- =========================================================================
-- V81: Remove SUB-INPAT sub-categories as requested by user
-- Description: Replaces SUB-INPAT-% with CAT-IP in classifications/claims,
--              deletes their policy rules to hide them from the UI, and
--              soft-deletes the categories themselves.
-- =========================================================================

-- 1. Replace in service_specialty_insurance_map
UPDATE service_specialty_insurance_map
SET insurance_category_code = 'CAT-IP'
WHERE insurance_category_code LIKE 'SUB-INPAT-%';

-- 2. Update claim_lines
UPDATE claim_lines
SET applied_category_id = (SELECT id FROM medical_categories WHERE code = 'CAT-IP' LIMIT 1)
WHERE applied_category_id IN (
    SELECT id FROM medical_categories WHERE code LIKE 'SUB-INPAT-%'
);

UPDATE claim_lines
SET service_category_id = (SELECT id FROM medical_categories WHERE code = 'CAT-IP' LIMIT 1)
WHERE service_category_id IN (
    SELECT id FROM medical_categories WHERE code LIKE 'SUB-INPAT-%'
);

-- 3. Update provider_contract_pricing_items
UPDATE provider_contract_pricing_items
SET medical_category_id = (SELECT id FROM medical_categories WHERE code = 'CAT-IP' LIMIT 1)
WHERE medical_category_id IN (
    SELECT id FROM medical_categories WHERE code LIKE 'SUB-INPAT-%'
);

-- 4. Update visits
UPDATE visits
SET medical_category_id = (SELECT id FROM medical_categories WHERE code = 'CAT-IP' LIMIT 1)
WHERE medical_category_id IN (
    SELECT id FROM medical_categories WHERE code LIKE 'SUB-INPAT-%'
);

-- 5. Delete policy rules (this removes them from the UI for existing policies)
DELETE FROM benefit_policy_rules
WHERE medical_category_id IN (
    SELECT id FROM medical_categories WHERE code LIKE 'SUB-INPAT-%'
);

DELETE FROM benefit_policy_template_rules
WHERE medical_category_id IN (
    SELECT id FROM medical_categories WHERE code LIKE 'SUB-INPAT-%'
);

-- 6. Delete from medical_category_roots
DELETE FROM medical_category_roots
WHERE category_id IN (SELECT id FROM medical_categories WHERE code LIKE 'SUB-INPAT-%')
   OR root_id IN (SELECT id FROM medical_categories WHERE code LIKE 'SUB-INPAT-%');

-- 7. Soft delete the categories
UPDATE medical_categories
SET active = false, deleted = true, deleted_at = CURRENT_TIMESTAMP
WHERE code LIKE 'SUB-INPAT-%';
