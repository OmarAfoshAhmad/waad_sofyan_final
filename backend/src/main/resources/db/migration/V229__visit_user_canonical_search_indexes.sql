CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_visits_doctor_name_canonical_trgm
    ON visits USING GIN (waad_search_normalize(doctor_name) gin_trgm_ops)
    WHERE doctor_name IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_visits_specialty_canonical_trgm
    ON visits USING GIN (waad_search_normalize(specialty) gin_trgm_ops)
    WHERE specialty IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_visits_diagnosis_canonical_trgm
    ON visits USING GIN (waad_search_normalize(diagnosis) gin_trgm_ops)
    WHERE diagnosis IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_users_username_canonical_trgm
    ON users USING GIN (waad_search_normalize(username) gin_trgm_ops)
    WHERE username IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_users_full_name_canonical_trgm
    ON users USING GIN (waad_search_normalize(full_name) gin_trgm_ops)
    WHERE full_name IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_users_email_canonical_trgm
    ON users USING GIN (waad_search_normalize(email) gin_trgm_ops)
    WHERE email IS NOT NULL;
