-- =============================================================================
-- V223: Pregnancy complications are a claim-wide decision context, not a
-- medical-service category.
--
-- Real production pricing items for maternity operations can legitimately be
-- classified as generic inpatient services (CAT-COV-INPATIENT). When a reviewer
-- selects the claim context PREGNANCY_COMPLICATIONS, the coverage engine must
-- still find rules for those ordinary service categories inside that exact
-- claim context. The service classification must not be rewritten to
-- CAT-MAT-COMP; CAT-MAT-COMP is kept only as the existing anchor that tells us
-- which policies intentionally define a pregnancy-complications ceiling.
--
-- Conservative data rule:
--   * Only policies that already have an active PREGNANCY_COMPLICATIONS rule
--     for CAT-MAT-COMP are touched.
--   * The copied rules come from the same policy's active MATERNITY rules.
--   * Copied rules are linked to the existing PREGNANCY_COMPLICATIONS bucket(s),
--     so the claim uses the complications ceiling, not the maternity ceiling.
--   * Existing PREGNANCY_COMPLICATIONS rules are never overwritten.
-- =============================================================================

WITH pregnancy_anchor AS (
    SELECT
        r.benefit_policy_id,
        r.id AS anchor_rule_id
    FROM benefit_policy_rules r
    JOIN medical_categories c ON c.id = r.medical_category_id
    WHERE r.active = true
      AND r.deleted = false
      AND r.claim_context_code = 'PREGNANCY_COMPLICATIONS'
      AND c.code = 'CAT-MAT-COMP'
),
pregnancy_buckets AS (
    SELECT
        a.benefit_policy_id,
        brb.bucket_id,
        brb.consumption_order,
        brb.consumption_mode,
        brb.mandatory
    FROM pregnancy_anchor a
    JOIN benefit_rule_buckets brb ON brb.rule_id = a.anchor_rule_id
    JOIN benefit_limit_buckets b ON b.id = brb.bucket_id
    WHERE b.active = true
),
maternity_source_rules AS (
    SELECT
        r.*
    FROM benefit_policy_rules r
    JOIN pregnancy_anchor a ON a.benefit_policy_id = r.benefit_policy_id
    WHERE r.active = true
      AND r.deleted = false
      AND r.claim_context_code = 'MATERNITY'
      AND r.medical_category_id IS NOT NULL
      AND EXISTS (
          SELECT 1
          FROM pregnancy_buckets pb
          WHERE pb.benefit_policy_id = r.benefit_policy_id
      )
      AND NOT EXISTS (
          SELECT 1
          FROM benefit_policy_rules existing
          WHERE existing.benefit_policy_id = r.benefit_policy_id
            AND existing.medical_category_id = r.medical_category_id
            AND existing.claim_context_code = 'PREGNANCY_COMPLICATIONS'
            AND existing.deleted = false
      )
),
inserted_rules AS (
    INSERT INTO benefit_policy_rules (
        benefit_policy_id,
        medical_category_id,
        coverage_percent,
        requires_pre_approval,
        waiting_period_days,
        encounter_type,
        claim_context_code,
        copay_percentage,
        inheritance_enabled,
        priority,
        notes,
        active,
        deleted,
        created_at,
        updated_at,
        created_by,
        version
    )
    SELECT
        src.benefit_policy_id,
        src.medical_category_id,
        src.coverage_percent,
        src.requires_pre_approval,
        src.waiting_period_days,
        src.encounter_type,
        'PREGNANCY_COMPLICATIONS',
        src.copay_percentage,
        src.inheritance_enabled,
        src.priority,
        LEFT(
            COALESCE(NULLIF(src.notes, ''), '') ||
            CASE WHEN COALESCE(NULLIF(src.notes, ''), '') = '' THEN '' ELSE ' | ' END ||
            'Auto-created by V223 from MATERNITY rule #' || src.id ||
            ' so pregnancy complications can cover ordinary service categories under the complications ceiling.',
            500
        ),
        src.active,
        false,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        src.created_by,
        0
    FROM maternity_source_rules src
    RETURNING id, benefit_policy_id, medical_category_id
)
INSERT INTO benefit_rule_buckets (
    rule_id,
    bucket_id,
    consumption_order,
    consumption_mode,
    mandatory,
    created_at
)
SELECT
    ir.id,
    pb.bucket_id,
    pb.consumption_order,
    pb.consumption_mode,
    pb.mandatory,
    CURRENT_TIMESTAMP
FROM inserted_rules ir
JOIN pregnancy_buckets pb ON pb.benefit_policy_id = ir.benefit_policy_id
ON CONFLICT DO NOTHING;

DO $$
DECLARE
    created_count INTEGER;
BEGIN
    SELECT COUNT(*)
    INTO created_count
    FROM benefit_policy_rules
    WHERE claim_context_code = 'PREGNANCY_COMPLICATIONS'
      AND notes LIKE '%Auto-created by V223 from MATERNITY rule #%';

    RAISE NOTICE 'V223: pregnancy-complications mirrored maternity category rules present: %', created_count;
END $$;
