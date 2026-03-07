CREATE SEQUENCE IF NOT EXISTS benefit_policies_id_seq;

SELECT setval(
    'benefit_policies_id_seq',
    COALESCE((SELECT MAX(id) FROM benefit_policies), 0) + 1,
    false
);

ALTER TABLE benefit_policies
    ALTER COLUMN id SET DEFAULT nextval('benefit_policies_id_seq');

CREATE SEQUENCE IF NOT EXISTS member_barcode_seq;

SELECT setval(
    'member_barcode_seq',
    COALESCE((
        SELECT MAX(split_part(barcode, '-', 3)::bigint)
        FROM members
        WHERE barcode ~ '^[A-Z]+-[0-9]{4}-[0-9]+$'
    ), 0) + 1,
    false
);

CREATE SEQUENCE IF NOT EXISTS member_card_number_seq;

SELECT setval(
    'member_card_number_seq',
    COALESCE((
        SELECT MAX(
            CASE
                WHEN card_number ~ '^[0-9]{6}$' THEN card_number::bigint
                WHEN card_number ~ '^[0-9]{6}-[0-9]{2}$' THEN split_part(card_number, '-', 1)::bigint
                ELSE NULL
            END
        )
        FROM members
    ), 0) + 1,
    false
);
