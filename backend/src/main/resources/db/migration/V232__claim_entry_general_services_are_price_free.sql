UPDATE medical_services
SET pricing_mode = 'CLAIM_UNIT_PRICE',
    base_price = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE code LIKE 'SYS-CLAIM-%';

COMMENT ON COLUMN medical_services.pricing_mode IS
    'CONTRACT_PRICE uses provider-contract pricing. MANUAL_AMOUNT is invoice total entry. CLAIM_UNIT_PRICE is entered per claim as a unit price without fixed catalog/provider price.';
