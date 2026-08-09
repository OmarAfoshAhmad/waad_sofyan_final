-- Supports the hot balance-reader path: one member, applicable buckets and exact periods.
CREATE INDEX IF NOT EXISTS idx_bucket_consumption_member_balance
    ON benefit_bucket_consumptions (member_id, bucket_id, period_start, period_end, status)
    INCLUDE (approved_amount, claim_id);
