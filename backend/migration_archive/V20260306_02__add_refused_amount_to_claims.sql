-- Add refused_amount column to claims table
ALTER TABLE claims ADD COLUMN IF NOT EXISTS refused_amount DECIMAL(15,2) DEFAULT 0;

-- Backfill refused_amount for already REJECTED claims
UPDATE claims SET refused_amount = requested_amount WHERE status = 'REJECTED';
