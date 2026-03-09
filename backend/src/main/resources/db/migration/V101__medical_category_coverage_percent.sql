-- V101: Add coverage_percent to medical_categories
-- Used by the Medical Services Mapping center to track how well each
-- category's services are mapped to unified catalog entries.

ALTER TABLE medical_categories
    ADD COLUMN IF NOT EXISTS coverage_percent DECIMAL(5,2) DEFAULT NULL;

COMMENT ON COLUMN medical_categories.coverage_percent IS
    'Admin-managed target coverage percentage for this category (0–100). NULL = not set.';
