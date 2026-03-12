-- ============================================================
-- Table: feature_flags
-- Depends on: (none)
-- ============================================================
CREATE TABLE IF NOT EXISTS feature_flags (
    id          BIGSERIAL PRIMARY KEY,
    flag_key    VARCHAR(100) NOT NULL UNIQUE,
    flag_name   VARCHAR(255) NOT NULL,
    description TEXT,
    enabled     BOOLEAN DEFAULT true,
    role_filters TEXT,
    created_by  VARCHAR(50),
    updated_by  VARCHAR(50),
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP
);
