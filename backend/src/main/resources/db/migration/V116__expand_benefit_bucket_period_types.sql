ALTER TABLE benefit_limit_buckets
    DROP CONSTRAINT IF EXISTS ck_benefit_bucket_period;

ALTER TABLE benefit_limit_buckets
    DROP CONSTRAINT IF EXISTS chk_bucket_period;

ALTER TABLE benefit_limit_buckets
    ADD CONSTRAINT ck_benefit_bucket_period CHECK (
        period_type IN (
            'PER_SERVICE',
            'PER_VISIT',
            'DAILY',
            'WEEKLY',
            'MONTHLY',
            'QUARTERLY',
            'ANNUAL',
            'MULTI_YEAR_POLICY',
            'CUSTOM_DAYS',
            'CUSTOM_WEEKS',
            'CUSTOM_MONTHS',
            'CUSTOM_YEARS',
            'POLICY_PERIOD',
            'LIFETIME'
        )
        AND period_value >= 1
        AND (
            period_type NOT IN ('MULTI_YEAR_POLICY', 'CUSTOM_DAYS', 'CUSTOM_WEEKS', 'CUSTOM_MONTHS', 'CUSTOM_YEARS')
            OR period_value > 1
        )
    );
