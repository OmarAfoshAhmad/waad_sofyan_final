-- Governance trail for reviewer-driven claim classification learning.
-- The financial decision remains owned by the coverage engine; these fields
-- preserve who changed the insurance classification and what it was before.

ALTER TABLE claim_lines
    ADD COLUMN IF NOT EXISTS original_service_category_id BIGINT,
    ADD COLUMN IF NOT EXISTS original_service_category_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS classification_reviewed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS classification_review_source VARCHAR(50),
    ADD COLUMN IF NOT EXISTS classification_reviewed_by BIGINT,
    ADD COLUMN IF NOT EXISTS classification_reviewed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS classification_review_note VARCHAR(1000);

CREATE INDEX IF NOT EXISTS idx_claim_lines_classification_reviewed
    ON claim_lines (classification_reviewed);

CREATE INDEX IF NOT EXISTS idx_claim_lines_classification_reviewed_by
    ON claim_lines (classification_reviewed_by);

ALTER TABLE medical_dictionary_synonyms
    ADD COLUMN IF NOT EXISTS lifecycle_status VARCHAR(30) NOT NULL DEFAULT 'REVIEWER_APPROVED',
    ADD COLUMN IF NOT EXISTS learned_from_source VARCHAR(50),
    ADD COLUMN IF NOT EXISTS source_reference VARCHAR(200),
    ADD COLUMN IF NOT EXISTS approved_by BIGINT,
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS locked_by BIGINT,
    ADD COLUMN IF NOT EXISTS locked_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS disabled_by BIGINT,
    ADD COLUMN IF NOT EXISTS disabled_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS governance_note VARCHAR(1000);

CREATE INDEX IF NOT EXISTS idx_med_dict_synonym_lifecycle_status
    ON medical_dictionary_synonyms (lifecycle_status);

CREATE INDEX IF NOT EXISTS idx_med_dict_synonym_learned_source
    ON medical_dictionary_synonyms (learned_from_source);
