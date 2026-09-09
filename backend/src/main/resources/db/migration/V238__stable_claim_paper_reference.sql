-- Stable, paper-facing claim reference. This must not be recalculated from the
-- current active row order because deleted/cancelled claims would renumber
-- references already written on paper.
ALTER TABLE claims
    ADD COLUMN IF NOT EXISTS paper_reference VARCHAR(140);

WITH ordered AS (
    SELECT
        c.id,
        cb.batch_code || '/' || LPAD(
            ROW_NUMBER() OVER (
                PARTITION BY c.claim_batch_id
                ORDER BY c.created_at ASC NULLS LAST, c.id ASC
            )::text,
            4,
            '0'
        ) AS generated_reference
    FROM claims c
    JOIN claim_batches cb ON cb.id = c.claim_batch_id
    WHERE c.paper_reference IS NULL
      AND cb.batch_code IS NOT NULL
)
UPDATE claims c
SET paper_reference = ordered.generated_reference
FROM ordered
WHERE c.id = ordered.id;

CREATE UNIQUE INDEX IF NOT EXISTS ux_claims_paper_reference
    ON claims(paper_reference)
    WHERE paper_reference IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_claims_batch_paper_reference
    ON claims(claim_batch_id, paper_reference);
