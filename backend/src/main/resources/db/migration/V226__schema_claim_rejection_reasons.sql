-- V226: Claim Rejection Reasons lookup table
-- Predefined rejection reasons that can be selected or extended by users

CREATE TABLE IF NOT EXISTS claim_rejection_reasons (
    id          BIGSERIAL PRIMARY KEY,
    reason_text VARCHAR(500) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uq_claim_rejection_reason_text UNIQUE (reason_text)
);

-- Seed predefined reasons
INSERT INTO claim_rejection_reasons (reason_text) VALUES
    ('تجاوز السعر المتفق عليه'),
    ('الخدمة غير مغطاة'),
    ('المستفيد استهلك رصيده')
ON CONFLICT DO NOTHING;
