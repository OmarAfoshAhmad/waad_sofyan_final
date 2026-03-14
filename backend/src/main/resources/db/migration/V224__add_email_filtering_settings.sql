-- Add filtering columns to email_settings table
ALTER TABLE email_settings ADD COLUMN subject_filter VARCHAR(255);
ALTER TABLE email_settings ADD COLUMN only_from_providers BOOLEAN DEFAULT FALSE;
