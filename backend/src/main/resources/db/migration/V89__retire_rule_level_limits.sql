-- Full benefit-bucket cutover: rule rows decide coverage; buckets own every ceiling.
-- Keep the legacy columns temporarily for entity compatibility, but remove their data
-- so no reporting or older client can treat them as a second source of truth.
UPDATE benefit_policy_rules
SET amount_limit = NULL,
    times_limit = NULL
WHERE amount_limit IS NOT NULL
   OR times_limit IS NOT NULL;
