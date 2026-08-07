CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE provider_contract_terms (
    id BIGSERIAL PRIMARY KEY,
    contract_id BIGINT NOT NULL REFERENCES provider_contracts(id) ON DELETE RESTRICT,
    effective_from DATE NOT NULL,
    effective_to DATE,
    discount_percent NUMERIC(5,2) NOT NULL CHECK (discount_percent BETWEEN 0 AND 100),
    discount_before_rejection BOOLEAN NOT NULL,
    change_reason VARCHAR(1000),
    approved_by VARCHAR(150),
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_provider_contract_terms_period
        CHECK (effective_to IS NULL OR effective_to > effective_from),
    CONSTRAINT ex_provider_contract_terms_no_overlap
        EXCLUDE USING gist (
            contract_id WITH =,
            daterange(effective_from, effective_to, '[)') WITH &&
        )
);

CREATE INDEX idx_provider_contract_terms_effective
    ON provider_contract_terms(contract_id, effective_from, effective_to);

INSERT INTO provider_contract_terms (
    contract_id, effective_from, effective_to, discount_percent,
    discount_before_rejection, change_reason, approved_by, approved_at
)
SELECT id, start_date, NULL, COALESCE(discount_percent, 0),
       COALESCE(discount_before_rejection, FALSE),
       'Initial terms migrated from provider contract', created_by, created_at
FROM provider_contracts;

ALTER TABLE claims ADD COLUMN provider_contract_id BIGINT;
ALTER TABLE claims ADD COLUMN contract_terms_id BIGINT;
ALTER TABLE claims ADD COLUMN financial_calculated_at TIMESTAMP;
ALTER TABLE claims ADD CONSTRAINT fk_claim_provider_contract
    FOREIGN KEY (provider_contract_id) REFERENCES provider_contracts(id) ON DELETE RESTRICT;
ALTER TABLE claims ADD CONSTRAINT fk_claim_contract_terms
    FOREIGN KEY (contract_terms_id) REFERENCES provider_contract_terms(id) ON DELETE RESTRICT;
CREATE INDEX idx_claims_provider_contract ON claims(provider_contract_id);
CREATE INDEX idx_claims_contract_terms ON claims(contract_terms_id);

ALTER TABLE claim_lines ADD COLUMN contract_terms_id BIGINT;
ALTER TABLE claim_lines ADD COLUMN contract_unit_price NUMERIC(15,2);
ALTER TABLE claim_lines ADD CONSTRAINT fk_claim_line_contract_terms
    FOREIGN KEY (contract_terms_id) REFERENCES provider_contract_terms(id) ON DELETE RESTRICT;
CREATE INDEX idx_claim_lines_contract_terms ON claim_lines(contract_terms_id);
