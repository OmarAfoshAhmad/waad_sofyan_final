-- Restore the specialty lookup expected by MedicalSpecialty and MedicalService.
CREATE TABLE IF NOT EXISTS medical_specialties (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(50) NOT NULL,
    name_ar     VARCHAR(255) NOT NULL,
    name_en     VARCHAR(255),
    category_id BIGINT,
    deleted     BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_medical_specialties_code UNIQUE (code),
    CONSTRAINT fk_medical_specialties_category
        FOREIGN KEY (category_id) REFERENCES medical_categories(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_medical_specialties_category_active
    ON medical_specialties(category_id, deleted);

ALTER TABLE medical_services
    ADD COLUMN IF NOT EXISTS specialty_id BIGINT;

ALTER TABLE medical_services
    DROP CONSTRAINT IF EXISTS fk_medical_services_specialty;

ALTER TABLE medical_services
    ADD CONSTRAINT fk_medical_services_specialty
        FOREIGN KEY (specialty_id) REFERENCES medical_specialties(id) ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_medical_services_specialty
    ON medical_services(specialty_id)
    WHERE specialty_id IS NOT NULL;
