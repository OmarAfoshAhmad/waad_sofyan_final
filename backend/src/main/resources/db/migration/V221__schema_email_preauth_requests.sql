-- Phase 2: Email PreAuth Requests Schema
CREATE TABLE pre_auth_email_requests (
    id BIGSERIAL PRIMARY KEY,
    message_id VARCHAR(255) UNIQUE,
    sender_email VARCHAR(255),
    subject VARCHAR(500),
    body_text TEXT,
    body_html TEXT,
    received_at TIMESTAMP,
    processed BOOLEAN DEFAULT FALSE,
    converted_to_pre_auth_id BIGINT,
    provider_id BIGINT,
    member_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Store attachments specifically for email requests before conversion
CREATE TABLE pre_auth_email_attachments (
    id BIGSERIAL PRIMARY KEY,
    email_request_id BIGINT NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_type VARCHAR(100),
    file_size BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_email_request FOREIGN KEY (email_request_id) REFERENCES pre_auth_email_requests(id) ON DELETE CASCADE
);

CREATE INDEX idx_preauth_email_sender ON pre_auth_email_requests(sender_email);
CREATE INDEX idx_preauth_email_processed ON pre_auth_email_requests(processed);
CREATE INDEX idx_preauth_email_received ON pre_auth_email_requests(received_at);
