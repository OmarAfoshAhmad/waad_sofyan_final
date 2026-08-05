-- =================================================================================
-- V131: Re-seed the standard benefit policy template's rules.
--
-- V84 (full classification cutover) deleted every medical_categories row and,
-- with it, every benefit_policy_template_rules row via ON DELETE CASCADE. No
-- migration since has re-populated the "القالب القياسي" template's rules for
-- the current category set, so the template exists but is empty: applying it
-- to a new policy creates zero rules, and BenefitPolicyService.activate()
-- then refuses to activate that policy ("no active coverage rule").
--
-- This re-seeds one rule per active, non-deleted medical category at the
-- standard 75% coverage rate (the rate V58 used for the majority of
-- categories), so the default template is usable again for its intended
-- "quick setup" purpose. Per-category overrides remain editable afterwards
-- through the normal template-rule management screens.
-- =================================================================================

DO $$
DECLARE
    tpl_id BIGINT;
BEGIN
    SELECT id INTO tpl_id FROM benefit_policy_templates WHERE name = 'القالب القياسي' LIMIT 1;

    IF tpl_id IS NOT NULL THEN
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, active)
        SELECT tpl_id, mc.id, 75, true
        FROM medical_categories mc
        WHERE mc.deleted = false AND mc.active = true
        ON CONFLICT (template_id, medical_category_id) DO NOTHING;
    END IF;
END $$;
