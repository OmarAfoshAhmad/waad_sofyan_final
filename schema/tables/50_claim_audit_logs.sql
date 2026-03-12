-- ============================================================
-- Table: claim_audit_logs
-- Depends on: claims
-- ============================================================
CREATE TABLE IF NOT EXISTS claim_audit_logs (
    id                         BIGSERIAL PRIMARY KEY,
    claim_id                   BIGINT NOT NULL,
    change_type                VARCHAR(50) NOT NULL,
    previous_status            VARCHAR(30),
    new_status                 VARCHAR(30),
    previous_requested_amount  NUMERIC(15,2),
    new_requested_amount       NUMERIC(15,2),
    previous_approved_amount   NUMERIC(15,2),
    new_approved_amount        NUMERIC(15,2),
    actor_user_id              BIGINT NOT NULL,
    actor_username             VARCHAR(100) NOT NULL,
    actor_role                 VARCHAR(50) NOT NULL,
    timestamp                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    comment                    TEXT,
    ip_address                 VARCHAR(45),
    before_snapshot            TEXT,
    after_snapshot             TEXT,

    CONSTRAINT fk_claim_audit_claim FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_claim_audit_claim_timestamp ON claim_audit_logs(claim_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_claim_audit_user_timestamp  ON claim_audit_logs(actor_user_id, timestamp DESC);
