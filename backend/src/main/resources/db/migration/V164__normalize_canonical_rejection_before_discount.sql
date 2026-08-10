-- WAAD has one canonical settlement order: explicit rejection first, then the
-- provider contractual discount. The historical timing flag is retained only
-- for schema/API compatibility and must no longer select a formula.
UPDATE provider_contracts
SET discount_before_rejection = FALSE
WHERE discount_before_rejection IS DISTINCT FROM FALSE;

UPDATE provider_contract_terms
SET discount_before_rejection = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE discount_before_rejection IS DISTINCT FROM FALSE;

-- Repair the specific legacy-import drift where the contract header already
-- carries a non-zero rate but its initial effective term remained at zero.
-- Never rewrite a term referenced by a claim/line: historical financial
-- snapshots remain immutable and auditable.
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
