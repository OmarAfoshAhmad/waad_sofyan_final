-- Add detected_service_id to pre_auth_email_requests
ALTER TABLE pre_auth_email_requests ADD COLUMN detected_service_id BIGINT;
ALTER TABLE pre_auth_email_requests ADD CONSTRAINT fk_email_detected_service FOREIGN KEY (detected_service_id) REFERENCES medical_services(id);
