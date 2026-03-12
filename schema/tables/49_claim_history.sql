-- ============================================================
-- Table: claim_history
-- Depends on: claims
-- ============================================================
CREATE TABLE IF NOT EXISTS claim_history (
    id         BIGSERIAL PRIMARY KEY,
    claim_id   BIGINT NOT NULL,
    old_status VARCHAR(50),
    new_status VARCHAR(50),
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    changed_by VARCHAR(255),
    reason     TEXT,

    CONSTRAINT fk_claim_history FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_claim_history_timeline ON claim_history(claim_id, changed_at DESC, new_status);
