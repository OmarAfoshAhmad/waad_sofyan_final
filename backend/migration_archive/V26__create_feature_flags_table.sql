-- V26: Create feature_flags table required by FeatureFlag entity

CREATE TABLE IF NOT EXISTS feature_flags (
    id BIGSERIAL PRIMARY KEY,
    flag_key VARCHAR(100) NOT NULL,
    flag_name VARCHAR(255) NOT NULL,
    description TEXT,
    enabled BOOLEAN NOT NULL DEFAULT true,
    role_filters JSON,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_feature_flags_flag_key ON feature_flags(flag_key);
