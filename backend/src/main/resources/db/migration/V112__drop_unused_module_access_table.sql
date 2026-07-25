-- ============================================================
-- V112: Drop unused module_access table
-- ============================================================
-- SECTION_02 finding: ModuleAccess/ModuleAccessService/
-- ModuleAccessController/ModuleAccessRepository formed a fully-built CRUD
-- feature (10 REST endpoints) that nothing in the application actually
-- consulted to gate access — no filter, interceptor, or @PreAuthorize
-- expression anywhere in the codebase called it. The frontend has no page
-- for it either (only an unused translation label). The table had 0 rows.
--
-- All 5 backend files removed in the same change as this migration:
--   ModuleAccessController.java, ModuleAccessDto.java, ModuleAccess.java,
--   ModuleAccessRepository.java, ModuleAccessService.java.

DROP TABLE IF EXISTS module_access;
