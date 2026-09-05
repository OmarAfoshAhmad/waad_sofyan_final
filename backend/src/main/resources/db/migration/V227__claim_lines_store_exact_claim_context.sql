-- Claim-line audit snapshots must store the exact claim context code applied
-- during financial adjudication, not only the base encounter type.
--
-- Examples:
--   INPATIENT
--   MATERNITY
--   PREGNANCY_COMPLICATIONS
--
-- The previous VARCHAR(20) was enough for INPATIENT/OUTPATIENT but truncates
-- or rejects longer dynamic claim contexts.
ALTER TABLE claim_lines
    ALTER COLUMN applied_context TYPE VARCHAR(60);

COMMENT ON COLUMN claim_lines.applied_context IS
    'Exact claim_context_code used when adjudicating this line; not merely encounter_type.';
