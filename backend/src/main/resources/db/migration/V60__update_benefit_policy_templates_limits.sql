-- =================================================================================
-- V60: Update Benefit Policy Templates System Limits based on Al-Waha Excel
-- =================================================================================

DO $$
DECLARE
    tpl_id BIGINT;
BEGIN
    SELECT id INTO tpl_id FROM benefit_policy_templates WHERE name = 'القالب القياسي' LIMIT 1;
    
    IF tpl_id IS NOT NULL THEN
        DELETE FROM benefit_policy_template_rules WHERE template_id = tpl_id;

        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, 1, 500.0 FROM medical_categories WHERE code = 'CAT-OPT' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, NULL, NULL FROM medical_categories WHERE code = 'CAT-IP' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, NULL, NULL FROM medical_categories WHERE code = 'CAT-OP' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 75, NULL, NULL FROM medical_categories WHERE code = 'CAT-Go' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, NULL, NULL FROM medical_categories WHERE code = 'CAT001' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, NULL, NULL FROM medical_categories WHERE code = 'CAT002' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, NULL, NULL FROM medical_categories WHERE code = 'CAT003' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, NULL, NULL FROM medical_categories WHERE code = 'CAT004' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, NULL, NULL FROM medical_categories WHERE code = 'CAT005' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, NULL, NULL FROM medical_categories WHERE code = 'CAT006' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, NULL, NULL FROM medical_categories WHERE code = 'CAT007' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, NULL, 10000.0 FROM medical_categories WHERE code = 'CAT008' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, NULL, NULL FROM medical_categories WHERE code = 'CAT009' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, NULL, NULL FROM medical_categories WHERE code = 'CAT010' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, NULL, NULL FROM medical_categories WHERE code = 'CAT011' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, NULL, NULL FROM medical_categories WHERE code = 'CAT012' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, NULL, NULL FROM medical_categories WHERE code = 'CAT013' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, NULL, 3000.0 FROM medical_categories WHERE code = 'CAT014' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, NULL, NULL FROM medical_categories WHERE code = 'CAT015' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, NULL, NULL FROM medical_categories WHERE code = 'CAT016' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, NULL, NULL FROM medical_categories WHERE code = 'CAT017' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, NULL, NULL FROM medical_categories WHERE code = 'CAT018' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, NULL, NULL FROM medical_categories WHERE code = 'CAT019' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, NULL, NULL FROM medical_categories WHERE code = 'CAT020' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, NULL, 5000.0 FROM medical_categories WHERE code = 'CAT021' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, NULL, 1500.0 FROM medical_categories WHERE code = 'CAT022' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, NULL, 3000.0 FROM medical_categories WHERE code = 'CAT023' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 75, NULL, NULL FROM medical_categories WHERE code = 'CAT024' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, NULL, 4000.0 FROM medical_categories WHERE code = 'CAT025' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, NULL, 1500.0 FROM medical_categories WHERE code = 'CAT026' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, 40, NULL FROM medical_categories WHERE code = 'CAT027' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, NULL, 3000.0 FROM medical_categories WHERE code = 'CAT028' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 75, NULL, NULL FROM medical_categories WHERE code = 'CAT029' ON CONFLICT DO NOTHING;
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit) SELECT tpl_id, id, 100, 1, 500.0 FROM medical_categories WHERE code = 'CAT030' ON CONFLICT DO NOTHING;

        -- 100% for Inpatient Sub-Categories
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent)
        SELECT tpl_id, id, 100 FROM medical_categories WHERE code LIKE 'SUB-INPAT-%' ON CONFLICT DO NOTHING;
    END IF;
END $$;