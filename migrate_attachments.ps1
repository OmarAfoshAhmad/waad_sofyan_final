$env:PGPASSWORD = 'postgres'
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -d tba_waad_system -c @"
-- Add missing columns to match the Entity definition
ALTER TABLE pre_authorization_attachments
  ADD COLUMN IF NOT EXISTS pre_authorization_id BIGINT,
  ADD COLUMN IF NOT EXISTS original_file_name VARCHAR(255),
  ADD COLUMN IF NOT EXISTS stored_file_name VARCHAR(255),
  ADD COLUMN IF NOT EXISTS created_at TIMESTAMP,
  ADD COLUMN IF NOT EXISTS created_by VARCHAR(100);

-- Migrate data from old columns to new columns
UPDATE pre_authorization_attachments
SET
  pre_authorization_id = preauthorization_request_id,
  original_file_name = file_name,
  stored_file_name = file_name,
  created_at = COALESCE(uploaded_at, NOW()),
  created_by = uploaded_by
WHERE pre_authorization_id IS NULL;

-- Make pre_authorization_id NOT NULL after migration
ALTER TABLE pre_authorization_attachments
  ALTER COLUMN pre_authorization_id SET NOT NULL,
  ALTER COLUMN created_at SET NOT NULL;

SELECT 'Migration completed. Columns added.' AS result;
SELECT column_name, data_type FROM information_schema.columns WHERE table_name='pre_authorization_attachments' ORDER BY ordinal_position;
"@
