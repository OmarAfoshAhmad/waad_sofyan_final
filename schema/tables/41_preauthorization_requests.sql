-- ============================================================
-- Table: preauthorization_requests
-- Depends on: providers, members
-- ============================================================
CREATE TABLE IF NOT EXISTS preauthorization_requests (
    id                      BIGSERIAL PRIMARY KEY,
    request_number          VARCHAR(100),
    provider_id             BIGINT NOT NULL,
    member_id               BIGINT NOT NULL,

    service_date            DATE,
    requested_service_date  DATE,
    diagnosis_code          VARCHAR(50),
    diagnosis_description   TEXT,

    requested_amount        NUMERIC(15,2),
    approved_amount         NUMERIC(15,2),

    status                  VARCHAR(50)
        CHECK (status IN ('PENDING','APPROVED','REJECTED','EXPIRED','CANCELLED')),

    valid_from              TIMESTAMP,
    valid_until             TIMESTAMP,

    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP,
    approved_at             TIMESTAMP,
    created_by              VARCHAR(255),
    approved_by             VARCHAR(255),

    CONSTRAINT fk_pauthreq_provider FOREIGN KEY (provider_id) REFERENCES providers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pauthreq_member   FOREIGN KEY (member_id)   REFERENCES members(id)   ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_pauthreq_member_status_date ON preauthorization_requests(member_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pauthreq_expiring           ON preauthorization_requests(valid_until)
    WHERE status = 'APPROVED' AND valid_until IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pauthreq_provider_date      ON preauthorization_requests(provider_id, created_at DESC, status);

-- Extra performance indexes (from legacy V090)
CREATE INDEX IF NOT EXISTS idx_preauth_member_status_date ON preauthorization_requests(member_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_preauth_expiring           ON preauthorization_requests(valid_until)
    WHERE status = 'APPROVED' AND valid_until IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_preauth_provider_date_status ON preauthorization_requests(provider_id, created_at DESC, status);
