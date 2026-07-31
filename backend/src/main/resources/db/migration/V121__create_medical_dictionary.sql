CREATE TABLE IF NOT EXISTS medical_dictionary_entries (
    id BIGSERIAL PRIMARY KEY,
    canonical_name VARCHAR(300) NOT NULL,
    normalized_canonical_name VARCHAR(300) NOT NULL,
    medical_category_id BIGINT NOT NULL REFERENCES medical_categories(id),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    default_confidence INTEGER NOT NULL DEFAULT 80,
    notes VARCHAR(1000),
    created_by BIGINT,
    approved_by BIGINT,
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_med_dict_entry_normalized UNIQUE (normalized_canonical_name),
    CONSTRAINT chk_med_dict_entry_confidence CHECK (default_confidence BETWEEN 0 AND 100)
);

CREATE INDEX IF NOT EXISTS idx_med_dict_entry_normalized ON medical_dictionary_entries(normalized_canonical_name);
CREATE INDEX IF NOT EXISTS idx_med_dict_entry_category ON medical_dictionary_entries(medical_category_id);
CREATE INDEX IF NOT EXISTS idx_med_dict_entry_status ON medical_dictionary_entries(status);

CREATE TABLE IF NOT EXISTS medical_dictionary_synonyms (
    id BIGSERIAL PRIMARY KEY,
    entry_id BIGINT NOT NULL REFERENCES medical_dictionary_entries(id) ON DELETE CASCADE,
    synonym VARCHAR(300) NOT NULL,
    normalized_synonym VARCHAR(300) NOT NULL,
    synonym_type VARCHAR(30) NOT NULL DEFAULT 'COMMON',
    language VARCHAR(10) NOT NULL DEFAULT 'ar',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    usage_count BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_med_dict_synonym_normalized UNIQUE (normalized_synonym)
);

CREATE INDEX IF NOT EXISTS idx_med_dict_synonym_entry ON medical_dictionary_synonyms(entry_id);
CREATE INDEX IF NOT EXISTS idx_med_dict_synonym_normalized ON medical_dictionary_synonyms(normalized_synonym);
CREATE INDEX IF NOT EXISTS idx_med_dict_synonym_active ON medical_dictionary_synonyms(active);

CREATE TABLE IF NOT EXISTS medical_dictionary_suggestions (
    id BIGSERIAL PRIMARY KEY,
    original_text VARCHAR(500) NOT NULL,
    normalized_original_text VARCHAR(500) NOT NULL,
    suggested_entry_id BIGINT REFERENCES medical_dictionary_entries(id),
    suggested_category_id BIGINT REFERENCES medical_categories(id),
    source VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    confidence INTEGER,
    source_reference VARCHAR(200),
    review_note VARCHAR(1000),
    created_by BIGINT,
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_med_dict_suggestion_confidence CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 100)
);

CREATE INDEX IF NOT EXISTS idx_med_dict_suggestion_normalized ON medical_dictionary_suggestions(normalized_original_text);
CREATE INDEX IF NOT EXISTS idx_med_dict_suggestion_status ON medical_dictionary_suggestions(status);
CREATE INDEX IF NOT EXISTS idx_med_dict_suggestion_source ON medical_dictionary_suggestions(source);
