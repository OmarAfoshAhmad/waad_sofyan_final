-- Fix medical_categories.id auto-generation for JPA IDENTITY

CREATE SEQUENCE IF NOT EXISTS medical_categories_id_seq;

SELECT setval(
    'medical_categories_id_seq',
    COALESCE((SELECT MAX(id) FROM medical_categories), 0) + 1,
    false
);

ALTER TABLE medical_categories
    ALTER COLUMN id SET DEFAULT nextval('medical_categories_id_seq');
