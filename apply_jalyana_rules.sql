-- Benefit Policy Update for Jalyana
UPDATE benefit_policies SET annual_limit = 60000.00, default_coverage_percent = 75, status = 'ACTIVE' WHERE id = 1;
DELETE FROM benefit_policy_rules WHERE benefit_policy_id = 1;

INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, NULL, true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-STAY';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, NULL, true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-PHARMA';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, NULL, true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-ICU';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, NULL, true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-SURGERY';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, NULL, true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-EMERGENCY';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, NULL, true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-AMBULANCE';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, 10000, true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-PHYSIO';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, 25000, true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-WORK-INJ';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, 1500, true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-RAD';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, 3000, true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-LAB';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, 3000, true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-PSYCH';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active, created_at, requires_pre_approval) 
SELECT 1, id, 100, NULL, true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-ONCOLOGY';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, NULL, true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-DIALYSIS';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, 4000, true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-MATERNITY';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, 1500, true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-SUPPLIES';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, NULL, true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-DENT-REG';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, NULL, true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-DENT-REG';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active, created_at, requires_pre_approval) 
SELECT 1, id, 50, NULL, true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-DENT-COS';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, 500, true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'SUB-VISION';
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, 3000, true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'ROOT-OP'
AND NOT EXISTS (SELECT 1 FROM benefit_policy_rules bpr2 JOIN medical_categories mc2 ON bpr2.medical_category_id = mc2.id 
                WHERE bpr2.benefit_policy_id = 1 AND mc2.code = 'ROOT-OP');
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, NULL, true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'ROOT-IP'
AND NOT EXISTS (SELECT 1 FROM benefit_policy_rules bpr2 JOIN medical_categories mc2 ON bpr2.medical_category_id = mc2.id 
                WHERE bpr2.benefit_policy_id = 1 AND mc2.code = 'ROOT-IP');
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, 4000, true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'ROOT-MAT'
AND NOT EXISTS (SELECT 1 FROM benefit_policy_rules bpr2 JOIN medical_categories mc2 ON bpr2.medical_category_id = mc2.id 
                WHERE bpr2.benefit_policy_id = 1 AND mc2.code = 'ROOT-MAT');
INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active, created_at, requires_pre_approval) 
SELECT 1, id, 75, NULL, true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = 'ROOT-DENT'
AND NOT EXISTS (SELECT 1 FROM benefit_policy_rules bpr2 JOIN medical_categories mc2 ON bpr2.medical_category_id = mc2.id 
                WHERE bpr2.benefit_policy_id = 1 AND mc2.code = 'ROOT-DENT');