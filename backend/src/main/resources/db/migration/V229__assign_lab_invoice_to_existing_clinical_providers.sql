-- V228 made SYS-LAB-INVOICE a default service for hospitals and clinics.
-- Defaults protect newly provisioned providers; this migration also applies
-- the service to already-existing active clinical providers so claim entry is
-- consistent immediately after upgrade.

INSERT INTO provider_services (provider_id, service_code, active, created_at, updated_at)
SELECT p.id, 'SYS-LAB-INVOICE', true, now(), now()
FROM providers p
WHERE p.active = true
  AND p.provider_type IN ('HOSPITAL', 'CLINIC')
ON CONFLICT (provider_id, service_code) DO UPDATE SET
    active = true,
    updated_at = now();
