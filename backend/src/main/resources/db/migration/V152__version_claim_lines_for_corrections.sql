-- Claim-line revisions are immutable across approved financial cycles. A
-- correction supersedes the old row and inserts a new current row, preserving
-- every historical snapshot FK and its original service identity.
ALTER TABLE claim_lines
    ADD COLUMN current_line BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN superseded_at TIMESTAMP,
    ADD COLUMN superseded_by_calculation_version INTEGER;

ALTER TABLE claim_lines
    ADD CONSTRAINT chk_claim_line_supersession
    CHECK (
        (current_line = TRUE AND superseded_at IS NULL AND superseded_by_calculation_version IS NULL)
        OR
        (current_line = FALSE AND superseded_at IS NOT NULL AND superseded_by_calculation_version IS NOT NULL)
    );

CREATE INDEX idx_claim_lines_current_by_claim
    ON claim_lines (claim_id, id)
    WHERE current_line = TRUE;
