-- Central payment reference sequence for settlement batch payments
-- Ensures globally unique, ordered references across all users.

CREATE SEQUENCE IF NOT EXISTS settlement_payment_reference_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1;

SELECT setval(
    'settlement_payment_reference_seq',
    GREATEST(
        COALESCE(
            (
                SELECT MAX(NULLIF(regexp_replace(payment_reference, '[^0-9]', '', 'g'), '')::BIGINT)
                FROM settlement_batches
                WHERE payment_reference IS NOT NULL
                  AND regexp_replace(payment_reference, '[^0-9]', '', 'g') <> ''
            ),
            0
        ) + 1,
        1
    ),
    false
);
