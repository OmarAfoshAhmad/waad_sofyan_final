-- Migration to add snapshot columns for limits in claim_lines
ALTER TABLE claim_lines ADD COLUMN IF NOT EXISTS times_limit_snapshot INTEGER;
ALTER TABLE claim_lines ADD COLUMN IF NOT EXISTS amount_limit_snapshot NUMERIC(15, 2);

-- Update visits_visit_type_check to support LEGACY_BACKLOG
ALTER TABLE visits DROP CONSTRAINT IF EXISTS visits_visit_type_check;
ALTER TABLE visits ADD CONSTRAINT visits_visit_type_check CHECK (visit_type IN ('EMERGENCY', 'INPATIENT', 'OUTPATIENT', 'ROUTINE', 'FOLLOW_UP', 'PREVENTIVE', 'SPECIALIZED', 'HOME_CARE', 'TELECONSULTATION', 'DAY_SURGERY', 'LEGACY_BACKLOG'));
