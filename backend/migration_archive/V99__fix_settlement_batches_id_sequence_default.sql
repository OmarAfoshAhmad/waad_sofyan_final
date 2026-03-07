-- Ensure settlement_batches.id has a working sequence default
-- Fixes runtime 500 on POST /api/v1/settlement-batches when id is inserted as NULL.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_class
        WHERE relkind = 'S'
          AND relname = 'settlement_batches_id_seq'
    ) THEN
        CREATE SEQUENCE settlement_batches_id_seq;
    END IF;

    ALTER TABLE settlement_batches
        ALTER COLUMN id SET DEFAULT nextval('settlement_batches_id_seq');

    ALTER SEQUENCE settlement_batches_id_seq OWNED BY settlement_batches.id;

    PERFORM setval(
        'settlement_batches_id_seq',
        COALESCE((SELECT MAX(id) FROM settlement_batches), 0) + 1,
        false
    );
END $$;
