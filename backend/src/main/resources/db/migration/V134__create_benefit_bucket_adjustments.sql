-- Non-financial, append-only adjustments for migrated/opening benefit usage.
-- These rows affect benefit-limit availability only. They are deliberately
-- independent from claims, settlements, payments and accounting records.
CREATE TABLE benefit_bucket_adjustments (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES members(id),
    policy_id BIGINT NOT NULL REFERENCES benefit_policies(id),
    bucket_id BIGINT NOT NULL REFERENCES benefit_limit_buckets(id),
    period_start DATE NOT NULL,
    period_end DATE,
    amount_delta NUMERIC(15,2) NOT NULL DEFAULT 0,
    times_delta INTEGER NOT NULL DEFAULT 0,
    days_delta INTEGER NOT NULL DEFAULT 0,
    adjustment_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    source_batch_id VARCHAR(100) NOT NULL,
    source_reference VARCHAR(500),
    reason VARCHAR(1000),
    idempotency_key VARCHAR(220) NOT NULL,
    reversal_of_id BIGINT REFERENCES benefit_bucket_adjustments(id),
    created_by_user_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reversed_at TIMESTAMP,
    CONSTRAINT uk_benefit_bucket_adjustment_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_benefit_bucket_adjustment_type
        CHECK (adjustment_type IN ('OPENING_BALANCE', 'MANUAL_CORRECTION', 'REVERSAL')),
    CONSTRAINT ck_benefit_bucket_adjustment_status
        CHECK (status IN ('ACTIVE', 'REVERSED')),
    CONSTRAINT ck_benefit_bucket_adjustment_values
        CHECK (amount_delta >= 0 AND times_delta >= 0 AND days_delta >= 0),
    CONSTRAINT ck_benefit_bucket_adjustment_period
        CHECK (period_end IS NULL OR period_end >= period_start)
);

CREATE INDEX idx_benefit_bucket_adjustment_usage
    ON benefit_bucket_adjustments(member_id, bucket_id, period_start, period_end)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_benefit_bucket_adjustment_batch
    ON benefit_bucket_adjustments(source_batch_id);

