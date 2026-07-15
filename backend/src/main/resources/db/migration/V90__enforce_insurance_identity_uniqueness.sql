-- Close race conditions and case/whitespace variants in master identities.
-- The application normalizes these values too; database indexes are the final guard.

CREATE UNIQUE INDEX IF NOT EXISTS uq_employers_code_ci
    ON employers (LOWER(BTRIM(code)));

CREATE UNIQUE INDEX IF NOT EXISTS uq_employers_name_ci
    ON employers (LOWER(BTRIM(name)));

CREATE UNIQUE INDEX IF NOT EXISTS uq_benefit_policies_code_ci
    ON benefit_policies (LOWER(BTRIM(policy_code)))
    WHERE policy_code IS NOT NULL;
