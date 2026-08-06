-- Add OPTICS (مركز بصريات وعيون) to the provider_type constraint.
-- Follows the same defensive pattern as V82: the constraint has been created
-- under different names historically, so drop by known name AND by pattern
-- before re-creating it with the full current enum.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_name = 'providers'
          AND constraint_type = 'CHECK'
          AND constraint_name = 'chk_provider_type'
    ) THEN
        ALTER TABLE providers DROP CONSTRAINT chk_provider_type;
    END IF;

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
    CHECK (provider_type IN ('HOSPITAL', 'CLINIC', 'CLINIC_DEN', 'LAB',
                             'PHARMACY', 'RADIOLOGY', 'PHYSIOTHERAPY', 'OPTICS'));
