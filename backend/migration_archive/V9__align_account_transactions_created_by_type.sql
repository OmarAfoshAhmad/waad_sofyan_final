-- ============================================================================
-- V9: Align account_transactions.created_by column type with JPA entity
-- ============================================================================
-- Problem:
--   Entity AccountTransaction.createdBy expects BIGINT.
--   Legacy schema defines account_transactions.created_by as VARCHAR(255).
--
-- Strategy:
--   Convert column in-place to BIGINT with safe cast.
--   Non-numeric legacy values are mapped to NULL.
-- ============================================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'account_transactions'
          AND column_name = 'created_by'
          AND data_type <> 'bigint'
    ) THEN
        ALTER TABLE account_transactions
            ALTER COLUMN created_by TYPE BIGINT
            USING (
                CASE
                    WHEN created_by IS NULL THEN NULL
                    WHEN trim(created_by) ~ '^[0-9]+$' THEN trim(created_by)::BIGINT
                    ELSE NULL
                END
            );
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_account_transactions_created_by
    ON account_transactions(created_by);

-- ============================================================================
-- Migration Complete: V9
-- ============================================================================
