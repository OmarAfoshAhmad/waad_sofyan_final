CREATE TABLE medical_dictionary_releases (
    id BIGSERIAL PRIMARY KEY,
    version VARCHAR(40) NOT NULL,
    source_filename VARCHAR(255) NOT NULL,
    source_sha256 CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    category_count INTEGER NOT NULL DEFAULT 0,
    concept_count INTEGER NOT NULL DEFAULT 0,
    alias_count INTEGER NOT NULL DEFAULT 0,
    exception_count INTEGER NOT NULL DEFAULT 0,
    validation_report JSONB,
    failure_reason VARCHAR(2000),
    created_by BIGINT,
    validated_by BIGINT,
    activated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    validated_at TIMESTAMP,
    activated_at TIMESTAMP,
    CONSTRAINT uk_med_dictionary_release_version UNIQUE (version),
    CONSTRAINT uk_med_dictionary_release_sha UNIQUE (source_sha256),
    CONSTRAINT chk_med_dictionary_release_status CHECK
        (status IN ('STAGED', 'VALIDATED', 'ACTIVE', 'FAILED', 'RETIRED')),
    CONSTRAINT chk_med_dictionary_release_counts CHECK
        (category_count >= 0 AND concept_count >= 0 AND alias_count >= 0 AND exception_count >= 0)
);

CREATE UNIQUE INDEX ux_med_dictionary_one_active_release
    ON medical_dictionary_releases ((status)) WHERE status = 'ACTIVE';

CREATE TABLE medical_dictionary_release_categories (
    id BIGSERIAL PRIMARY KEY,
    release_id BIGINT NOT NULL REFERENCES medical_dictionary_releases(id) ON DELETE RESTRICT,
    category_code VARCHAR(100) NOT NULL,
    name_ar VARCHAR(300) NOT NULL,
    name_en VARCHAR(300),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_med_dictionary_release_category UNIQUE (release_id, category_code)
);

CREATE TABLE medical_dictionary_concepts_v2 (
    id BIGSERIAL PRIMARY KEY,
    release_id BIGINT NOT NULL REFERENCES medical_dictionary_releases(id) ON DELETE RESTRICT,
    concept_code VARCHAR(100) NOT NULL,
    dictionary_type VARCHAR(100),
    name_ar VARCHAR(500),
    name_en VARCHAR(500),
    abbreviation VARCHAR(100),
    normalized_ar VARCHAR(500),
    normalized_en VARCHAR(500),
    token_key VARCHAR(1000),
    specialty VARCHAR(200),
    procedure_type VARCHAR(200),
    category_code VARCHAR(100) NOT NULL,
    parent_context VARCHAR(100),
    context_rule VARCHAR(1000),
    confidence NUMERIC(5,4) NOT NULL,
    production_status VARCHAR(40) NOT NULL,
    auto_approved BOOLEAN NOT NULL DEFAULT FALSE,
    notes VARCHAR(2000),
    CONSTRAINT uk_med_dictionary_release_concept UNIQUE (release_id, concept_code),
    CONSTRAINT fk_med_dictionary_concept_category FOREIGN KEY (release_id, category_code)
        REFERENCES medical_dictionary_release_categories(release_id, category_code) ON DELETE RESTRICT,
    CONSTRAINT chk_med_dictionary_concept_confidence CHECK (confidence BETWEEN 0 AND 1)
);

CREATE INDEX idx_med_dictionary_concept_ar ON medical_dictionary_concepts_v2(release_id, normalized_ar);
CREATE INDEX idx_med_dictionary_concept_en ON medical_dictionary_concepts_v2(release_id, normalized_en);
CREATE INDEX idx_med_dictionary_concept_token ON medical_dictionary_concepts_v2(release_id, token_key);
CREATE INDEX idx_med_dictionary_concept_abbr ON medical_dictionary_concepts_v2(release_id, abbreviation);

