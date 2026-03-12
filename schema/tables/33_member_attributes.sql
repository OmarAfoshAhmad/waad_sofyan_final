-- ============================================================
-- Table: member_attributes
-- Depends on: members
-- ============================================================
CREATE TABLE IF NOT EXISTS member_attributes (
    id               BIGSERIAL PRIMARY KEY,
    member_id        BIGINT NOT NULL,
    attribute_code   VARCHAR(100) NOT NULL,
    attribute_value  TEXT,
    source           VARCHAR(50),
    source_reference VARCHAR(200),
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    created_at       TIMESTAMP,
    updated_at       TIMESTAMP,

    CONSTRAINT fk_member_attrs_member FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE,
    CONSTRAINT uk_member_attribute_code UNIQUE (member_id, attribute_code)
);

CREATE INDEX IF NOT EXISTS idx_member_attributes_member ON member_attributes(member_id);
CREATE INDEX IF NOT EXISTS idx_member_attributes_code   ON member_attributes(attribute_code);
