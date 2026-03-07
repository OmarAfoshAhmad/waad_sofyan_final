-- V099: Fix JSON columns mapped as String in Java entities.
-- PostgreSQL 'json' type requires explicit JDBC casting which fails with
-- standard Hibernate 6 String mapping. Changing to 'text' is lossless
-- (text stores any string including JSON) and avoids JDBC type mismatch.
ALTER TABLE feature_flags ALTER COLUMN role_filters TYPE TEXT USING role_filters::TEXT;
ALTER TABLE module_access ALTER COLUMN allowed_roles TYPE TEXT USING allowed_roles::TEXT;
ALTER TABLE module_access ALTER COLUMN required_permissions TYPE TEXT USING required_permissions::TEXT;
