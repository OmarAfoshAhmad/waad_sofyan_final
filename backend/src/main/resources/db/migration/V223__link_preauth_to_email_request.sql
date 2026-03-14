-- Add email_request_id to pre_authorizations
ALTER TABLE pre_authorizations ADD COLUMN email_request_id BIGINT;
ALTER TABLE pre_authorizations ADD CONSTRAINT fk_preauth_email_request FOREIGN KEY (email_request_id) REFERENCES pre_auth_email_requests(id);
