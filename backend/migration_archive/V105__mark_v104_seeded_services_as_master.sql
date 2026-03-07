-- V105: Make V104 seeded services visible in "main only" catalog filters
-- Reason: V104 inserted rows without is_master=true, so they don't appear in UI views
-- that filter by main/master services.

UPDATE medical_services
SET is_master = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted = FALSE
  AND code LIKE 'SRV-XLS-%'
  AND COALESCE(is_master, FALSE) = FALSE;
