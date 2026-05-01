-- =================================================================================
-- V58: Create Benefit Policy Templates System
-- Description: Adds tables for managing reusable policy templates and their rules.
--              Includes seeding of the "Standard Template" with 33 categories.
-- =================================================================================

-- 1. Create Policy Templates Table
CREATE TABLE IF NOT EXISTS benefit_policy_templates (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    is_default  BOOLEAN DEFAULT false,
    active      BOOLEAN DEFAULT true,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Create Template Rules Table
CREATE TABLE IF NOT EXISTS benefit_policy_template_rules (
    id                    BIGSERIAL PRIMARY KEY,
    template_id           BIGINT NOT NULL,
    medical_category_id   BIGINT NOT NULL,
    
    coverage_percent      INTEGER,
    times_limit           INTEGER,
    amount_limit          NUMERIC(15,2),
    requires_pre_approval BOOLEAN DEFAULT false,
    
    active                BOOLEAN DEFAULT true,
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_template_rule_parent FOREIGN KEY (template_id) 
        REFERENCES benefit_policy_templates(id) ON DELETE CASCADE,
    CONSTRAINT fk_template_rule_category FOREIGN KEY (medical_category_id) 
        REFERENCES medical_categories(id) ON DELETE CASCADE,
    CONSTRAINT uk_template_category UNIQUE (template_id, medical_category_id)
);

-- 3. Seed the "Standard Template" (القالب القياسي)
INSERT INTO benefit_policy_templates (name, description, is_default)
VALUES ('القالب القياسي', 'القالب المعتمد مع نسب التغطية والأسقف المالية القياسية (33 تصنيف)', true)
ON CONFLICT DO NOTHING;

-- 4. Seed Rules for the Standard Template based on User Reference
DO $$
DECLARE
    tpl_id BIGINT;
BEGIN
    SELECT id INTO tpl_id FROM benefit_policy_templates WHERE name = 'القالب القياسي' LIMIT 1;
    
    IF tpl_id IS NOT NULL THEN
        -- Rules extracted from user screenshots
        -- CAT031: 50%
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent)
        SELECT tpl_id, id, 50 FROM medical_categories WHERE code = 'CAT031' ON CONFLICT DO NOTHING;

        -- CAT028: 75%
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent)
        SELECT tpl_id, id, 75 FROM medical_categories WHERE code = 'CAT028' ON CONFLICT DO NOTHING;

        -- CAT027: 75%, 20 times
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit)
        SELECT tpl_id, id, 75, 20 FROM medical_categories WHERE code = 'CAT027' ON CONFLICT DO NOTHING;

        -- CAT026: 75%, 1 time, 1500 limit
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, times_limit, amount_limit)
        SELECT tpl_id, id, 75, 1, 1500.00 FROM medical_categories WHERE code = 'CAT026' ON CONFLICT DO NOTHING;

        -- CAT025: 75%, 15000 limit
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, amount_limit)
        SELECT tpl_id, id, 75, 15000.00 FROM medical_categories WHERE code = 'CAT025' ON CONFLICT DO NOTHING;

        -- CAT024: 75%, 1500 limit
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, amount_limit)
        SELECT tpl_id, id, 75, 1500.00 FROM medical_categories WHERE code = 'CAT024' ON CONFLICT DO NOTHING;

        -- CAT023: 75%, 3000 limit
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, amount_limit)
        SELECT tpl_id, id, 75, 3000.00 FROM medical_categories WHERE code = 'CAT023' ON CONFLICT DO NOTHING;

        -- CAT022: 75%, 1500 limit
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, amount_limit)
        SELECT tpl_id, id, 75, 1500.00 FROM medical_categories WHERE code = 'CAT022' ON CONFLICT DO NOTHING;

        -- CAT021: 75%, 4000 limit
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, amount_limit)
        SELECT tpl_id, id, 75, 4000.00 FROM medical_categories WHERE code = 'CAT021' ON CONFLICT DO NOTHING;

        -- CAT009: 75%, 10000 limit
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, amount_limit)
        SELECT tpl_id, id, 75, 10000.00 FROM medical_categories WHERE code = 'CAT009' ON CONFLICT DO NOTHING;

        -- CAT008: 75%, 1000 limit
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent, amount_limit)
        SELECT tpl_id, id, 75, 1000.00 FROM medical_categories WHERE code = 'CAT008' ON CONFLICT DO NOTHING;

        -- Default 75% for common outpatient
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent)
        SELECT tpl_id, id, 75 FROM medical_categories 
        WHERE code IN ('CAT029', 'CAT020', 'CAT019', 'CAT018', 'CAT017', 'CAT016', 'CAT015', 'CAT014', 'CAT013', 'CAT012', 'CAT011', 'CAT010', 'CAT007')
        ON CONFLICT DO NOTHING;

        -- 100% for Inpatient Sub-Categories
        INSERT INTO benefit_policy_template_rules (template_id, medical_category_id, coverage_percent)
        SELECT tpl_id, id, 100 FROM medical_categories WHERE code LIKE 'SUB-INPAT-%'
        ON CONFLICT DO NOTHING;
    END IF;
END $$;
