-- Duplicate resolution is an identity link, never a reassignment of medical
-- or financial history. Existing claims/visits/ledger rows stay on the member
-- who actually owned them.
ALTER TABLE members DROP CONSTRAINT IF EXISTS members_status_check;
ALTER TABLE members DROP CONSTRAINT IF EXISTS chk_member_status_active_consistency;
ALTER TABLE members ADD CONSTRAINT members_status_check
    CHECK (status IN ('ACTIVE','SUSPENDED','TERMINATED','PENDING','DUPLICATE_MERGED'));
ALTER TABLE members ADD CONSTRAINT chk_member_status_active_consistency CHECK (
    (status = 'ACTIVE' AND active = TRUE)
    OR (status IN ('SUSPENDED','TERMINATED','PENDING','DUPLICATE_MERGED') AND active = FALSE));

CREATE TABLE member_merge_records (
    id                    BIGSERIAL PRIMARY KEY,
    merge_id              UUID NOT NULL,
    duplicate_member_id   BIGINT NOT NULL,
    primary_member_id     BIGINT NOT NULL,
    reason                VARCHAR(500) NOT NULL,
    merged_by             BIGINT,
    duplicate_name        VARCHAR(200),
    duplicate_card_number VARCHAR(50),
    primary_name          VARCHAR(200),
    primary_card_number   VARCHAR(50),
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_member_merge_duplicate UNIQUE (duplicate_member_id),
    CONSTRAINT chk_member_merge_not_self CHECK (duplicate_member_id <> primary_member_id),
    CONSTRAINT fk_member_merge_duplicate FOREIGN KEY (duplicate_member_id) REFERENCES members(id) ON DELETE RESTRICT,
    CONSTRAINT fk_member_merge_primary FOREIGN KEY (primary_member_id) REFERENCES members(id) ON DELETE RESTRICT
);
CREATE INDEX idx_member_merge_primary ON member_merge_records(primary_member_id);

CREATE OR REPLACE FUNCTION enforce_member_merge_acyclic() RETURNS trigger AS $$
DECLARE cycle_found BOOLEAN;
BEGIN
    WITH RECURSIVE chain(id) AS (
        SELECT NEW.primary_member_id
        UNION ALL
        SELECT r.primary_member_id FROM member_merge_records r JOIN chain c ON r.duplicate_member_id = c.id
    )
    SELECT EXISTS(SELECT 1 FROM chain WHERE id = NEW.duplicate_member_id) INTO cycle_found;
    IF cycle_found THEN RAISE EXCEPTION 'MEMBER_MERGE_CYCLE'; END IF;
    RETURN NEW;
END $$ LANGUAGE plpgsql;
CREATE TRIGGER trg_member_merge_acyclic BEFORE INSERT ON member_merge_records
FOR EACH ROW EXECUTE FUNCTION enforce_member_merge_acyclic();

CREATE OR REPLACE FUNCTION member_merge_records_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'member_merge_records is append-only: % is not allowed', TG_OP;
END $$ LANGUAGE plpgsql;
CREATE TRIGGER trg_member_merge_records_no_update BEFORE UPDATE OR DELETE ON member_merge_records
FOR EACH ROW EXECUTE FUNCTION member_merge_records_append_only();
