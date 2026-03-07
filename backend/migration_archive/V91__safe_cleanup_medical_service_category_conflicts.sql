-- ============================================================
-- V91: Safe cleanup for medical_service_categories conflicts
-- Scope : Data cleanup only (idempotent, no schema changes)
-- Date  : 2026-02-20
-- ============================================================

-- 1) Remove stale inactive primary rows when an active primary exists
--    for the same (service_id, context).
--    This prevents historical primary-flag conflicts from blocking updates.
WITH stale_inactive_primary AS (
    SELECT msc.id
    FROM medical_service_categories msc
    WHERE msc.is_primary = TRUE
      AND msc.active = FALSE
      AND EXISTS (
          SELECT 1
          FROM medical_service_categories m2
          WHERE m2.service_id = msc.service_id
            AND m2.context = msc.context
            AND m2.is_primary = TRUE
            AND m2.active = TRUE
      )
)
DELETE FROM medical_service_categories d
USING stale_inactive_primary s
WHERE d.id = s.id;

-- 2) Deduplicate exact (service_id, category_id) duplicates.
--    Keep the best row by priority:
--      active DESC, is_primary DESC, id ASC
WITH ranked_pairs AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY service_id, category_id
            ORDER BY active DESC, is_primary DESC, id ASC
        ) AS rn
    FROM medical_service_categories
),
pair_dups AS (
    SELECT id
    FROM ranked_pairs
    WHERE rn > 1
)
DELETE FROM medical_service_categories d
USING pair_dups x
WHERE d.id = x.id;

-- 3) Safety normalization:
--    If a (service_id, context) has active rows but none marked primary,
--    promote one deterministic active row (lowest id) to primary.
WITH missing_primary AS (
    SELECT service_id, context
    FROM medical_service_categories
    WHERE active = TRUE
    GROUP BY service_id, context
    HAVING BOOL_OR(is_primary) = FALSE
),
chosen AS (
    SELECT DISTINCT ON (msc.service_id, msc.context)
           msc.id
    FROM medical_service_categories msc
    JOIN missing_primary mp
      ON mp.service_id = msc.service_id
     AND mp.context = msc.context
    WHERE msc.active = TRUE
    ORDER BY msc.service_id, msc.context, msc.id ASC
)
UPDATE medical_service_categories u
SET is_primary = TRUE
FROM chosen c
WHERE u.id = c.id;
