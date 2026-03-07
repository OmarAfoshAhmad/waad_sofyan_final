UPDATE email_verification_tokens
SET expiry_date = expires_at
WHERE expiry_date IS NULL
  AND expires_at IS NOT NULL;

ALTER TABLE email_verification_tokens
    ALTER COLUMN expiry_date DROP NOT NULL;
