-- Fix IDENTITY generation for tables used by POST /employers and POST /canonical-services

CREATE SEQUENCE IF NOT EXISTS employers_id_seq;

SELECT setval(
    'employers_id_seq',
    COALESCE((SELECT MAX(id) FROM employers), 0) + 1,
    false
);

ALTER TABLE employers
    ALTER COLUMN id SET DEFAULT nextval('employers_id_seq');

CREATE SEQUENCE IF NOT EXISTS canonical_medical_services_id_seq;

SELECT setval(
    'canonical_medical_services_id_seq',
    COALESCE((SELECT MAX(id) FROM canonical_medical_services), 0) + 1,
    false
);

ALTER TABLE canonical_medical_services
    ALTER COLUMN id SET DEFAULT nextval('canonical_medical_services_id_seq');
