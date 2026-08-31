-- A browser may lose the response after the transaction commits and retry the
-- exact same direct-entry command. Persist the caller key on the resulting
-- claim so the retry returns that claim instead of manufacturing a second
-- visit and a second financial event.
ALTER TABLE claims
    ADD COLUMN direct_entry_idempotency_key VARCHAR(120),
    ADD COLUMN direct_entry_request_fingerprint VARCHAR(64);

CREATE UNIQUE INDEX ux_claims_direct_entry_idempotency
    ON claims (direct_entry_idempotency_key)
    WHERE direct_entry_idempotency_key IS NOT NULL;

ALTER TABLE claims ADD CONSTRAINT chk_claims_direct_entry_idempotency_pair
    CHECK ((direct_entry_idempotency_key IS NULL) = (direct_entry_request_fingerprint IS NULL));

COMMENT ON COLUMN claims.direct_entry_idempotency_key IS
    'Client command key for atomic direct visit+claim entry; write-once.';
COMMENT ON COLUMN claims.direct_entry_request_fingerprint IS
    'SHA-256 of the direct-entry request; prevents reusing one key for different data.';

-- ClaimService performs the canonical claim insert. The direct-entry boundary
-- attaches the command identity immediately afterwards in the same transaction.
-- Permit precisely that NULL -> pair transition and reject every later rewrite.
CREATE OR REPLACE FUNCTION guard_claim_direct_entry_identity_write_once()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.direct_entry_idempotency_key IS NOT NULL
       AND (NEW.direct_entry_idempotency_key IS DISTINCT FROM OLD.direct_entry_idempotency_key
            OR NEW.direct_entry_request_fingerprint IS DISTINCT FROM OLD.direct_entry_request_fingerprint) THEN
        RAISE EXCEPTION 'direct claim entry identity is write-once';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_claim_direct_entry_identity_write_once
    BEFORE UPDATE OF direct_entry_idempotency_key, direct_entry_request_fingerprint ON claims
    FOR EACH ROW
    EXECUTE FUNCTION guard_claim_direct_entry_identity_write_once();
