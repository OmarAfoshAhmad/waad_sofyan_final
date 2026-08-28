-- S-01 / Phase 2 — remove the privileged default from the identity column.
-- Existing rows and NOT NULL are deliberately preserved; only the implicit
-- DATA_ENTRY grant is removed so every account creation must name its role.
ALTER TABLE users ALTER COLUMN user_type DROP DEFAULT;
