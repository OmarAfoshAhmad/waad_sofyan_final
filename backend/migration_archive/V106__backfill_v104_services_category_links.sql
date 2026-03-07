-- V106: Backfill category links for V104 seeded services (SRV-XLS-*)
-- Purpose:
--   Ensure services inserted by V104 appear in unified catalog tree endpoint,
--   which depends on medical_service_categories mappings.
--
-- Strategy:
--   Insert one primary ANY-context mapping per SRV-XLS service that has:
--   - specialty_id present
--   - specialty mapped to category_id
--   - no existing row in medical_service_categories
--
-- Safety:
--   Idempotent via NOT EXISTS(service_id).

INSERT INTO medical_service_categories (service_id, category_id, context, is_primary, active)
SELECT
    ms.id,
    sp.category_id,
    'ANY',
    TRUE,
    TRUE
FROM medical_services ms
JOIN medical_specialties sp ON sp.id = ms.specialty_id
WHERE ms.deleted = FALSE
  AND ms.code LIKE 'SRV-XLS-%'
  AND ms.specialty_id IS NOT NULL
  AND sp.category_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM medical_service_categories msc
      WHERE msc.service_id = ms.id
  );
