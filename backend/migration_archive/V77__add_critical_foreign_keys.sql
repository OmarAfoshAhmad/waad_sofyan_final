-- ============================================================================
-- V77: Add Critical Foreign Key Constraints
-- ============================================================================
-- Phase 2B: Enforce referential integrity on core financial relationships
--
-- ALL constraints use ON DELETE RESTRICT to prevent accidental data loss.
-- Nullable FK columns use standard FK behavior (NULL values are allowed,
-- but non-NULL values MUST reference a valid parent row).
--
-- SAFE: These are ADD CONSTRAINT only. No data modification.
-- PREREQUISITE: No orphan data exists in these columns (clean schema).
-- ============================================================================

-- ═══════════════════════════════════════════════════════════════════════════
-- 1. claims.settlement_batch_id → settlement_batches(id)
--    Nullable: YES (claims not yet batched have NULL)
--    Rationale: Prevent deletion of settlement batches with linked claims
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE claims
    ADD CONSTRAINT fk_claim_settlement_batch
    FOREIGN KEY (settlement_batch_id) 
    REFERENCES settlement_batches(id)
    ON DELETE RESTRICT;

-- ═══════════════════════════════════════════════════════════════════════════
-- 2. claims.pre_authorization_id → pre_authorizations(id)
--    Nullable: YES (not all claims require pre-authorization)
--    Rationale: Prevent deletion of pre-authorizations with linked claims
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE claims
    ADD CONSTRAINT fk_claim_pre_authorization
    FOREIGN KEY (pre_authorization_id) 
    REFERENCES pre_authorizations(id)
    ON DELETE RESTRICT;

-- ═══════════════════════════════════════════════════════════════════════════
-- 3. pre_authorizations.member_id → members(id)
--    Nullable: NO (every pre-authorization MUST have a member)
--    Rationale: Prevent deletion of members with pending pre-authorizations
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE pre_authorizations
    ADD CONSTRAINT fk_preauth_member
    FOREIGN KEY (member_id) 
    REFERENCES members(id)
    ON DELETE RESTRICT;

-- ═══════════════════════════════════════════════════════════════════════════
-- 4. pre_authorizations.provider_id → providers(id)
--    Nullable: NO (every pre-authorization MUST have a provider)
--    Rationale: Prevent deletion of providers with linked pre-authorizations
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE pre_authorizations
    ADD CONSTRAINT fk_preauth_provider
    FOREIGN KEY (provider_id) 
    REFERENCES providers(id)
    ON DELETE RESTRICT;

-- ═══════════════════════════════════════════════════════════════════════════
-- 5. claim_audit_logs.claim_id → claims(id)
--    Nullable: NO (every audit log MUST reference a claim)
--    Rationale: Prevent deletion of claims with audit trail
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE claim_audit_logs
    ADD CONSTRAINT fk_claim_audit_claim
    FOREIGN KEY (claim_id) 
    REFERENCES claims(id)
    ON DELETE RESTRICT;

-- ============================================================================
-- VERIFICATION: After migration, run:
-- SELECT tc.table_name, kcu.column_name, ccu.table_name AS parent_table,
--        tc.constraint_name
-- FROM information_schema.table_constraints tc
-- JOIN information_schema.key_column_usage kcu 
--     ON tc.constraint_name = kcu.constraint_name
-- JOIN information_schema.constraint_column_usage ccu 
--     ON tc.constraint_name = ccu.constraint_name
-- WHERE tc.constraint_type = 'FOREIGN KEY'
-- AND tc.constraint_name IN ('fk_claim_settlement_batch', 
--     'fk_claim_pre_authorization', 'fk_preauth_member', 
--     'fk_preauth_provider', 'fk_claim_audit_claim');
-- ============================================================================
