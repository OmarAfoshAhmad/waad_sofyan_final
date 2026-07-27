-- ============================================================
-- V119: Drop legacy_provider_contracts
-- ============================================================
-- SECTION_02 finding (provider & contracts module closure, 2026-07-27):
-- this flat provider+serviceCode+price table was a separate, parallel
-- contract system alongside the modern provider_contracts +
-- provider_contract_pricing_items tables. Investigation confirmed the real
-- claim/pre-auth pricing engine (getEffectivePrice) already read exclusively
-- from the modern tables — anything written to legacy_provider_contracts via
-- POST/PUT/DELETE /providers/{id}/contracts was silently invisible to actual
-- pricing. The table held 0 rows (V110 had previously declined to drop it
-- only because the JPA entity still mapped it). All legacy CRUD/list
-- endpoints, the entity, and the repository have been removed in the same
-- change as this migration; the frontend never called them (confirmed with
-- a full grep for the route pattern).

DROP TABLE IF EXISTS legacy_provider_contracts;
