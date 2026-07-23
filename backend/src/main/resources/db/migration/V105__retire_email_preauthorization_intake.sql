-- Provider portal is the sole intake channel for pre-authorizations.
-- Retire all inbound-email/IMAP persistence while preserving outbound SMTP.

DROP TABLE IF EXISTS pre_auth_email_attachments;
DROP TABLE IF EXISTS pre_auth_email_requests;

ALTER TABLE pre_authorizations
    DROP COLUMN IF EXISTS email_request_id;

ALTER TABLE email_settings
    DROP COLUMN IF EXISTS imap_host,
    DROP COLUMN IF EXISTS imap_port,
    DROP COLUMN IF EXISTS imap_username,
    DROP COLUMN IF EXISTS imap_password,
    DROP COLUMN IF EXISTS listener_enabled,
    DROP COLUMN IF EXISTS sync_interval_mins,
    DROP COLUMN IF EXISTS subject_filter,
    DROP COLUMN IF EXISTS only_from_providers,
    DROP COLUMN IF EXISTS last_sync_at;
