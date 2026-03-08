-- ============================================================
-- V100: Medical Catalog Performance Indexes
-- ============================================================
-- Fixes slow GET /api/v1/medical-catalog/tree and catalog search.
-- Addresses: missing indexes on medical_services, medical_service_categories,
--            and lack of text search support for ILIKE queries.
-- ============================================================

-- ----------------------------------------------------------
-- medical_services: core lookup columns
-- ----------------------------------------------------------

-- Fast lookup by category (used in getTree JOIN and getActiveByCategoryId)
CREATE INDEX IF NOT EXISTS idx_medical_services_category_deleted_active
    ON medical_services(category_id, deleted, active);

-- Fast lookup for active-only queries (used in findByActiveTrue)
CREATE INDEX IF NOT EXISTS idx_medical_services_active_deleted
    ON medical_services(active, deleted);

-- Fast code lookup (already unique, but explicit index helps planner)
CREATE INDEX IF NOT EXISTS idx_medical_services_code_lower
    ON medical_services(LOWER(code));

-- ILIKE search on name_ar (catalog search)
CREATE INDEX IF NOT EXISTS idx_medical_services_name_ar_lower
    ON medical_services(LOWER(name_ar));

-- ILIKE search on name_en (catalog search)
CREATE INDEX IF NOT EXISTS idx_medical_services_name_en_lower
    ON medical_services(LOWER(name_en));

-- ----------------------------------------------------------
-- medical_service_categories: junction table
-- ----------------------------------------------------------

-- Already has: idx_package_services_service ON (service_id)
-- Missing: index on category_id for reverse lookup in getTree
CREATE INDEX IF NOT EXISTS idx_medical_svc_categories_category
    ON medical_service_categories(category_id);

-- Composite: supports the main tree JOIN
CREATE INDEX IF NOT EXISTS idx_medical_svc_categories_composite
    ON medical_service_categories(category_id, service_id);

-- ----------------------------------------------------------
-- medical_categories: lookup by deleted flag
-- ----------------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_medical_categories_deleted_code
    ON medical_categories(deleted, code);

-- ----------------------------------------------------------
-- ent_service_aliases: alias search (used in catalog search ILIKE)
-- ----------------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_ent_service_aliases_text_lower
    ON ent_service_aliases(LOWER(alias_text));

CREATE INDEX IF NOT EXISTS idx_ent_service_aliases_service_id
    ON ent_service_aliases(medical_service_id);
