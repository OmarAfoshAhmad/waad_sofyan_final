-- ============================================================
-- Table: member_deductibles
-- Depends on: members
-- ============================================================
CREATE TABLE IF NOT EXISTS member_deductibles (
    id                    BIGSERIAL PRIMARY KEY,
    member_id             BIGINT NOT NULL,
    deductible_year       INTEGER NOT NULL,
    total_deductible      NUMERIC(10,2) DEFAULT 0.00,
    deductible_used       NUMERIC(10,2) DEFAULT 0.00,
    deductible_remaining  NUMERIC(10,2) DEFAULT 0.00,
    version               BIGINT DEFAULT 0,
    updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by            VARCHAR(255),

    CONSTRAINT fk_deductible_member        FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE RESTRICT,
    CONSTRAINT uq_member_deductible_year   UNIQUE (member_id, deductible_year),
    CONSTRAINT chk_deductible_math         CHECK (deductible_remaining = total_deductible - deductible_used),
    CONSTRAINT chk_deductible_non_negative CHECK (deductible_used >= 0 AND deductible_remaining >= 0)
);

CREATE INDEX IF NOT EXISTS idx_deductibles_member     ON member_deductibles(member_id);
CREATE INDEX IF NOT EXISTS idx_deductibles_year       ON member_deductibles(deductible_year);
CREATE INDEX IF NOT EXISTS idx_deductibles_near_limit ON member_deductibles(member_id, deductible_year)
    WHERE deductible_used >= total_deductible * 0.8;
