-- Repair legacy-import drift where the contract header carries a non-zero
-- discount but its initial effective term remained at zero. Referenced terms
-- are immutable financial history and are deliberately excluded.
UPDATE provider_contract_terms t
SET discount_percent = c.discount_percent,
    change_reason = CONCAT(COALESCE(t.change_reason || '; ', ''),
        'Aligned unreferenced legacy term with contract discount'),
    updated_at = CURRENT_TIMESTAMP
FROM provider_contracts c
WHERE c.id = t.contract_id
  AND t.discount_percent = 0
  AND c.discount_percent > 0
  AND NOT EXISTS (SELECT 1 FROM claims cl WHERE cl.contract_terms_id = t.id)
  AND NOT EXISTS (SELECT 1 FROM claim_lines ln WHERE ln.contract_terms_id = t.id);
