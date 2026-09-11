CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE OR REPLACE FUNCTION waad_search_normalize(value text)
RETURNS text
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
AS $$
    SELECT CASE
        WHEN value IS NULL THEN ''
        ELSE regexp_replace(
            regexp_replace(
                translate(
                    lower(btrim(value)),
                    'أإآٱىؤئةًٌٍَُِّْـ',
                    'اااايويه'
                ),
                '^\s*ال(.{2,})$',
                '\1'
            ),
            '\s+',
            ' ',
            'g'
        )
    END
$$;

CREATE INDEX IF NOT EXISTS idx_benefit_policies_search_name_norm_trgm
    ON benefit_policies USING GIN (waad_search_normalize(name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_benefit_policies_search_code_norm_trgm
    ON benefit_policies USING GIN (waad_search_normalize(policy_code) gin_trgm_ops)
    WHERE policy_code IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_employers_search_name_norm_trgm
    ON employers USING GIN (waad_search_normalize(name) gin_trgm_ops);
