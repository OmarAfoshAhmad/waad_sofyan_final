-- =================================================================================
-- V43: Map Unmapped Pricing Items to Medical Categories
-- Description: Unifies text-based category names into foreign key relations to 
-- avoid performance impact from runtime fuzzy matching.
-- =================================================================================

-- 1. Exact Name Matching
UPDATE provider_contract_pricing_items p
SET medical_category_id = c.id
FROM medical_categories c
WHERE p.medical_category_id IS NULL 
  AND p.category_name IS NOT NULL
  AND TRIM(p.category_name) = c.name;

-- 2. Fuzzy Match: Strip parenthetical suffixes (e.g., " (IP)" or " (OP)") and re-match
UPDATE provider_contract_pricing_items p
SET medical_category_id = c.id
FROM medical_categories c
WHERE p.medical_category_id IS NULL 
  AND p.category_name IS NOT NULL
  AND TRIM(REGEXP_REPLACE(p.category_name, '\s*\(.*?\)\s*$', '')) = c.name;

