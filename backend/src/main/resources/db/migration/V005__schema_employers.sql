-- ============================================================
-- V005: Employers (main business customers)
-- ============================================================
-- No external dependencies.

CREATE TABLE IF NOT EXISTS employers (
    id                              BIGINT PRIMARY KEY DEFAULT nextval('employer_seq'),
    code                            VARCHAR(50)  NOT NULL UNIQUE,
    name                            VARCHAR(200) NOT NULL,

    -- Contact information
    address                         TEXT,
    phone                           VARCHAR(50),
    email                           VARCHAR(255),

    -- Branding & identity
    logo_url                        VARCHAR(500),
    website                         VARCHAR(200),
    business_type                   VARCHAR(100),
    tax_number                      VARCHAR(50),
    commercial_registration_number  VARCHAR(50),

    -- Status
    active                          BOOLEAN DEFAULT true,
    is_default                      BOOLEAN DEFAULT false,

    -- Audit
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_employers_code    ON employers(code);
CREATE INDEX IF NOT EXISTS idx_employers_active  ON employers(active);
CREATE INDEX IF NOT EXISTS idx_employers_default ON employers(is_default) WHERE is_default = true;
