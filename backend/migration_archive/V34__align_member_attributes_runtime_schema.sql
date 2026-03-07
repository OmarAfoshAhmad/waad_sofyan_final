-- V34: Create member_attributes table required by MemberAttribute entity

CREATE TABLE IF NOT EXISTS member_attributes (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    attribute_code VARCHAR(100) NOT NULL,
    attribute_value TEXT,
    source VARCHAR(50),
    source_reference VARCHAR(200),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_member_attributes_member
        FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uk_member_attribute_code'
          AND conrelid = 'member_attributes'::regclass
    ) THEN
        ALTER TABLE member_attributes
            ADD CONSTRAINT uk_member_attribute_code UNIQUE (member_id, attribute_code);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_member_attributes_member_id ON member_attributes(member_id);
CREATE INDEX IF NOT EXISTS idx_member_attributes_code ON member_attributes(attribute_code);
