-- Ensure settlement_batch_items.id has a working sequence default
-- Fixes runtime persistence errors when SettlementBatchItem is inserted with null ID.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_class
        WHERE relkind = 'S'
          AND relname = 'settlement_batch_items_id_seq'
    ) THEN
        CREATE SEQUENCE settlement_batch_items_id_seq;
    END IF;

    ALTER TABLE settlement_batch_items
        ALTER COLUMN id SET DEFAULT nextval('settlement_batch_items_id_seq');

    ALTER SEQUENCE settlement_batch_items_id_seq OWNED BY settlement_batch_items.id;

    PERFORM setval(
        'settlement_batch_items_id_seq',
        COALESCE((SELECT MAX(id) FROM settlement_batch_items), 0) + 1,
        false
    );
END $$;
