-- Names are unique inside one policy after trimming and case normalization.
-- Codes already have unique constraints; these indexes protect imports and concurrent requests too.

-- Older/manual imports may already contain groups with the same visible name.
-- Keep the group that owns the most buckets (then the oldest id), move all buckets
-- to it, and remove only the redundant group rows. Bucket links and consumptions
-- remain intact because their bucket ids do not change.
WITH ranked_groups AS (
    SELECT g.id,
           FIRST_VALUE(g.id) OVER (
               PARTITION BY g.policy_id, LOWER(BTRIM(g.name_ar))
               ORDER BY (SELECT COUNT(*) FROM benefit_limit_buckets b WHERE b.benefit_group_id = g.id) DESC,
                        g.id
           ) AS keeper_id
    FROM benefit_groups g
)
UPDATE benefit_limit_buckets b
SET benefit_group_id = r.keeper_id
FROM ranked_groups r
WHERE b.benefit_group_id = r.id
  AND r.id <> r.keeper_id;

WITH ranked_groups AS (
    SELECT g.id,
           FIRST_VALUE(g.id) OVER (
               PARTITION BY g.policy_id, LOWER(BTRIM(g.name_ar))
               ORDER BY (SELECT COUNT(*) FROM benefit_limit_buckets b WHERE b.benefit_group_id = g.id) DESC,
                        g.id
           ) AS keeper_id
    FROM benefit_groups g
)
DELETE FROM benefit_groups g
USING ranked_groups r
WHERE g.id = r.id
  AND r.id <> r.keeper_id;

CREATE UNIQUE INDEX IF NOT EXISTS uq_benefit_group_policy_name_ar_ci
    ON benefit_groups (policy_id, LOWER(BTRIM(name_ar)));

CREATE UNIQUE INDEX IF NOT EXISTS uq_benefit_bucket_policy_name_ar_ci
    ON benefit_limit_buckets (policy_id, LOWER(BTRIM(name_ar)));
