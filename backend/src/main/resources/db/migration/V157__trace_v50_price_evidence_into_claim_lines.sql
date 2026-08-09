ALTER TABLE provider_contract_pricing_items
    ADD COLUMN source_price_list_item_id BIGINT REFERENCES price_list_classification_items(id) ON DELETE RESTRICT,
    ADD COLUMN dictionary_release_id BIGINT REFERENCES medical_dictionary_releases(id) ON DELETE RESTRICT,
    ADD COLUMN dictionary_version VARCHAR(40),
    ADD COLUMN dictionary_concept_code VARCHAR(100),
    ADD COLUMN classification_method_v50 VARCHAR(80),
    ADD COLUMN classification_evidence_id BIGINT;

ALTER TABLE provider_contract_pricing_items
    ADD CONSTRAINT chk_provider_price_v50_evidence_complete CHECK (
        (dictionary_release_id IS NULL AND dictionary_version IS NULL)
        OR
        (dictionary_release_id IS NOT NULL AND dictionary_version IS NOT NULL
         AND classification_method_v50 IS NOT NULL)
    );

CREATE INDEX idx_provider_price_dictionary_release
    ON provider_contract_pricing_items(dictionary_release_id, dictionary_concept_code);
CREATE INDEX idx_provider_price_source_list_item
    ON provider_contract_pricing_items(source_price_list_item_id);

ALTER TABLE claim_lines
    ADD COLUMN pricing_effective_from DATE,
    ADD COLUMN pricing_effective_to DATE,
    ADD COLUMN dictionary_release_id BIGINT REFERENCES medical_dictionary_releases(id) ON DELETE RESTRICT,
    ADD COLUMN dictionary_version VARCHAR(40),
    ADD COLUMN dictionary_concept_code VARCHAR(100),
    ADD COLUMN classification_method_v50 VARCHAR(80),
    ADD COLUMN classification_evidence_id BIGINT;

ALTER TABLE claim_lines
    ADD CONSTRAINT chk_claim_line_v50_evidence_complete CHECK (
        (dictionary_release_id IS NULL AND dictionary_version IS NULL)
        OR
        (dictionary_release_id IS NOT NULL AND dictionary_version IS NOT NULL
         AND classification_method_v50 IS NOT NULL)
    );

CREATE INDEX idx_claim_line_dictionary_release
    ON claim_lines(dictionary_release_id, dictionary_concept_code);
