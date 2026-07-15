ALTER TABLE benefit_groups
    ADD COLUMN IF NOT EXISTS coverage_percent INTEGER,
    ADD COLUMN IF NOT EXISTS copay_percentage NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS requires_preapproval BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS notes TEXT,
    ADD COLUMN IF NOT EXISTS source_clause TEXT;

ALTER TABLE benefit_groups
    ADD CONSTRAINT chk_benefit_group_coverage
        CHECK (coverage_percent IS NULL OR coverage_percent BETWEEN 0 AND 100),
    ADD CONSTRAINT chk_benefit_group_copay
        CHECK (copay_percentage IS NULL OR copay_percentage BETWEEN 0 AND 100);
