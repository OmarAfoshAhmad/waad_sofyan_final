CREATE TABLE member_import_preview_tickets (
    token UUID PRIMARY KEY,
    user_id BIGINT NOT NULL,
    file_hash VARCHAR(64) NOT NULL,
    default_employer_id BIGINT,
    benefit_policy_id BIGINT,
    header_row_number INTEGER,
    clear_old_members BOOLEAN NOT NULL,
    custom_mappings_hash VARCHAR(64) NOT NULL,
    resolved_employer_ids BIGINT[] NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_member_import_preview_expiry CHECK (expires_at > created_at)
);
CREATE INDEX idx_member_import_preview_expiry ON member_import_preview_tickets(expires_at)
    WHERE consumed_at IS NULL;
