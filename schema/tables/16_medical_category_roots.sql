-- ============================================================
-- Table: medical_category_roots
-- Depends on: medical_categories
-- ============================================================
CREATE TABLE IF NOT EXISTS medical_category_roots (
    category_id BIGINT NOT NULL,
    root_id     BIGINT NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (category_id, root_id),
    CONSTRAINT fk_mcr_category FOREIGN KEY (category_id) REFERENCES medical_categories(id) ON DELETE CASCADE,
    CONSTRAINT fk_mcr_root     FOREIGN KEY (root_id)     REFERENCES medical_categories(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_mcr_root_id ON medical_category_roots(root_id);
