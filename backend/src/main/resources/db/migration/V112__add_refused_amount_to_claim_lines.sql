ALTER TABLE claim_lines ADD COLUMN refused_amount NUMERIC(15, 2) DEFAULT 0;
COMMENT ON COLUMN claim_lines.refused_amount IS 'Amount refused from this service line (e.g. price excess over contract)';
