-- Excel stores 10% as the numeric fraction 0.1. The WAAD domain stores
-- percentages on the explicit 0..100 scale. Correct only fractional positive
-- legacy values; exact 1.00 remains a legitimate 1% contract.
UPDATE provider_contracts
SET discount_percent = discount_percent * 100
WHERE discount_percent > 0 AND discount_percent < 1;

UPDATE provider_contract_terms
SET discount_percent = discount_percent * 100
WHERE discount_percent > 0 AND discount_percent < 1;

UPDATE provider_contract_pricing_items
SET discount_percent = discount_percent * 100
WHERE discount_percent > 0 AND discount_percent < 1;
