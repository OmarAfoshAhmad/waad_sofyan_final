CREATE EXTENSION IF NOT EXISTS btree_gist;

UPDATE provider_contract_pricing_items p
   SET effective_from = COALESCE(c.start_date, CURRENT_DATE)
  FROM provider_contracts c
 WHERE p.contract_id = c.id AND p.effective_from IS NULL;

ALTER TABLE provider_contract_pricing_items
    ALTER COLUMN effective_from SET NOT NULL,
    ADD COLUMN service_identity TEXT GENERATED ALWAYS AS (
        CASE
            WHEN NULLIF(BTRIM(service_code), '') IS NOT NULL
                THEN 'CODE:' || LOWER(BTRIM(service_code))
            ELSE 'NAME:' || LOWER(BTRIM(service_name))
        END
    ) STORED,
    ADD CONSTRAINT chk_provider_price_service_identity_present
        CHECK (
            NULLIF(BTRIM(service_code), '') IS NOT NULL
            OR NULLIF(BTRIM(service_name), '') IS NOT NULL
        ),
    ADD CONSTRAINT chk_provider_price_half_open_period
        CHECK (effective_to IS NULL OR effective_to > effective_from);

ALTER TABLE provider_contract_pricing_items
    ADD CONSTRAINT ex_provider_price_no_overlapping_versions
    EXCLUDE USING gist (
        contract_id WITH =,
        service_identity WITH =,
        daterange(effective_from, effective_to, '[)') WITH &&
    ) WHERE (active = TRUE);

CREATE INDEX idx_provider_price_effective_identity
    ON provider_contract_pricing_items(contract_id, service_identity, effective_from, effective_to)
    WHERE active = TRUE;
