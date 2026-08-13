-- ============================================================
-- V169: centralize the member status lifecycle.
--
-- Before this migration, `status` and `active` on `members` could be set
-- independently by several different code paths (toggleActive, changeStatus,
-- the Excel import row processor, deleteMember) with no single source of
-- truth and no DB-level guard -- toggleActive in particular could set
-- active=true while status stayed SUSPENDED/TERMINATED (or vice versa),
-- since it never touched `status` at all. From here on, `status` is the
-- source of truth and `active` is a derived, DB-enforced consequence of it,
-- never an independent business state.
-- ============================================================

-- One-time correction: force any row that already drifted (from the
-- toggleActive gap above) into the consistent shape before the CHECK
-- constraint below would otherwise reject it.
--
-- A row claiming status='ACTIVE' but with no benefit_policy_id is already
-- inconsistent with the OLDER chk_active_member_requires_policy constraint
-- (an active member must have a policy) -- simply forcing active=true for
-- it here would trade one violation for another. Such a row can't
-- legitimately be ACTIVE, so it moves to SUSPENDED instead of being
-- "activated" by this backfill.
UPDATE members SET status = 'SUSPENDED', active = false
    WHERE status = 'ACTIVE' AND benefit_policy_id IS NULL;

UPDATE members SET active = (status = 'ACTIVE') WHERE active <> (status = 'ACTIVE');

ALTER TABLE members
    ADD CONSTRAINT chk_member_status_active_consistency
    CHECK (
        (status = 'ACTIVE' AND active = true)
        OR
        (status IN ('SUSPENDED', 'TERMINATED', 'PENDING') AND active = false)
    );

-- Last-transition fields (the current snapshot only -- member_status_history
-- below is the append-only record of every transition, not just the last).
ALTER TABLE members
    ADD COLUMN IF NOT EXISTS status_reason        VARCHAR(500),
    ADD COLUMN IF NOT EXISTS status_source         VARCHAR(30),
    ADD COLUMN IF NOT EXISTS status_changed_at     TIMESTAMP,
    ADD COLUMN IF NOT EXISTS status_changed_by     BIGINT,
    ADD COLUMN IF NOT EXISTS previous_status       VARCHAR(20),
    ADD COLUMN IF NOT EXISTS status_transition_id  VARCHAR(64);

ALTER TABLE members
    ADD CONSTRAINT chk_member_status_source
    CHECK (status_source IS NULL OR status_source IN
        ('MANUAL', 'IMPORT', 'FAMILY_CASCADE', 'POLICY_EXPIRY', 'EMPLOYER_SUSPENSION', 'SYSTEM'));

CREATE INDEX IF NOT EXISTS idx_members_status_transition_id ON members(status_transition_id)
    WHERE status_transition_id IS NOT NULL;

-- Append-only history of every status transition. member_id has NO foreign
-- key to members(id) with a blocking delete rule -- it CASCADEs, since a
-- member's own status history is administrative metadata about that member,
-- not financial/medical audit evidence (claims, visits, preauth, eligibility
-- checks, benefit-bucket consumption remain the things that block hard
-- delete; see hardDeleteMember). A hard-deleted member's status history goes
-- with it.
CREATE TABLE IF NOT EXISTS member_status_history (
    id              BIGSERIAL PRIMARY KEY,
    member_id       BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    from_status     VARCHAR(20),
    to_status       VARCHAR(20) NOT NULL,
    reason          VARCHAR(500),
    source          VARCHAR(30) NOT NULL
        CHECK (source IN ('MANUAL', 'IMPORT', 'FAMILY_CASCADE', 'POLICY_EXPIRY', 'EMPLOYER_SUSPENSION', 'SYSTEM')),
    transition_id   VARCHAR(64) NOT NULL,
    changed_at      TIMESTAMP NOT NULL DEFAULT now(),
    changed_by      BIGINT
);

CREATE INDEX IF NOT EXISTS idx_member_status_history_member    ON member_status_history(member_id, changed_at DESC);
CREATE INDEX IF NOT EXISTS idx_member_status_history_transition ON member_status_history(transition_id);

CREATE OR REPLACE FUNCTION prevent_member_status_history_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'member_status_history is append-only: operation % is not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_no_update_member_status_history ON member_status_history;
CREATE TRIGGER trg_no_update_member_status_history
BEFORE UPDATE ON member_status_history
FOR EACH ROW EXECUTE FUNCTION prevent_member_status_history_mutation();

DROP TRIGGER IF EXISTS trg_no_delete_member_status_history ON member_status_history;
CREATE TRIGGER trg_no_delete_member_status_history
BEFORE DELETE ON member_status_history
FOR EACH ROW EXECUTE FUNCTION prevent_member_status_history_mutation();

-- Independent audit record for a hard (physical) delete -- deliberately NOT
-- foreign-keyed to members(id): the whole point is that it survives the
-- member row it describes ceasing to exist. Snapshots the identifying
-- details a member_status_history CASCADE would otherwise take with it.
CREATE TABLE IF NOT EXISTS member_hard_delete_audit (
    id                  BIGSERIAL PRIMARY KEY,
    member_id           BIGINT NOT NULL,
    member_full_name    VARCHAR(200),
    member_card_number  VARCHAR(50),
    employer_id         BIGINT,
    was_principal       BOOLEAN NOT NULL,
    reason              VARCHAR(500) NOT NULL,
    performed_by        BIGINT,
    performed_by_username VARCHAR(100),
    performed_at        TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_member_hard_delete_audit_member ON member_hard_delete_audit(member_id);

CREATE OR REPLACE FUNCTION prevent_member_hard_delete_audit_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'member_hard_delete_audit is append-only: operation % is not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_no_update_member_hard_delete_audit ON member_hard_delete_audit;
CREATE TRIGGER trg_no_update_member_hard_delete_audit
BEFORE UPDATE ON member_hard_delete_audit
FOR EACH ROW EXECUTE FUNCTION prevent_member_hard_delete_audit_mutation();

DROP TRIGGER IF EXISTS trg_no_delete_member_hard_delete_audit ON member_hard_delete_audit;
CREATE TRIGGER trg_no_delete_member_hard_delete_audit
BEFORE DELETE ON member_hard_delete_audit
FOR EACH ROW EXECUTE FUNCTION prevent_member_hard_delete_audit_mutation();
