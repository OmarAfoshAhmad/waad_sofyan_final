-- Preserve historical rows but allow only one canonical session for the same
-- provider/file/content fingerprint from this release forward.
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY source_fingerprint
               ORDER BY (posted_count > 0) DESC, updated_at DESC, id DESC
           ) AS position
    FROM price_list_classification_sessions
    WHERE source_fingerprint IS NOT NULL
)
UPDATE price_list_classification_sessions session
SET source_fingerprint = NULL,
    notes = LEFT(CONCAT_WS(E'\n', NULLIF(session.notes, ''),
        'Duplicate historical session fingerprint cleared by V165; row retained for audit.'), 2000)
FROM ranked
WHERE session.id = ranked.id AND ranked.position > 1;

DROP INDEX IF EXISTS idx_price_list_session_fingerprint;
CREATE UNIQUE INDEX ux_price_list_session_fingerprint
    ON price_list_classification_sessions(source_fingerprint)
    WHERE source_fingerprint IS NOT NULL;
