CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_claims_provider_name_canonical_trgm
    ON claims USING GIN (waad_search_normalize(provider_name) gin_trgm_ops)
    WHERE provider_name IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_claims_diagnosis_description_canonical_trgm
    ON claims USING GIN (waad_search_normalize(diagnosis_description) gin_trgm_ops)
    WHERE diagnosis_description IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_claim_batches_batch_code_canonical_trgm
    ON claim_batches USING GIN (waad_search_normalize(batch_code) gin_trgm_ops)
    WHERE batch_code IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_members_civil_id_canonical_trgm
    ON members USING GIN (waad_search_normalize(civil_id) gin_trgm_ops)
    WHERE civil_id IS NOT NULL;
