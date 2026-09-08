ALTER TABLE provider_contract_pricing_items
    ADD COLUMN IF NOT EXISTS pricing_mode VARCHAR(30) NOT NULL DEFAULT 'CONTRACT_PRICE';

UPDATE provider_contract_pricing_items
SET pricing_mode = 'CONTRACT_PRICE'
WHERE pricing_mode IS NULL OR btrim(pricing_mode) = '';

UPDATE provider_contract_pricing_items
SET pricing_mode = 'CLAIM_UNIT_PRICE',
    base_price = 0,
    contract_price = 0,
    max_contract_price = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE active = true
  AND service_code LIKE 'SYS-CLAIM-%';

COMMENT ON COLUMN provider_contract_pricing_items.pricing_mode IS
    'CONTRACT_PRICE uses contract_price as the accepted unit cap. CLAIM_UNIT_PRICE belongs to the provider contract but the claim line unit price is entered per claim and is not rejected as price excess.';
