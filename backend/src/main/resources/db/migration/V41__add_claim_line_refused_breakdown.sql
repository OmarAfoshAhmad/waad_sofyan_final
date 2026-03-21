-- V41: Add refused-amount breakdown columns to claim_lines
--
-- price_excess_refused: portion refused because submitted price > contract price
--                       = max(0, requestedUnitPrice - contractPrice) × qty
-- limit_refused       : portion refused due to benefit limits (timesLimit / amountLimit)
--                       = max(0, clientRefused - priceExcessRefused)
--
-- Both default to 0 so existing rows remain valid.

ALTER TABLE claim_lines
    ADD COLUMN IF NOT EXISTS price_excess_refused DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS limit_refused         DECIMAL(15, 2) NOT NULL DEFAULT 0.00;

COMMENT ON COLUMN claim_lines.price_excess_refused IS
    'Amount refused because submitted unit price exceeded the contracted price';
COMMENT ON COLUMN claim_lines.limit_refused IS
    'Amount refused due to benefit limit enforcement (times-per-year or annual-amount caps)';
