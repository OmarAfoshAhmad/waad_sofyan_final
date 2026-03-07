-- Allow modern MedicalCategory entity inserts to succeed.
-- Legacy columns category_name/category_code are no longer populated by entity.

ALTER TABLE medical_categories
    ALTER COLUMN category_name DROP NOT NULL,
    ALTER COLUMN category_code DROP NOT NULL;
