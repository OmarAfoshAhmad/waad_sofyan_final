-- V232 existed in an intermediate review build where claim-entry custom
-- services were stored as shared medical catalog rows. The final workflow
-- stores new claim-entry services on the active provider contract with
-- provider_contract_pricing_items.pricing_mode = 'CLAIM_UNIT_PRICE'.
--
-- Keep any historical SYS-CLAIM catalog rows read-only/inactive so they do
-- not reappear as global services or force a fixed price path.
UPDATE medical_services
SET active = false,
    updated_at = CURRENT_TIMESTAMP
WHERE code LIKE 'SYS-CLAIM-%';
