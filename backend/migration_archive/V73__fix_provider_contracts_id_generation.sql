-- V73: Fix provider_contracts.id auto-generation
-- Root cause: legacy table has id NOT NULL without default/sequence, causing INSERT failures.

CREATE SEQUENCE IF NOT EXISTS provider_contracts_id_seq;

SELECT setval(
    'provider_contracts_id_seq',
    COALESCE((SELECT MAX(id) FROM provider_contracts), 0) + 1,
    false
);

ALTER TABLE provider_contracts
    ALTER COLUMN id SET DEFAULT nextval('provider_contracts_id_seq');
