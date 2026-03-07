-- Fix medical_services inserts from modern MedicalService entity
-- 1) Restore ID auto-generation expected by @GeneratedValue(IDENTITY)
-- 2) Relax legacy NOT NULL columns not populated by current entity

CREATE SEQUENCE IF NOT EXISTS medical_services_id_seq;

SELECT setval(
    'medical_services_id_seq',
    COALESCE((SELECT MAX(id) FROM medical_services), 0) + 1,
    false
);

ALTER TABLE medical_services
    ALTER COLUMN id SET DEFAULT nextval('medical_services_id_seq');

ALTER TABLE medical_services
    ALTER COLUMN service_name DROP NOT NULL,
    ALTER COLUMN service_code DROP NOT NULL;
