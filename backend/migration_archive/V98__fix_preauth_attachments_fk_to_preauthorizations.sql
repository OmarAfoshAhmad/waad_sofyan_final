-- Fix pre-authorization attachments FK to reference canonical table used by runtime entity
-- Root cause: FK still points to legacy table preauthorization_requests, while API v1 writes to pre_authorizations

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_name = 'pre_authorization_attachments'
          AND constraint_name = 'fk_preauth_attachment_preauth'
    ) THEN
        ALTER TABLE pre_authorization_attachments
            DROP CONSTRAINT fk_preauth_attachment_preauth;
    END IF;
END $$;

ALTER TABLE pre_authorization_attachments
    ADD CONSTRAINT fk_preauth_attachment_preauth
    FOREIGN KEY (pre_authorization_id)
    REFERENCES pre_authorizations(id)
    ON DELETE CASCADE
    NOT VALID;
