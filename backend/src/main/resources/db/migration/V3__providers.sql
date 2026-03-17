-- ============================================================
-- V3: جدول مقدمي الخدمات الصحية
-- ============================================================

CREATE TABLE IF NOT EXISTS providers (
    id                  BIGINT PRIMARY KEY DEFAULT nextval('provider_seq'),
    provider_name       VARCHAR(255) NOT NULL,
    provider_name_ar    VARCHAR(255),
    license_number      VARCHAR(100) NOT NULL UNIQUE,
    provider_type       VARCHAR(50) NOT NULL
        CHECK (provider_type IN ('HOSPITAL','CLINIC','PHARMACY','LAB','RADIOLOGY','OTHER')),

    contact_person      VARCHAR(255),
    contact_email       VARCHAR(255) UNIQUE,
    contact_phone       VARCHAR(50),
    address             TEXT,
    city                VARCHAR(100),
    region              VARCHAR(100),

    bank_name           VARCHAR(255),
    bank_account_number VARCHAR(100),
    iban                VARCHAR(50),

    allow_all_employers     BOOLEAN DEFAULT false,
    tax_company_code        VARCHAR(50),
    principal_name          VARCHAR(255),
    principal_phone         VARCHAR(50),
    principal_email         VARCHAR(255),
    principal_mobile        VARCHAR(50),
    principal_address       TEXT,
    secondary_contact       VARCHAR(255),
    secondary_contact_phone VARCHAR(50),
    secondary_contact_email VARCHAR(255),
    accounting_person       VARCHAR(255),
    accounting_phone        VARCHAR(50),
    accounting_email        VARCHAR(255),
    provider_status         VARCHAR(50),

    active      BOOLEAN DEFAULT true,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_providers_type    ON providers(provider_type) WHERE active = true;
CREATE INDEX IF NOT EXISTS idx_providers_active  ON providers(active);
CREATE INDEX IF NOT EXISTS idx_providers_license ON providers(license_number);
