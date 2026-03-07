-- ============================================================================
-- V2: Security Schema - RBAC and Access Control
-- ============================================================================
-- CLEAN MIGRATION REBASELINE - Development Environment
-- 
-- Creates:
--   - Roles table
--   - Permissions table
--   - Role-Permission mapping
--   - User-Role mapping
--   - Performance indexes for RBAC queries
-- 
-- Incorporates: Wave 1 RBAC hardening + performance indexes from day 1
-- ============================================================================

-- ============================================================================
-- SECTION 1: Security Sequences
-- ============================================================================

CREATE SEQUENCE IF NOT EXISTS role_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS permission_seq START WITH 1 INCREMENT BY 50;

-- ============================================================================
-- SECTION 2: Roles Table
-- ============================================================================

CREATE TABLE IF NOT EXISTS roles (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    role_type VARCHAR(50) NOT NULL DEFAULT 'CUSTOM' CHECK (role_type IN ('SYSTEM', 'CUSTOM')),
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    
    CONSTRAINT chk_role_name_format CHECK (name ~ '^[A-Z_]+$')
);

CREATE INDEX IF NOT EXISTS idx_roles_name ON roles(name);
CREATE INDEX IF NOT EXISTS idx_roles_type ON roles(role_type);

COMMENT ON TABLE roles IS 'System and custom roles for RBAC';
COMMENT ON COLUMN roles.role_type IS 'SYSTEM roles cannot be modified; CUSTOM roles can be edited';
COMMENT ON CONSTRAINT chk_role_name_format ON roles IS 'Role names must be UPPER_SNAKE_CASE';

-- ============================================================================
-- SECTION 3: Permissions Table
-- ============================================================================

CREATE TABLE IF NOT EXISTS permissions (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    module_name VARCHAR(100) NOT NULL,
    action_name VARCHAR(100) NOT NULL,
    permission_type VARCHAR(50) NOT NULL DEFAULT 'CUSTOM' CHECK (permission_type IN ('SYSTEM', 'CUSTOM')),
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_permissions_name ON permissions(name);
CREATE INDEX IF NOT EXISTS idx_permissions_module ON permissions(module_name);
CREATE INDEX IF NOT EXISTS idx_permissions_action ON permissions(action_name);
CREATE INDEX IF NOT EXISTS idx_permissions_type ON permissions(permission_type);
CREATE INDEX IF NOT EXISTS idx_permissions_module_action ON permissions(module_name, action_name);

COMMENT ON TABLE permissions IS 'Granular permissions for system resources';
COMMENT ON COLUMN permissions.module_name IS 'Module/resource the permission applies to (e.g., CLAIMS, MEMBERS)';
COMMENT ON COLUMN permissions.action_name IS 'Action type (e.g., VIEW, CREATE, UPDATE, DELETE)';

-- ============================================================================
-- SECTION 4: Role-Permission Mapping
-- ============================================================================

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    
    PRIMARY KEY (role_id, permission_id),
    
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) 
        REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) 
        REFERENCES permissions(id) ON DELETE CASCADE
);

-- Wave 1 RBAC Performance Indexes (incorporated from day 1)
CREATE INDEX IF NOT EXISTS idx_role_permissions_role ON role_permissions(role_id);
CREATE INDEX IF NOT EXISTS idx_role_permissions_permission ON role_permissions(permission_id);

COMMENT ON TABLE role_permissions IS 'Many-to-many mapping between roles and permissions';

-- ============================================================================
-- SECTION 5: User-Role Mapping
-- ============================================================================

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    
    -- Audit fields
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by VARCHAR(255),
    
    PRIMARY KEY (user_id, role_id),
    
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) 
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) 
        REFERENCES roles(id) ON DELETE CASCADE
);

-- Wave 1 RBAC Performance Indexes (incorporated from day 1)
CREATE INDEX IF NOT EXISTS idx_user_roles_user ON user_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_role ON user_roles(role_id);

COMMENT ON TABLE user_roles IS 'Many-to-many mapping between users and roles';

-- ============================================================================
-- SECTION 6: Employer Settings (REMOVED - RBAC Only)
-- ============================================================================
-- Feature flags removed in favor of pure RBAC (Phase 9 Cleanup)
-- ============================================================================

-- ============================================================================
-- Migration Complete: V2
-- ============================================================================
-- Created: Roles, Permissions, Role-Permission mapping, User-Role mapping
-- Hardened: RBAC performance indexes from day 1 (no separate fix migration)
-- Ready for: Medical catalog (V3) and business entities (V4)
-- ============================================================================
