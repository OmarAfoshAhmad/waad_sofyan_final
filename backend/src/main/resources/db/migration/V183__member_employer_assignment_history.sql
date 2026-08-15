-- Dated employer ownership for members. Current members.employer_id remains a
-- display/indexing pointer only; dated business decisions use this history.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE member_employer_assignments (
    id                    BIGSERIAL PRIMARY KEY,
    member_id             BIGINT NOT NULL,
    employer_id           BIGINT NOT NULL REFERENCES employers(id) ON DELETE RESTRICT,
    assignment_start_date DATE NOT NULL,
    assignment_end_date   DATE,
    assignment_reason     VARCHAR(500) NOT NULL,
    assignment_source     VARCHAR(30) NOT NULL,
    assigned_by           BIGINT,
    member_full_name      VARCHAR(200),
    member_card_number    VARCHAR(50),
    employer_name         VARCHAR(255),
    employer_code         VARCHAR(100),
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_member_employer_assignment_dates
        CHECK (assignment_end_date IS NULL OR assignment_end_date > assignment_start_date),
    CONSTRAINT chk_member_employer_assignment_source
        CHECK (assignment_source IN ('MANUAL', 'IMPORT', 'BACKFILL', 'FAMILY_CASCADE', 'SYSTEM')),
    CONSTRAINT uk_member_employer_assignment_no_overlap
        EXCLUDE USING gist (
            member_id WITH =,
            daterange(assignment_start_date, assignment_end_date, '[)') WITH &&
        )
);

COMMENT ON COLUMN member_employer_assignments.member_id IS
    'Immutable identifier snapshot; deliberately no FK so employer history survives member hard delete';

INSERT INTO member_employer_assignments (
    member_id, employer_id, assignment_start_date, assignment_reason,
    assignment_source, member_full_name, member_card_number,
    employer_name, employer_code, created_at)
SELECT m.id, m.employer_id,
       COALESCE(m.start_date, m.created_at::date, DATE '1900-01-01'),
       'ترحيل تلقائي: لم يكن هناك سجل زمني لجهة العمل قبل V183',
       'BACKFILL', m.full_name, m.card_number, e.name, e.code, now()
FROM members m
JOIN employers e ON e.id = m.employer_id;

CREATE INDEX idx_member_employer_assignments_member_start
    ON member_employer_assignments(member_id, assignment_start_date DESC);
CREATE INDEX idx_member_employer_assignments_employer
    ON member_employer_assignments(employer_id);

CREATE OR REPLACE FUNCTION member_employer_assignment_guard()
RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'member_employer_assignments is append-only: DELETE is not allowed';
    END IF;

    IF NEW.member_id IS DISTINCT FROM OLD.member_id
       OR NEW.employer_id IS DISTINCT FROM OLD.employer_id
       OR NEW.assignment_start_date IS DISTINCT FROM OLD.assignment_start_date
       OR NEW.assignment_reason IS DISTINCT FROM OLD.assignment_reason
       OR NEW.assignment_source IS DISTINCT FROM OLD.assignment_source
       OR NEW.assigned_by IS DISTINCT FROM OLD.assigned_by
       OR NEW.member_full_name IS DISTINCT FROM OLD.member_full_name
       OR NEW.member_card_number IS DISTINCT FROM OLD.member_card_number
       OR NEW.employer_name IS DISTINCT FROM OLD.employer_name
       OR NEW.employer_code IS DISTINCT FROM OLD.employer_code
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'member_employer_assignments: only assignment_end_date may be updated';
    END IF;

    IF OLD.assignment_end_date IS NOT NULL
       AND NEW.assignment_end_date IS DISTINCT FROM OLD.assignment_end_date THEN
        RAISE EXCEPTION 'member_employer_assignments: an already-closed assignment cannot be re-dated';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_member_employer_assignment_no_delete
BEFORE DELETE ON member_employer_assignments
FOR EACH ROW EXECUTE FUNCTION member_employer_assignment_guard();

CREATE TRIGGER trg_member_employer_assignment_update_guard
BEFORE UPDATE ON member_employer_assignments
FOR EACH ROW EXECUTE FUNCTION member_employer_assignment_guard();
