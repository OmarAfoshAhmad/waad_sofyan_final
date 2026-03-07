-- Ensure claims.id has a working sequence default (repair broken/defaultless environments)

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_class
        WHERE relkind = 'S'
          AND relname = 'claims_id_seq'
    ) THEN
        CREATE SEQUENCE claims_id_seq;
    END IF;

    ALTER TABLE claims
        ALTER COLUMN id SET DEFAULT nextval('claims_id_seq');

    ALTER SEQUENCE claims_id_seq OWNED BY claims.id;

    PERFORM setval(
        'claims_id_seq',
        COALESCE((SELECT MAX(id) FROM claims), 0) + 1,
        false
    );
END $$;
