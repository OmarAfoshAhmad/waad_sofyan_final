-- Family structure is deliberately one level: a principal has no parent and
-- no relationship; a dependent has both, belongs to the same employer, and
-- can never itself be a parent. Audit existing rows before making the rules
-- authoritative -- never "repair" identity relationships silently.
DO $$
DECLARE
    bad_shape BIGINT;
    self_parent BIGINT;
    nested_parent BIGINT;
    cross_employer BIGINT;
BEGIN
    SELECT count(*) INTO bad_shape FROM members
     WHERE (parent_id IS NULL) <> (relationship IS NULL);
    SELECT count(*) INTO self_parent FROM members WHERE parent_id = id;
    SELECT count(*) INTO nested_parent
      FROM members child JOIN members parent ON parent.id = child.parent_id
     WHERE parent.parent_id IS NOT NULL;
    SELECT count(*) INTO cross_employer
      FROM members child JOIN members parent ON parent.id = child.parent_id
     WHERE child.employer_id IS DISTINCT FROM parent.employer_id;

    IF bad_shape > 0 OR self_parent > 0 OR nested_parent > 0 OR cross_employer > 0 THEN
        RAISE EXCEPTION USING
            MESSAGE = format('V184 family audit failed: bad_shape=%s, self_parent=%s, nested_parent=%s, cross_employer=%s',
                             bad_shape, self_parent, nested_parent, cross_employer),
            HINT = 'Correct family records explicitly with an audited business decision before retrying V184.';
    END IF;
END $$;

ALTER TABLE members DROP CONSTRAINT IF EXISTS fk_member_parent;
ALTER TABLE members
    ADD CONSTRAINT fk_member_parent
    FOREIGN KEY (parent_id) REFERENCES members(id) ON DELETE RESTRICT;

ALTER TABLE members
    ADD CONSTRAINT chk_member_family_shape
        CHECK ((parent_id IS NULL AND relationship IS NULL)
            OR (parent_id IS NOT NULL AND relationship IS NOT NULL)),
    ADD CONSTRAINT chk_member_not_own_parent
        CHECK (parent_id IS NULL OR parent_id <> id);

-- Display order is not identity. Never resequence card_number/barcode merely
-- because a family wants a different visual order.
ALTER TABLE members ADD COLUMN family_order INTEGER;
WITH ranked AS (
    SELECT id, row_number() OVER (PARTITION BY parent_id ORDER BY birth_date NULLS LAST, id)::INTEGER AS position
      FROM members WHERE parent_id IS NOT NULL
)
UPDATE members m SET family_order = ranked.position FROM ranked WHERE ranked.id = m.id;
ALTER TABLE members ADD CONSTRAINT chk_member_family_order_shape CHECK (
    (parent_id IS NULL AND family_order IS NULL)
    OR (parent_id IS NOT NULL AND family_order IS NOT NULL AND family_order > 0));
CREATE UNIQUE INDEX uq_member_family_order ON members(parent_id, family_order) WHERE parent_id IS NOT NULL;

CREATE OR REPLACE FUNCTION enforce_member_family_integrity()
RETURNS trigger AS $$
DECLARE
    parent_parent_id BIGINT;
    parent_employer_id BIGINT;
BEGIN
    IF NEW.parent_id IS NULL THEN
        NEW.family_order := NULL;
        RETURN NEW;
    END IF;

    PERFORM pg_advisory_xact_lock(184, (NEW.parent_id % 2147483647)::INTEGER);
    IF NEW.family_order IS NULL THEN
        SELECT COALESCE(max(family_order), 0) + 1 INTO NEW.family_order
          FROM members WHERE parent_id = NEW.parent_id AND id IS DISTINCT FROM NEW.id;
    END IF;

    SELECT parent_id, employer_id
      INTO parent_parent_id, parent_employer_id
      FROM members
     WHERE id = NEW.parent_id
     FOR KEY SHARE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'FAMILY_PARENT_NOT_FOUND: %', NEW.parent_id;
    END IF;
    IF parent_parent_id IS NOT NULL THEN
        RAISE EXCEPTION 'FAMILY_PARENT_MUST_BE_PRINCIPAL: %', NEW.parent_id;
    END IF;
    IF NEW.employer_id IS DISTINCT FROM parent_employer_id THEN
        RAISE EXCEPTION 'FAMILY_EMPLOYER_MISMATCH: member employer %, parent employer %',
            NEW.employer_id, parent_employer_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_member_family_integrity
BEFORE INSERT OR UPDATE OF parent_id, relationship, employer_id, family_order ON members
FOR EACH ROW EXECUTE FUNCTION enforce_member_family_integrity();

-- Permanent audit history of structural changes. No FK to members: hard
-- deletion, where exceptionally allowed, must not erase who was moved.
CREATE TABLE member_family_transitions (
    id                    BIGSERIAL PRIMARY KEY,
    transition_id         UUID NOT NULL,
    member_id             BIGINT NOT NULL,
    previous_parent_id    BIGINT,
    new_parent_id         BIGINT,
    previous_relationship VARCHAR(20),
    new_relationship      VARCHAR(20),
    effective_date        DATE NOT NULL,
    reason                VARCHAR(500) NOT NULL,
    transition_type       VARCHAR(30) NOT NULL,
    changed_by            BIGINT,
    member_name           VARCHAR(200),
    member_card_number    VARCHAR(50),
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_member_family_transition_type
        CHECK (transition_type IN ('TRANSFER', 'RELATIONSHIP_CORRECTION')),
    CONSTRAINT chk_member_family_transition_changed
        CHECK (previous_parent_id IS DISTINCT FROM new_parent_id
            OR previous_relationship IS DISTINCT FROM new_relationship)
);

CREATE INDEX idx_member_family_transitions_member_created
    ON member_family_transitions(member_id, created_at DESC);

CREATE OR REPLACE FUNCTION member_family_transition_append_only()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'member_family_transitions is append-only: % is not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_member_family_transition_no_update
BEFORE UPDATE OR DELETE ON member_family_transitions
FOR EACH ROW EXECUTE FUNCTION member_family_transition_append_only();
