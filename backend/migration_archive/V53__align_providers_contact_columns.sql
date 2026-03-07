-- Align providers contact column names with Provider entity mapping

ALTER TABLE providers
    ADD COLUMN IF NOT EXISTS email VARCHAR(100),
    ADD COLUMN IF NOT EXISTS phone VARCHAR(50);

UPDATE providers
SET email = COALESCE(email, contact_email)
WHERE email IS NULL;

UPDATE providers
SET phone = COALESCE(phone, contact_phone)
WHERE phone IS NULL;
