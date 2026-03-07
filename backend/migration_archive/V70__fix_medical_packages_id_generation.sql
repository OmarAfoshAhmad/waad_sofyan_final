-- V70: Fix medical_packages.id auto-generation for JPA IDENTITY
--
-- Root cause:
-- medical_packages.id has no default, causing INSERT failures with:
-- "null value in column \"id\" of relation \"medical_packages\""

CREATE SEQUENCE IF NOT EXISTS medical_packages_id_seq;

SELECT setval(
    'medical_packages_id_seq',
    COALESCE((SELECT MAX(id) FROM medical_packages), 0) + 1,
    false
);

ALTER TABLE medical_packages
    ALTER COLUMN id SET DEFAULT nextval('medical_packages_id_seq');
