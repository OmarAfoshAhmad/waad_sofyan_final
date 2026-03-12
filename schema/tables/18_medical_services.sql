-- ============================================================
-- Table: medical_services
-- Depends on: medical_categories, medical_specialties
-- ============================================================
CREATE TABLE IF NOT EXISTS medical_services (
    id               BIGINT PRIMARY KEY DEFAULT nextval('medical_service_seq'),

    category_id      BIGINT,
    specialty_id     BIGINT,

    service_name     VARCHAR(255),
    service_name_ar  VARCHAR(255),
    service_code     VARCHAR(50) UNIQUE,

    name             VARCHAR(255),
    name_ar          VARCHAR(255),
    name_en          VARCHAR(255),
    code             VARCHAR(50),

    status           VARCHAR(20) DEFAULT 'ACTIVE',
    description      TEXT,

    base_price       NUMERIC(10,2),
    cost             NUMERIC(15,2),

    is_master        BOOLEAN NOT NULL DEFAULT false,
    requires_pa      BOOLEAN NOT NULL DEFAULT false,

    deleted          BOOLEAN NOT NULL DEFAULT false,
    deleted_at       TIMESTAMP,
    deleted_by       BIGINT,

    active           BOOLEAN DEFAULT true,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),

    CONSTRAINT fk_medical_service_category  FOREIGN KEY (category_id)
        REFERENCES medical_categories(id) ON DELETE RESTRICT,
    CONSTRAINT fk_medical_service_specialty FOREIGN KEY (specialty_id)
        REFERENCES medical_specialties(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_medical_services_category             ON medical_services(category_id);
CREATE INDEX IF NOT EXISTS idx_medical_services_code                 ON medical_services(service_code);
CREATE INDEX IF NOT EXISTS idx_medical_services_active               ON medical_services(active);
CREATE INDEX IF NOT EXISTS idx_medical_services_is_master            ON medical_services(is_master) WHERE deleted = false;
CREATE UNIQUE INDEX IF NOT EXISTS uq_medical_services_code_active    ON medical_services(code) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_medical_services_category_deleted_active ON medical_services(category_id, deleted, active);
CREATE INDEX IF NOT EXISTS idx_medical_services_active_deleted       ON medical_services(active, deleted);
CREATE INDEX IF NOT EXISTS idx_medical_services_code_lower           ON medical_services(LOWER(code));
CREATE INDEX IF NOT EXISTS idx_medical_services_name_ar_lower        ON medical_services(LOWER(name_ar));
CREATE INDEX IF NOT EXISTS idx_medical_services_name_en_lower        ON medical_services(LOWER(name_en));
