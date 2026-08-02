ALTER TABLE price_list_classification_sessions
    ADD COLUMN IF NOT EXISTS source_fingerprint VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_price_list_session_fingerprint
    ON price_list_classification_sessions(source_fingerprint);
