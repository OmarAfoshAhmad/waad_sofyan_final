-- DATA_ENTRY stops holding MEMBER_LIMIT_VIEW by default.
--
-- V192 granted it to the role template. The server then refused the same users
-- through a separate rule that named roles rather than permissions, so the
-- permission catalogue said one thing and the access policy did another --
-- and the UI, which can only read the catalogue, offered a ceiling column that
-- failed on every load.
--
-- The policy now reads the permission. This aligns the template with what the
-- role is for: entering identity, employer and policy, not consumed and
-- remaining limits.
--
-- Only the ROLE template is touched. A per-user GRANT in rbac_user_permissions
-- is an administrator's deliberate exception and is left exactly as it is; the
-- server honours it from the next authorization refresh.
DELETE FROM rbac_role_permissions
WHERE role_code = 'DATA_ENTRY' AND permission_code = 'MEMBER_LIMIT_VIEW';
