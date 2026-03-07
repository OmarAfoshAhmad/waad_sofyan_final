-- Add Out-of-Pocket Max to Benefit Policies
ALTER TABLE benefit_policies
ADD COLUMN out_of_pocket_max DECIMAL(15, 2) DEFAULT 0.00;

-- Update existing policies with a default (e.g. 5000) if needed, 
-- or leave as 0 (unlimited/not configured)
UPDATE benefit_policies SET out_of_pocket_max = 5000.00 WHERE out_of_pocket_max IS NULL OR out_of_pocket_max = 0;
