-- A member imported after care was delivered was already enrolled in the
-- employer's policy; the import timestamp is not an eligibility start date.
-- Repair only the unambiguous first/only inferred assignment. Any member with
-- financial activity or a real assignment transition is deliberately skipped.

CREATE TEMP TABLE late_import_assignment_repair ON COMMIT DROP AS
SELECT m.id AS member_id,
       m.employer_id,
       m.benefit_policy_id AS policy_id,
       bp.start_date AS effective_from
FROM members m
JOIN benefit_policies bp
  ON bp.id = m.benefit_policy_id
 AND bp.employer_id = m.employer_id
WHERE bp.start_date IS NOT NULL
  AND (SELECT count(*) FROM member_employer_assignments ea WHERE ea.member_id = m.id) = 1
  AND (SELECT count(*) FROM member_policy_assignments pa WHERE pa.member_id = m.id) = 1
  AND EXISTS (
      SELECT 1 FROM member_employer_assignments ea
      WHERE ea.member_id = m.id
        AND ea.employer_id = m.employer_id
        AND ea.assignment_end_date IS NULL
        AND ea.assignment_source IN ('IMPORT', 'BACKFILL')
        AND ea.assignment_start_date > bp.start_date)
  AND EXISTS (
      SELECT 1 FROM member_policy_assignments pa
      WHERE pa.member_id = m.id
        AND pa.policy_id = m.benefit_policy_id
        AND pa.assignment_end_date IS NULL
        AND pa.assignment_source IN ('IMPORT', 'BACKFILL')
        AND pa.assignment_start_date > bp.start_date)
  AND NOT EXISTS (SELECT 1 FROM claims c WHERE c.member_id = m.id)
  AND NOT EXISTS (SELECT 1 FROM pre_authorizations p WHERE p.member_id = m.id)
  AND NOT EXISTS (SELECT 1 FROM benefit_bucket_consumptions bc WHERE bc.member_id = m.id);

-- These guards protect immutable history during normal operation. This
-- one-time correction is permitted only because the rows above are inferred,
-- unique, and have never participated in a financial decision.
ALTER TABLE member_employer_assignments DISABLE TRIGGER trg_member_employer_assignment_update_guard;
ALTER TABLE member_policy_assignments DISABLE TRIGGER trg_member_policy_assignment_update_guard;

UPDATE member_employer_assignments ea
SET assignment_start_date = r.effective_from,
    assignment_reason = ea.assignment_reason || ' | تصحيح V216: العضوية سابقة لتاريخ إدخال السجل'
FROM late_import_assignment_repair r
WHERE ea.member_id = r.member_id
  AND ea.employer_id = r.employer_id;

UPDATE member_policy_assignments pa
SET assignment_start_date = r.effective_from,
    assignment_reason = concat_ws(' | ', pa.assignment_reason,
        'تصحيح V216: صلاحية العضوية تبدأ مع الوثيقة لا مع تاريخ إدخال السجل')
FROM late_import_assignment_repair r
WHERE pa.member_id = r.member_id
  AND pa.policy_id = r.policy_id;

ALTER TABLE member_employer_assignments ENABLE TRIGGER trg_member_employer_assignment_update_guard;
ALTER TABLE member_policy_assignments ENABLE TRIGGER trg_member_policy_assignment_update_guard;

COMMENT ON TABLE member_employer_assignments IS
    'Dated employer ownership. V216 corrected only single inferred late-import rows with no financial history.';
