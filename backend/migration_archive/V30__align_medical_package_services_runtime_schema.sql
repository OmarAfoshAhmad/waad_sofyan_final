-- V30: Align medical_package_services join table with MedicalPackage.services mapping

ALTER TABLE medical_package_services
    ADD COLUMN IF NOT EXISTS service_id BIGINT;

-- Best-effort backfill where historical data used canonical_service_id
-- (kept nullable because entity JoinColumn does not declare nullable=false)
UPDATE medical_package_services
SET service_id = COALESCE(service_id, canonical_service_id)
WHERE service_id IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_package_service_service'
          AND conrelid = 'medical_package_services'::regclass
    ) THEN
        ALTER TABLE medical_package_services
            ADD CONSTRAINT fk_package_service_service
            FOREIGN KEY (service_id) REFERENCES medical_services(id) ON DELETE RESTRICT;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_package_services_service
    ON medical_package_services(service_id);
