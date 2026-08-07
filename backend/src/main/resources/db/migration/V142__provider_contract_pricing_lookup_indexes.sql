-- Phase 8 (performance, no behavior change) — the claim/preauth service-lookup
-- queries (findAllServicesByProvider, findServicesByProviderAndCategory,
-- findDistinctCategoriesByProvider) filter provider_contracts by
-- (provider_id, active, status) and provider_contract_pricing_items by
-- (contract_id, active) together. Only single-column indexes existed on each
-- column separately (V12), forcing a bitmap AND instead of a single index scan
-- on what is a hot path (claim/preauth entry UI).
CREATE INDEX IF NOT EXISTS idx_contracts_provider_active_status
    ON provider_contracts(provider_id, active, status);

CREATE INDEX IF NOT EXISTS idx_pricing_contract_active
    ON provider_contract_pricing_items(contract_id, active);
