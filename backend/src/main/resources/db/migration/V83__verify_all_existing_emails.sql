-- Verify all existing users emails to ensure no lockout when REQUIRE_EMAIL_VERIFICATION is enabled
UPDATE users SET email_verified = true WHERE email_verified = false;
