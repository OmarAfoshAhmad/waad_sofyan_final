CREATE TABLE financial_outbox_events (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    calculation_version INTEGER NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP NULL,
    delivery_attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NULL,
    last_error TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_financial_outbox_event_id UNIQUE (event_id),
    CONSTRAINT ux_financial_outbox_claim_cycle
        UNIQUE (aggregate_type, aggregate_id, event_type, calculation_version),
    CONSTRAINT chk_financial_outbox_attempts CHECK (delivery_attempts >= 0)
);

CREATE INDEX ix_financial_outbox_pending
    ON financial_outbox_events (next_attempt_at, occurred_at, id)
    WHERE published_at IS NULL;

CREATE OR REPLACE FUNCTION protect_financial_outbox_payload()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.event_id IS DISTINCT FROM OLD.event_id
       OR NEW.aggregate_type IS DISTINCT FROM OLD.aggregate_type
       OR NEW.aggregate_id IS DISTINCT FROM OLD.aggregate_id
       OR NEW.event_type IS DISTINCT FROM OLD.event_type
       OR NEW.calculation_version IS DISTINCT FROM OLD.calculation_version
       OR NEW.payload IS DISTINCT FROM OLD.payload
       OR NEW.occurred_at IS DISTINCT FROM OLD.occurred_at
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'financial_outbox_events business payload is immutable';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_protect_financial_outbox_payload
BEFORE UPDATE ON financial_outbox_events
FOR EACH ROW EXECUTE FUNCTION protect_financial_outbox_payload();

CREATE OR REPLACE FUNCTION prevent_financial_outbox_delete()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'financial_outbox_events is append-only; DELETE is not allowed';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_financial_outbox_delete
BEFORE DELETE ON financial_outbox_events
FOR EACH ROW EXECUTE FUNCTION prevent_financial_outbox_delete();
