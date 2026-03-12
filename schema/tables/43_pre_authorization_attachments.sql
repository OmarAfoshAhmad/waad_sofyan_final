-- ============================================================
-- Table: pre_authorization_attachments
-- Depends on: preauthorization_requests
-- ============================================================
CREATE TABLE IF NOT EXISTS pre_authorization_attachments (
    id                          BIGSERIAL PRIMARY KEY,
    preauthorization_request_id BIGINT NOT NULL,
    file_name                   VARCHAR(500) NOT NULL,
    file_path                   VARCHAR(500),
    file_type                   VARCHAR(100),
    file_size                   BIGINT,
    attachment_type             VARCHAR(50),
    uploaded_by                 VARCHAR(255),
    uploaded_at                 TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_preauth_att FOREIGN KEY (preauthorization_request_id)
        REFERENCES preauthorization_requests(id) ON DELETE CASCADE
);
