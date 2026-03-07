-- ============================================================
-- V023: Medical codes — CPT (procedures) and ICD-10 (diagnoses)
-- ============================================================
-- Depends on: nothing

-- ----------------------------------------------------------
-- SECTION 1: CPT procedure codes
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS cpt_codes (
    id                    BIGSERIAL PRIMARY KEY,
    code                  VARCHAR(20)  NOT NULL UNIQUE,
    description           VARCHAR(500) NOT NULL,
    category              VARCHAR(100),
    sub_category          VARCHAR(100),
    procedure_type        VARCHAR(20),
    standard_price        NUMERIC(15,2),
    max_allowed_price     NUMERIC(15,2),
    min_allowed_price     NUMERIC(15,2),
    covered               BOOLEAN DEFAULT true,
    co_payment_percentage NUMERIC(5,2),
    requires_pre_auth     BOOLEAN DEFAULT false,
    notes                 VARCHAR(2000),
    active                BOOLEAN DEFAULT true,
    created_at            TIMESTAMP,
    updated_at            TIMESTAMP
);

-- ----------------------------------------------------------
-- SECTION 2: ICD-10 diagnosis codes
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS icd_codes (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL UNIQUE,
    description VARCHAR(500) NOT NULL,
    category    VARCHAR(50),
    sub_category VARCHAR(100),
    version     VARCHAR(20),
    notes       VARCHAR(2000),
    active      BOOLEAN DEFAULT true,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP
);
