-- V37: Create module_access table required by ModuleAccess entity

CREATE TABLE IF NOT EXISTS module_access (
    id BIGSERIAL PRIMARY KEY,
    module_name VARCHAR(100) NOT NULL,
    module_key VARCHAR(100) NOT NULL,
    description TEXT,
    allowed_roles JSON NOT NULL,
    required_permissions JSON,
    feature_flag_key VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_module_access_module_key ON module_access(module_key);
