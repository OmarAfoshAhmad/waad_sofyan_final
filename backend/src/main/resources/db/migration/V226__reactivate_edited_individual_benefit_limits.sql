-- Editing an individual benefit limit must not leave the visible rule saying
-- "has a 3000 cap" while the underlying AUTO-BEN bucket remains inactive.
-- This migration repairs rows created by the old upsert path; the Java service
-- change in the same release prevents the split-brain state from recurring.

UPDATE benefit_groups g
SET active = TRUE
WHERE g.code LIKE 'AUTO-BEN-RULE-%'
  AND g.active = FALSE
  AND EXISTS (
      SELECT 1
      FROM benefit_limit_buckets b
      JOIN benefit_rule_buckets brb ON brb.bucket_id = b.id
      WHERE b.benefit_group_id = g.id
        AND b.code LIKE 'AUTO-BEN-LIMIT-RULE-%'
        AND (b.amount_limit IS NOT NULL OR b.times_limit IS NOT NULL OR b.days_limit IS NOT NULL)
  );

UPDATE benefit_limit_buckets b
SET active = TRUE,
    updated_at = NOW()
WHERE b.code LIKE 'AUTO-BEN-LIMIT-RULE-%'
  AND b.active = FALSE
  AND (b.amount_limit IS NOT NULL OR b.times_limit IS NOT NULL OR b.days_limit IS NOT NULL)
  AND EXISTS (
      SELECT 1
      FROM benefit_rule_buckets brb
      WHERE brb.bucket_id = b.id
  );
