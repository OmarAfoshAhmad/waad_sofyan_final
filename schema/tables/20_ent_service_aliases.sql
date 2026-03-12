-- ============================================================
-- Table: ent_service_aliases
-- Depends on: medical_services
-- ============================================================
CREATE TABLE IF NOT EXISTS ent_service_aliases (
    id                 BIGINT PRIMARY KEY DEFAULT nextval('ent_service_alias_seq'),
    medical_service_id BIGINT NOT NULL,
    alias_text         VARCHAR(255) NOT NULL,
    locale             VARCHAR(10) NOT NULL DEFAULT 'ar',
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by         VARCHAR(255),

    CONSTRAINT fk_alias_service FOREIGN KEY (medical_service_id)
        REFERENCES medical_services(id) ON DELETE CASCADE,
    CONSTRAINT uq_alias_text_per_service_locale UNIQUE (medical_service_id, alias_text, locale)
);

CREATE INDEX IF NOT EXISTS idx_aliases_service_id             ON ent_service_aliases(medical_service_id);
CREATE INDEX IF NOT EXISTS idx_aliases_text                   ON ent_service_aliases(alias_text);
CREATE INDEX IF NOT EXISTS idx_aliases_locale                 ON ent_service_aliases(locale);
CREATE INDEX IF NOT EXISTS idx_ent_service_aliases_text_lower ON ent_service_aliases(LOWER(alias_text));
CREATE INDEX IF NOT EXISTS idx_ent_service_aliases_service_id ON ent_service_aliases(medical_service_id);
