-- Maintenance Center — Phase 3: the unified problem ledger.
--
-- Every detector/repair tool in the maintenance center (existing and future) registers
-- its findings here instead of running-and-forgetting. `fingerprint` is what makes this
-- more than a log: the same underlying problem re-detected does not create a new row —
-- it bumps occurrence_count and, if the issue had been resolved/ignored, flips it back
-- to REOPENED automatically.

CREATE TABLE IF NOT EXISTS system_issues (
    id                  BIGSERIAL     PRIMARY KEY,
    issue_type          VARCHAR(60)   NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    severity            VARCHAR(20)   NOT NULL DEFAULT 'MEDIUM',

    -- Stable identity for a problem instance. Two detections of "the same" problem
    -- (e.g. the same claim, the same stack trace) must resolve to the same fingerprint.
    fingerprint         VARCHAR(200)  NOT NULL,

    employer_id         BIGINT,
    entity_type         VARCHAR(60),
    entity_id           VARCHAR(80),

    title_ar            VARCHAR(300)  NOT NULL,
    description_ar      VARCHAR(2000),
    details_json        TEXT,

    detected_at         TIMESTAMP     NOT NULL,
    detected_by_rule     VARCHAR(120),
    occurrence_count    INTEGER       NOT NULL DEFAULT 1,
    last_seen_at        TIMESTAMP     NOT NULL,

    assigned_to         VARCHAR(150),
    assigned_at         TIMESTAMP,

    resolved_at         TIMESTAMP,
    resolved_by         VARCHAR(150),
    resolution_note     VARCHAR(2000),

    CONSTRAINT uq_system_issues_fingerprint UNIQUE (fingerprint),
    CONSTRAINT chk_system_issues_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'IGNORED', 'REOPENED')),
    CONSTRAINT chk_system_issues_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE INDEX IF NOT EXISTS idx_system_issues_status      ON system_issues (status);
CREATE INDEX IF NOT EXISTS idx_system_issues_type         ON system_issues (issue_type);
CREATE INDEX IF NOT EXISTS idx_system_issues_severity     ON system_issues (severity);
CREATE INDEX IF NOT EXISTS idx_system_issues_employer     ON system_issues (employer_id);
CREATE INDEX IF NOT EXISTS idx_system_issues_assigned_to  ON system_issues (assigned_to);
CREATE INDEX IF NOT EXISTS idx_system_issues_detected_at  ON system_issues (detected_at DESC);

-- Every maintenance action (backup run, reconciliation, manual resolve) linked to the
-- issue it addressed, so a single issue's full history is queryable in one place —
-- this is the "سجل صيانة موحّد" the plan calls for, distinct from the audit trail,
-- which is oriented around business entities rather than maintenance operations.
CREATE TABLE IF NOT EXISTS maintenance_operations_log (
    id              BIGSERIAL    PRIMARY KEY,
    issue_id        BIGINT       REFERENCES system_issues (id) ON DELETE SET NULL,
    operation_type  VARCHAR(60)  NOT NULL,
    performed_by    VARCHAR(150) NOT NULL,
    performed_at    TIMESTAMP    NOT NULL,
    details_ar      VARCHAR(2000)
);

CREATE INDEX IF NOT EXISTS idx_maintenance_ops_issue_id     ON maintenance_operations_log (issue_id);
CREATE INDEX IF NOT EXISTS idx_maintenance_ops_performed_at ON maintenance_operations_log (performed_at DESC);
