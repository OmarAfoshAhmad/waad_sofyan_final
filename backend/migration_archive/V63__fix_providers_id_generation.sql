CREATE SEQUENCE IF NOT EXISTS providers_id_seq;

SELECT setval(
    'providers_id_seq',
    COALESCE((SELECT MAX(id) FROM providers), 0) + 1,
    false
);

ALTER TABLE providers
    ALTER COLUMN id SET DEFAULT nextval('providers_id_seq');
