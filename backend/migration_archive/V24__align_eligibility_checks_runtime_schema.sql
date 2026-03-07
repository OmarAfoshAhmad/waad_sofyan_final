-- V24: Align eligibility_checks table with EligibilityCheck entity for ddl-auto=validate

ALTER TABLE eligibility_checks ADD COLUMN IF NOT EXISTS request_id VARCHAR(36);
ALTER TABLE eligibility_checks ADD COLUMN IF NOT EXISTS check_timestamp TIMESTAMP;
ALTER TABLE eligibility_checks ADD COLUMN IF NOT EXISTS provider_id BIGINT;
ALTER TABLE eligibility_checks ADD COLUMN IF NOT EXISTS service_date DATE;
ALTER TABLE eligibility_checks ADD COLUMN IF NOT EXISTS service_code VARCHAR(50);
ALTER TABLE eligibility_checks ADD COLUMN IF NOT EXISTS eligible BOOLEAN;
ALTER TABLE eligibility_checks ADD COLUMN IF NOT EXISTS status VARCHAR(50);
ALTER TABLE eligibility_checks ADD COLUMN IF NOT EXISTS reasons TEXT;
ALTER TABLE eligibility_checks ADD COLUMN IF NOT EXISTS member_name VARCHAR(255);
ALTER TABLE eligibility_checks ADD COLUMN IF NOT EXISTS member_civil_id VARCHAR(50);
ALTER TABLE eligibility_checks ADD COLUMN IF NOT EXISTS member_status VARCHAR(30);
ALTER TABLE eligibility_checks ADD COLUMN IF NOT EXISTS policy_number VARCHAR(100);
ALTER TABLE eligibility_checks ADD COLUMN IF NOT EXISTS policy_status VARCHAR(30);
ALTER TABLE eligibility_checks ADD COLUMN IF NOT EXISTS policy_start_date DATE;
ALTER TABLE eligibility_checks ADD COLUMN IF NOT EXISTS policy_end_date DATE;
ALTER TABLE eligibility_checks ADD COLUMN IF NOT EXISTS employer_id BIGINT;
ALTER TABLE eligibility_checks ADD COLUMN IF NOT EXISTS employer_name VARCHAR(255);
ALTER TABLE eligibility_checks ADD COLUMN IF NOT EXISTS checked_by_user_id BIGINT;
ALTER TABLE eligibility_checks ADD COLUMN IF NOT EXISTS checked_by_username VARCHAR(100);
ALTER TABLE eligibility_checks ADD COLUMN IF NOT EXISTS company_scope_id BIGINT;
ALTER TABLE eligibility_checks ADD COLUMN IF NOT EXISTS ip_address VARCHAR(45);
ALTER TABLE eligibility_checks ADD COLUMN IF NOT EXISTS user_agent VARCHAR(500);
ALTER TABLE eligibility_checks ADD COLUMN IF NOT EXISTS processing_time_ms INTEGER;
ALTER TABLE eligibility_checks ADD COLUMN IF NOT EXISTS rules_evaluated INTEGER;
ALTER TABLE eligibility_checks ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;

-- Backfill from legacy columns
UPDATE eligibility_checks
SET check_timestamp = COALESCE(check_timestamp, check_date)
WHERE check_timestamp IS NULL;

UPDATE eligibility_checks
SET eligible = COALESCE(eligible, is_eligible)
WHERE eligible IS NULL;

UPDATE eligibility_checks
SET reasons = COALESCE(reasons, eligibility_reason)
WHERE reasons IS NULL;

UPDATE eligibility_checks
SET checked_by_username = COALESCE(checked_by_username, checked_by)
WHERE checked_by_username IS NULL;

UPDATE eligibility_checks
SET service_date = COALESCE(service_date, CAST(check_timestamp AS DATE), CURRENT_DATE)
WHERE service_date IS NULL;

UPDATE eligibility_checks
SET status = COALESCE(status, CASE WHEN eligible THEN 'ELIGIBLE' ELSE 'NOT_ELIGIBLE' END)
WHERE status IS NULL;

UPDATE eligibility_checks
SET created_at = COALESCE(created_at, check_timestamp, CURRENT_TIMESTAMP)
WHERE created_at IS NULL;

-- Generate stable request IDs for historical rows missing it
UPDATE eligibility_checks
SET request_id = SUBSTRING(md5(id::text || ':' || COALESCE(check_timestamp::text, now()::text) || ':' || random()::text), 1, 36)
WHERE request_id IS NULL;

-- Enforce entity required columns
ALTER TABLE eligibility_checks ALTER COLUMN request_id SET NOT NULL;
ALTER TABLE eligibility_checks ALTER COLUMN check_timestamp SET NOT NULL;
ALTER TABLE eligibility_checks ALTER COLUMN service_date SET NOT NULL;
ALTER TABLE eligibility_checks ALTER COLUMN eligible SET NOT NULL;
ALTER TABLE eligibility_checks ALTER COLUMN status SET NOT NULL;
ALTER TABLE eligibility_checks ALTER COLUMN created_at SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uk_eligibility_request_id'
          AND conrelid = 'eligibility_checks'::regclass
    ) THEN
        ALTER TABLE eligibility_checks
            ADD CONSTRAINT uk_eligibility_request_id UNIQUE (request_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_eligibility_request_id ON eligibility_checks(request_id);
CREATE INDEX IF NOT EXISTS idx_eligibility_member_id ON eligibility_checks(member_id);
CREATE INDEX IF NOT EXISTS idx_eligibility_policy_id ON eligibility_checks(policy_id);
CREATE INDEX IF NOT EXISTS idx_eligibility_service_date ON eligibility_checks(service_date);
CREATE INDEX IF NOT EXISTS idx_eligibility_check_timestamp ON eligibility_checks(check_timestamp);
CREATE INDEX IF NOT EXISTS idx_eligibility_company_scope ON eligibility_checks(company_scope_id);
