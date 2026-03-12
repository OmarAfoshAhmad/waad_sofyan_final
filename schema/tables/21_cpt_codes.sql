-- ============================================================
-- Table: cpt_codes
-- Depends on: (none)
-- ============================================================
CREATE TABLE IF NOT EXISTS cpt_codes (
    id                   BIGSERIAL PRIMARY KEY,
    code                 VARCHAR(20)   NOT NULL UNIQUE,
    description          VARCHAR(500)  NOT NULL,
    category             VARCHAR(100),
    sub_category         VARCHAR(100),
    procedure_type       VARCHAR(20),
    standard_price       NUMERIC(15,2),
    max_allowed_price    NUMERIC(15,2),
    min_allowed_price    NUMERIC(15,2),
    covered              BOOLEAN DEFAULT true,
    co_payment_percentage NUMERIC(5,2),
    requires_pre_auth    BOOLEAN DEFAULT false,
    notes                VARCHAR(2000),
    active               BOOLEAN DEFAULT true,
    created_at           TIMESTAMP,
    updated_at           TIMESTAMP
);
