-- Add CLINIC_DEN and PHYSIOTHERAPY to the provider_type constraint
DO $$
BEGIN
    -- Drop the existing constraint if it exists
    IF EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_name = 'providers'
          AND constraint_type = 'CHECK'
          AND constraint_name = 'chk_provider_type'
    ) THEN
        ALTER TABLE providers DROP CONSTRAINT chk_provider_type;
    END IF;

    -- Drop any dynamically named check constraints related to provider_type
    IF EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_name = 'providers'
          AND constraint_type = 'CHECK'
          AND constraint_name LIKE '%provider_type%'
    ) THEN
        EXECUTE (
            SELECT 'ALTER TABLE providers DROP CONSTRAINT ' || constraint_name
            FROM information_schema.table_constraints
            WHERE table_name = 'providers'
              AND constraint_type = 'CHECK'
              AND constraint_name LIKE '%provider_type%'
            LIMIT 1
        );
    END IF;
END
$$;

ALTER TABLE providers
    ADD CONSTRAINT chk_provider_type
    CHECK (provider_type IN ('HOSPITAL', 'CLINIC', 'CLINIC_DEN', 'LAB', 'PHARMACY', 'RADIOLOGY', 'PHYSIOTHERAPY'));
