-- Align provider_service_prices with ProviderServicePrice entity mapping

ALTER TABLE provider_service_prices
    ADD COLUMN IF NOT EXISTS canonical_service_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS provider_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS provider_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS provider_service_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS price NUMERIC(10,2),
    ADD COLUMN IF NOT EXISTS currency VARCHAR(10);

UPDATE provider_service_prices
SET price = COALESCE(price, unit_price)
WHERE price IS NULL;

UPDATE provider_service_prices
SET provider_code = COALESCE(provider_code, provider_id::TEXT)
WHERE provider_code IS NULL;

UPDATE provider_service_prices
SET provider_name = COALESCE(provider_name, 'UNKNOWN')
WHERE provider_name IS NULL;

UPDATE provider_service_prices
SET provider_service_name = COALESCE(provider_service_name, canonical_service_code, 'UNKNOWN SERVICE')
WHERE provider_service_name IS NULL;

UPDATE provider_service_prices
SET currency = COALESCE(currency, 'LYD')
WHERE currency IS NULL;

ALTER TABLE provider_service_prices
    ALTER COLUMN price SET NOT NULL,
    ALTER COLUMN provider_code SET NOT NULL,
    ALTER COLUMN provider_name SET NOT NULL,
    ALTER COLUMN provider_service_name SET NOT NULL,
    ALTER COLUMN currency SET NOT NULL;

ALTER TABLE provider_service_prices
    ALTER COLUMN id DROP DEFAULT;

ALTER TABLE provider_service_prices
    ALTER COLUMN id TYPE UUID
    USING (
        (
            substring(md5('provider_service_prices:' || id::text), 1, 8) || '-' ||
            substring(md5('provider_service_prices:' || id::text), 9, 4) || '-' ||
            substring(md5('provider_service_prices:' || id::text), 13, 4) || '-' ||
            substring(md5('provider_service_prices:' || id::text), 17, 4) || '-' ||
            substring(md5('provider_service_prices:' || id::text), 21, 12)
        )::uuid
    );
