ALTER TABLE price_list_classification_items
    ADD COLUMN dictionary_release_id BIGINT REFERENCES medical_dictionary_releases(id) ON DELETE RESTRICT,
    ADD COLUMN dictionary_version VARCHAR(40),
    ADD COLUMN dictionary_concept_code VARCHAR(100),
    ADD COLUMN classification_method VARCHAR(80),
    ADD COLUMN classification_reason VARCHAR(2000),
    ADD COLUMN classification_exception_type VARCHAR(100),
    ADD COLUMN classification_evidence_id BIGINT,
    ADD COLUMN classification_exclude_precision BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_price_list_item_dictionary_release
    ON price_list_classification_items(dictionary_release_id);
CREATE INDEX idx_price_list_item_dictionary_concept
    ON price_list_classification_items(dictionary_release_id, dictionary_concept_code);

ALTER TABLE price_list_classification_items ADD CONSTRAINT chk_price_list_dictionary_evidence
    CHECK (
        (dictionary_release_id IS NULL AND dictionary_version IS NULL)
        OR
        (dictionary_release_id IS NOT NULL AND dictionary_version IS NOT NULL
         AND classification_method IS NOT NULL AND classification_reason IS NOT NULL)
    );
