ALTER TABLE benefit_limit_buckets
    ADD COLUMN IF NOT EXISTS period_value INTEGER NOT NULL DEFAULT 1;

ALTER TABLE benefit_limit_buckets
    DROP CONSTRAINT IF EXISTS chk_bucket_period;

ALTER TABLE benefit_limit_buckets
    ADD CONSTRAINT ck_benefit_bucket_period CHECK (
        period_type IN ('PER_SERVICE', 'PER_VISIT', 'DAILY', 'MONTHLY', 'ANNUAL',
                        'MULTI_YEAR_POLICY', 'POLICY_PERIOD', 'LIFETIME')
        AND period_value >= 1
        AND (period_type <> 'MULTI_YEAR_POLICY' OR period_value > 1)
    );
