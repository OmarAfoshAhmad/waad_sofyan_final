-- ============================================================
-- V107: Support Many-to-Many Roots for Medical Categories
-- ============================================================
-- Allows a sub-category (e.g. Lab) to belong to multiple roots (OP, IP, etc.)

CREATE TABLE IF NOT EXISTS medical_category_roots (
    category_id BIGINT NOT NULL,
    root_id     BIGINT NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    PRIMARY KEY (category_id, root_id),
    CONSTRAINT fk_mcr_category FOREIGN KEY (category_id) REFERENCES medical_categories(id) ON DELETE CASCADE,
    CONSTRAINT fk_mcr_root     FOREIGN KEY (root_id)     REFERENCES medical_categories(id) ON DELETE CASCADE
);

-- Index for reverse lookup
CREATE INDEX IF NOT EXISTS idx_mcr_root_id ON medical_category_roots(root_id);

-- Migrate existing single parent_id to medical_category_roots
INSERT INTO medical_category_roots (category_id, root_id)
SELECT id, parent_id FROM medical_categories 
WHERE parent_id IS NOT NULL
ON CONFLICT DO NOTHING;
