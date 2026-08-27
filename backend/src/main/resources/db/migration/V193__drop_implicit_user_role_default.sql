-- S-01 / Phase 2 — remove the privileged default from the identity column.
--
-- users.user_type carried DEFAULT 'DATA_ENTRY' since V5. Any INSERT that
-- omitted the column produced an account holding a role that RoleService
-- classifies as internal staff, which FeatureGuard.isStaff() waves past every
-- portal gate. Creating an identity and granting it standing must be two
-- separate decisions; a column default makes the second one happen by silence.
--
-- Deliberately narrow:
--   * NOT NULL is KEPT — an account with no role must remain impossible.
--   * No existing row is touched. Reassigning live users' roles is an
--     operational decision that needs evidence per account, not a blanket
--     UPDATE inside a schema migration.
--   * Every caller now has to name the role. The application enforces this in
--     User.userType (no @Builder.Default) and UserService.resolveUserType
--     (no fallback); this migration removes the last place the database
--     itself would supply one.

ALTER TABLE users ALTER COLUMN user_type DROP DEFAULT;
