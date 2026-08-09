CREATE TABLE claim_pending_services (
    id BIGSERIAL PRIMARY KEY,
    claim_id BIGINT NOT NULL REFERENCES claims(id) ON DELETE RESTRICT,
    provider_id BIGINT NOT NULL REFERENCES providers(id) ON DELETE RESTRICT,
    proposed_service_code VARCHAR(50),
    proposed_service_name VARCHAR(255) NOT NULL,
    proposed_category_id BIGINT NOT NULL REFERENCES medical_categories(id) ON DELETE RESTRICT,
    proposed_unit_price NUMERIC(15,2) NOT NULL CHECK (proposed_unit_price > 0),
    status VARCHAR(40) NOT NULL DEFAULT 'PRELIMINARY' CHECK (status IN (
        'PRELIMINARY','NEEDS_INFO','SPLIT_REQUIRED','APPROVED_CLAIM_ONLY',
        'APPROVED_FOR_CONTRACT','LINKED_EXISTING','REJECTED'
    )),
    dictionary_release_id BIGINT REFERENCES medical_dictionary_releases(id) ON DELETE RESTRICT,
    dictionary_version VARCHAR(40),
    dictionary_concept_code VARCHAR(100),
    classification_method VARCHAR(80),
    classification_reason VARCHAR(2000),
    classification_evidence_id BIGINT,
    final_service_code VARCHAR(50),
    final_service_name VARCHAR(255),
    final_category_id BIGINT REFERENCES medical_categories(id) ON DELETE RESTRICT,
    final_unit_price NUMERIC(15,2),
    linked_pricing_item_id BIGINT REFERENCES provider_contract_pricing_items(id) ON DELETE RESTRICT,
    decision_reason VARCHAR(2000),
    entered_by BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    decided_by BIGINT REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_pending_service_code_or_name CHECK (
        NULLIF(BTRIM(proposed_service_name), '') IS NOT NULL
    ),
    CONSTRAINT chk_pending_service_v50_evidence CHECK (
        (dictionary_release_id IS NULL AND dictionary_version IS NULL)
        OR (dictionary_release_id IS NOT NULL AND dictionary_version IS NOT NULL
            AND classification_method IS NOT NULL)
    ),
    CONSTRAINT chk_pending_service_decision_shape CHECK (
        (status IN ('PRELIMINARY','NEEDS_INFO','SPLIT_REQUIRED')
            AND decided_by IS NULL AND decided_at IS NULL)
        OR
        (status IN ('APPROVED_CLAIM_ONLY','APPROVED_FOR_CONTRACT','LINKED_EXISTING','REJECTED')
            AND decided_by IS NOT NULL AND decided_at IS NOT NULL
            AND NULLIF(BTRIM(decision_reason), '') IS NOT NULL)
    ),
    CONSTRAINT chk_pending_service_linked_price CHECK (
        status NOT IN ('LINKED_EXISTING','APPROVED_FOR_CONTRACT') OR linked_pricing_item_id IS NOT NULL
    ),
    CONSTRAINT chk_pending_service_final_values CHECK (
        status NOT IN ('APPROVED_CLAIM_ONLY','APPROVED_FOR_CONTRACT','LINKED_EXISTING')
        OR (NULLIF(BTRIM(final_service_name), '') IS NOT NULL
            AND final_category_id IS NOT NULL
            AND final_unit_price > 0)
    )
);

CREATE INDEX idx_claim_pending_services_claim_status
    ON claim_pending_services(claim_id, status);
CREATE INDEX idx_claim_pending_services_provider
    ON claim_pending_services(provider_id, created_at);

CREATE TABLE claim_pending_service_decisions (
    id BIGSERIAL PRIMARY KEY,
    pending_service_id BIGINT NOT NULL REFERENCES claim_pending_services(id) ON DELETE RESTRICT,
    from_status VARCHAR(40) NOT NULL,
    to_status VARCHAR(40) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    actor_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    snapshot_json JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE OR REPLACE FUNCTION prevent_pending_service_decision_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'claim pending service decisions are append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_pending_service_decisions_append_only
    BEFORE UPDATE OR DELETE ON claim_pending_service_decisions
    FOR EACH ROW EXECUTE FUNCTION prevent_pending_service_decision_mutation();

ALTER TABLE claim_lines
    ADD COLUMN pending_service_id BIGINT REFERENCES claim_pending_services(id) ON DELETE RESTRICT;

CREATE INDEX idx_claim_lines_pending_service
    ON claim_lines(pending_service_id) WHERE pending_service_id IS NOT NULL;

CREATE UNIQUE INDEX ux_claim_lines_pending_service
    ON claim_lines(pending_service_id) WHERE pending_service_id IS NOT NULL;
