-- Maintenance Center — Phase 2: internal error log for backend + frontend errors
-- surfaced to users. Distinct from system_monitoring_error_events (5xx-rate counter
-- for the alert scheduler, added alongside monitoring in V133).

CREATE TABLE IF NOT EXISTS system_error_log (
    id                 BIGSERIAL    PRIMARY KEY,
    occurred_at        TIMESTAMP    NOT NULL,
    source             VARCHAR(20)  NOT NULL,
    severity           VARCHAR(20)  NOT NULL DEFAULT 'ERROR',
    environment        VARCHAR(40),
    correlation_id     VARCHAR(80),
    user_id            BIGINT,
    username           VARCHAR(150),
    role               VARCHAR(80),
    http_method        VARCHAR(20),
    path               VARCHAR(500),
    status_code        INTEGER,
    error_code         VARCHAR(80),
    user_message       VARCHAR(1000),
    technical_message  VARCHAR(2000),
    exception_class    VARCHAR(255),
    stack_excerpt      VARCHAR(4000),
    stack_hash         VARCHAR(80),
    frontend_route     VARCHAR(500),
    browser            VARCHAR(300),
    resolved           BOOLEAN      NOT NULL DEFAULT FALSE,
    resolved_by        VARCHAR(150),
    resolved_at        TIMESTAMP,
    notes              VARCHAR(2000),

    CONSTRAINT chk_error_log_source   CHECK (source IN ('BACKEND', 'FRONTEND')),
    CONSTRAINT chk_error_log_severity CHECK (severity IN ('INFO', 'WARN', 'ERROR', 'CRITICAL'))
);

CREATE INDEX IF NOT EXISTS idx_system_error_log_occurred_at ON system_error_log (occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_system_error_log_source      ON system_error_log (source);
CREATE INDEX IF NOT EXISTS idx_system_error_log_severity    ON system_error_log (severity);
CREATE INDEX IF NOT EXISTS idx_system_error_log_resolved    ON system_error_log (resolved);
CREATE INDEX IF NOT EXISTS idx_system_error_log_correlation ON system_error_log (correlation_id);
