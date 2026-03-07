-- Add is_backlog column to claims table
ALTER TABLE claims ADD COLUMN IF NOT EXISTS is_backlog BOOLEAN DEFAULT FALSE;
