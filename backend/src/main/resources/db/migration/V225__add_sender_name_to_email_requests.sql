-- Add sender_name to pre_auth_email_requests
ALTER TABLE pre_auth_email_requests ADD COLUMN sender_name VARCHAR(255);
