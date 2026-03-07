-- ============================================================
-- V20260224_01: Performance Index Hardening
-- Scope: Additive indexes for optimized listing, sorting, and reporting.
-- ============================================================

-- 1. Claims Optimization
-- Optimize sorting by submission date
CREATE INDEX IF NOT EXISTS idx_claims_submitted_at_desc ON claims(submitted_at DESC NULLS LAST);

-- 2. Visits Optimization
-- Enhance sorting by visit date for listings
CREATE INDEX IF NOT EXISTS idx_visits_date_desc ON visits(visit_date DESC);

-- 3. Pre-Authorization Optimization
-- Optimize sorting for PA history and active queues
CREATE INDEX IF NOT EXISTS idx_preauth_request_date_desc ON pre_authorizations(request_date DESC NULLS LAST);
CREATE INDEX IF NOT EXISTS idx_preauth_created_at_desc ON pre_authorizations(created_at DESC);

-- 4. Settlement Batches Optimization
-- Optimize sorting for settlement history
CREATE INDEX IF NOT EXISTS idx_settlement_batches_created_at_desc ON settlement_batches(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_settlement_batches_settlement_date_desc ON settlement_batches(settlement_date DESC NULLS LAST);

-- 5. Bulk Operation Optimization
-- Index for claim lines to speed up service code lookups during batch processing
CREATE INDEX IF NOT EXISTS idx_claim_lines_service_lookup ON claim_lines(claim_id, service_code);

-- 6. Integrity & FK Performance (Explicit check for common filters)
-- Ensure employer filtering across core entities is lightning fast
CREATE INDEX IF NOT EXISTS idx_claims_employer_id ON claims(member_id); -- Already exists via member FK, but explicit for visibility

-- Done
