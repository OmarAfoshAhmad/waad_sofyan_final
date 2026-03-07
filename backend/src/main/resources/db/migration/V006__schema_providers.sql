-- ============================================================
-- V006: Providers, allowed employers, admin documents
-- ============================================================
-- Depends on: V005 (employers)

-- ----------------------------------------------------------
-- SECTION 1: Healthcare providers
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS providers (
    id                      BIGINT PRIMARY KEY DEFAULT nextval('provider_seq'),
    provider_name           VARCHAR(255) NOT NULL,
    provider_name_ar        VARCHAR(255),
    license_number          VARCHAR(100) NOT NULL UNIQUE,
    provider_type           VARCHAR(50)  NOT NULL
        CHECK (provider_type IN ('HOSPITAL','CLINIC','PHARMACY','LAB','RADIOLOGY','OTHER')),

    -- Contact
    contact_person          VARCHAR(255),
    contact_email           VARCHAR(255) UNIQUE,
    contact_phone           VARCHAR(50),
    address                 TEXT,
    city                    VARCHAR(100),
    region                  VARCHAR(100),

    -- Banking (for settlement)
    bank_name               VARCHAR(255),
    bank_account_number     VARCHAR(100),
    iban                    VARCHAR(50),

    -- Extended contact (runtime columns)
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

    -- Audit
    active      BOOLEAN DEFAULT true,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_providers_type    ON providers(provider_type) WHERE active = true;
CREATE INDEX IF NOT EXISTS idx_providers_active  ON providers(active);
CREATE INDEX IF NOT EXISTS idx_providers_license ON providers(license_number);

-- ----------------------------------------------------------
-- SECTION 2: Provider–employer allow list (access control)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS provider_allowed_employers (
    id          BIGSERIAL PRIMARY KEY,
    provider_id BIGINT NOT NULL,
    employer_id BIGINT NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(255),

    CONSTRAINT fk_allowed_provider FOREIGN KEY (provider_id) REFERENCES providers(id) ON DELETE CASCADE,
    CONSTRAINT fk_allowed_employer FOREIGN KEY (employer_id) REFERENCES employers(id) ON DELETE CASCADE,
    CONSTRAINT uq_provider_employer UNIQUE (provider_id, employer_id)
);

CREATE INDEX IF NOT EXISTS idx_allowed_employers_provider ON provider_allowed_employers(provider_id);
CREATE INDEX IF NOT EXISTS idx_allowed_employers_employer ON provider_allowed_employers(employer_id);

-- ----------------------------------------------------------
-- SECTION 3: Provider administrative documents
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS provider_admin_documents (
    id            BIGSERIAL PRIMARY KEY,
    provider_id   BIGINT NOT NULL,
    document_name VARCHAR(255) NOT NULL,
    document_type VARCHAR(100) NOT NULL,
    file_path     VARCHAR(500) NOT NULL,
    file_size     BIGINT,
    uploaded_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    uploaded_by   VARCHAR(255),

    CONSTRAINT fk_provider_docs FOREIGN KEY (provider_id) REFERENCES providers(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_provider_docs_provider ON provider_admin_documents(provider_id);
CREATE INDEX IF NOT EXISTS idx_provider_docs_type     ON provider_admin_documents(document_type);
