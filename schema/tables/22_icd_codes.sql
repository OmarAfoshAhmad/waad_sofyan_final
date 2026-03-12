-- ============================================================
-- Table: icd_codes
-- Depends on: (none)
-- ============================================================
CREATE TABLE IF NOT EXISTS icd_codes (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(20)  NOT NULL UNIQUE,
    description  VARCHAR(500) NOT NULL,
    category     VARCHAR(50),
    sub_category VARCHAR(100),
    version      VARCHAR(20),
    notes        VARCHAR(2000),
    active       BOOLEAN DEFAULT true,
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP
);