CREATE TABLE medical_dictionary_aliases_v2 (
    id BIGSERIAL PRIMARY KEY,
    release_id BIGINT NOT NULL REFERENCES medical_dictionary_releases(id) ON DELETE RESTRICT,
    alias_code VARCHAR(120) NOT NULL,
    raw_name VARCHAR(1000) NOT NULL,
    translation_ar VARCHAR(1000),
    normalized_name VARCHAR(1000) NOT NULL,
    token_key VARCHAR(1500),
    facility_name VARCHAR(500),
    scope_key VARCHAR(500) NOT NULL DEFAULT '*',
    provider_code_normalized VARCHAR(200) NOT NULL DEFAULT '',
    section_normalized VARCHAR(500) NOT NULL DEFAULT '',
    match_scope VARCHAR(40) NOT NULL,
    match_priority INTEGER NOT NULL,
    concept_code VARCHAR(100),
    mapping_level VARCHAR(30) NOT NULL,
    category_code VARCHAR(100) NOT NULL,
    specialty VARCHAR(200),
    procedure_type VARCHAR(200),
    parent_context VARCHAR(100),
    confidence NUMERIC(5,4) NOT NULL,
    production_status VARCHAR(40) NOT NULL,
    auto_approved BOOLEAN NOT NULL DEFAULT FALSE,
    source VARCHAR(500),
    notes VARCHAR(2000),
    CONSTRAINT uk_med_dictionary_release_alias UNIQUE (release_id, alias_code),
    CONSTRAINT uk_med_dictionary_alias_identity UNIQUE
        (release_id, scope_key, provider_code_normalized, section_normalized, normalized_name),
    CONSTRAINT fk_med_dictionary_alias_category FOREIGN KEY (release_id, category_code)
        REFERENCES medical_dictionary_release_categories(release_id, category_code) ON DELETE RESTRICT,
    CONSTRAINT fk_med_dictionary_alias_concept FOREIGN KEY (release_id, concept_code)
        REFERENCES medical_dictionary_concepts_v2(release_id, concept_code) ON DELETE RESTRICT,
    CONSTRAINT chk_med_dictionary_alias_confidence CHECK (confidence BETWEEN 0 AND 1)
);

CREATE INDEX idx_med_dictionary_alias_exact
    ON medical_dictionary_aliases_v2(release_id, scope_key, provider_code_normalized, normalized_name);
CREATE INDEX idx_med_dictionary_alias_name
    ON medical_dictionary_aliases_v2(release_id, normalized_name);
CREATE INDEX idx_med_dictionary_alias_token
    ON medical_dictionary_aliases_v2(release_id, token_key);

CREATE TABLE medical_dictionary_exceptions_v2 (
    id BIGSERIAL PRIMARY KEY,
    release_id BIGINT NOT NULL REFERENCES medical_dictionary_releases(id) ON DELETE RESTRICT,
    exception_code VARCHAR(120) NOT NULL,
    facility_name VARCHAR(500),
    scope_key VARCHAR(500) NOT NULL DEFAULT '*',
    provider_code_normalized VARCHAR(200) NOT NULL DEFAULT '',
    raw_name VARCHAR(1000),
    normalized_name VARCHAR(1000) NOT NULL,
    exception_status VARCHAR(50) NOT NULL,
    exception_type VARCHAR(100),
    routing_action VARCHAR(50) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    exclude_from_precision BOOLEAN NOT NULL DEFAULT TRUE,
    exclude_from_clean_denominator BOOLEAN NOT NULL DEFAULT FALSE,
    source VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_med_dictionary_release_exception UNIQUE (release_id, exception_code),
    CONSTRAINT uk_med_dictionary_exception_identity UNIQUE
        (release_id, scope_key, provider_code_normalized, normalized_name, exception_status)
);

CREATE INDEX idx_med_dictionary_exception_lookup
    ON medical_dictionary_exceptions_v2(release_id, scope_key, provider_code_normalized, normalized_name)
    WHERE active = TRUE;

CREATE OR REPLACE FUNCTION prevent_active_dictionary_mutation()
RETURNS trigger AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM medical_dictionary_releases r
        WHERE r.id = CASE WHEN TG_OP = 'DELETE' THEN OLD.release_id ELSE NEW.release_id END
          AND r.status IN ('ACTIVE', 'RETIRED')
    ) THEN
        RAISE EXCEPTION 'Active or retired medical dictionary releases are immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_dictionary_categories_immutable
    BEFORE UPDATE OR DELETE ON medical_dictionary_release_categories
    FOR EACH ROW EXECUTE FUNCTION prevent_active_dictionary_mutation();
CREATE TRIGGER trg_dictionary_concepts_immutable
    BEFORE UPDATE OR DELETE ON medical_dictionary_concepts_v2
    FOR EACH ROW EXECUTE FUNCTION prevent_active_dictionary_mutation();
CREATE TRIGGER trg_dictionary_aliases_immutable
    BEFORE UPDATE OR DELETE ON medical_dictionary_aliases_v2
    FOR EACH ROW EXECUTE FUNCTION prevent_active_dictionary_mutation();
CREATE TRIGGER trg_dictionary_exceptions_immutable
    BEFORE UPDATE OR DELETE ON medical_dictionary_exceptions_v2
    FOR EACH ROW EXECUTE FUNCTION prevent_active_dictionary_mutation();
