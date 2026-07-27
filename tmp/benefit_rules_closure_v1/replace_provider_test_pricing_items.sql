BEGIN;
DELETE FROM provider_contract_pricing_items WHERE contract_id = 3101;

INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار الإسعاف المحلي - EMERGENCY - وثيقة جليانة', 'TST-POL001-001-CAT-AMBULANCE', 'الإسعاف المحلي', 1, mc.id,
  1000, 1000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: EMERGENCY | وثيقة جليانة | الشركة 750.00 / المشترك 250.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'الإسعاف المحلي', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-AMBULANCE', 'الإسعاف المحلي', 1000
FROM medical_categories mc
WHERE mc.code = 'CAT-AMBULANCE';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار نفقات التخدير - INPATIENT - وثيقة جليانة', 'TST-POL001-002-CAT-ANESTHESIA', 'نفقات التخدير', 1, mc.id,
  1000, 1000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | وثيقة جليانة | الشركة 750.00 / المشترك 250.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'نفقات التخدير', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-ANESTHESIA', 'نفقات التخدير', 1000
FROM medical_categories mc
WHERE mc.code = 'CAT-ANESTHESIA';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار عناية القلب - INPATIENT - وثيقة جليانة', 'TST-POL001-003-CAT-CCU', 'عناية القلب', 1, mc.id,
  1000, 1000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | وثيقة جليانة | الشركة 750.00 / المشترك 250.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'عناية القلب', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-CCU', 'عناية القلب', 1000
FROM medical_categories mc
WHERE mc.code = 'CAT-CCU';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار العلاج والرعاية اليومية - INPATIENT - وثيقة جليانة', 'TST-POL001-004-CAT-DAY-CARE', 'العلاج والرعاية اليومية', 1, mc.id,
  1000, 1000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | وثيقة جليانة | الشركة 750.00 / المشترك 250.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'العلاج والرعاية اليومية', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-DAY-CARE', 'العلاج والرعاية اليومية', 1000
FROM medical_categories mc
WHERE mc.code = 'CAT-DAY-CARE';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار علاج الأسنان الطارئ للمريض داخل المستشفى - INPATIENT - وثيقة جليانة', 'TST-POL001-005-CAT-DENT-EMERG', 'علاج الأسنان الطارئ للمريض داخل المستشفى', 1, mc.id,
  1000, 1000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | وثيقة جليانة | الشركة 750.00 / المشترك 250.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'علاج الأسنان الطارئ للمريض داخل المستشفى', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-DENT-EMERG', 'علاج الأسنان الطارئ للمريض داخل المستشفى', 1000
FROM medical_categories mc
WHERE mc.code = 'CAT-DENT-EMERG';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار زراعة الأسنان - OUTPATIENT - وثيقة جليانة', 'TST-POL001-006-CAT-DENT-IMPLANT', 'زراعة الأسنان', 1, mc.id,
  1000, 1000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | وثيقة جليانة | الشركة 500.00 / المشترك 500.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'زراعة الأسنان', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-DENT-IMPLANT', 'زراعة الأسنان', 1000
FROM medical_categories mc
WHERE mc.code = 'CAT-DENT-IMPLANT';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار تقويم الأسنان - OUTPATIENT - وثيقة جليانة', 'TST-POL001-007-CAT-DENT-ORTHO', 'تقويم الأسنان', 1, mc.id,
  1000, 1000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | وثيقة جليانة | الشركة 500.00 / المشترك 500.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'تقويم الأسنان', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-DENT-ORTHO', 'تقويم الأسنان', 1000
FROM medical_categories mc
WHERE mc.code = 'CAT-DENT-ORTHO';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار تركيبات الأسنان - OUTPATIENT - وثيقة جليانة', 'TST-POL001-008-CAT-DENT-PROSTHO', 'تركيبات الأسنان', 1, mc.id,
  1000, 1000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | وثيقة جليانة | الشركة 500.00 / المشترك 500.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'تركيبات الأسنان', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-DENT-PROSTHO', 'تركيبات الأسنان', 1000
FROM medical_categories mc
WHERE mc.code = 'CAT-DENT-PROSTHO';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار علاج الأسنان الروتيني - OUTPATIENT - وثيقة جليانة', 'TST-POL001-009-CAT-DENT-ROUTINE', 'علاج الأسنان الروتيني', 1, mc.id,
  1000, 1000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | وثيقة جليانة | الشركة 750.00 / المشترك 250.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'علاج الأسنان الروتيني', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-DENT-ROUTINE', 'علاج الأسنان الروتيني', 1000
FROM medical_categories mc
WHERE mc.code = 'CAT-DENT-ROUTINE';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار الكشوفات الطبية - INPATIENT - وثيقة جليانة', 'TST-POL001-010-CAT-DIAGNOSTIC', 'الكشوفات الطبية', 1, mc.id,
  1000, 1000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | وثيقة جليانة | الشركة 750.00 / المشترك 250.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'الكشوفات الطبية', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-DIAGNOSTIC', 'الكشوفات الطبية', 1000
FROM medical_categories mc
WHERE mc.code = 'CAT-DIAGNOSTIC';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار الغسيل الكلوي - INPATIENT - وثيقة جليانة', 'TST-POL001-011-CAT-DIALYSIS', 'الغسيل الكلوي', 1, mc.id,
  1000, 1000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | وثيقة جليانة | الشركة 750.00 / المشترك 250.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'الغسيل الكلوي', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-DIALYSIS', 'الغسيل الكلوي', 1000
FROM medical_categories mc
WHERE mc.code = 'CAT-DIALYSIS';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار الأجهزة والمعدات الطبية وفق تقرير الطبيب المختص - OUTPATIENT - وثيقة جليانة', 'TST-POL001-012-CAT-DME', 'الأجهزة والمعدات الطبية وفق تقرير الطبيب المختص', 1, mc.id,
  750, 750, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | وثيقة جليانة | الشركة 562.50 / المشترك 187.50 | اختبار سقف مالي', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'الأجهزة والمعدات الطبية وفق تقرير الطبيب المختص', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-DME', 'الأجهزة والمعدات الطبية وفق تقرير الطبيب المختص', 750
FROM medical_categories mc
WHERE mc.code = 'CAT-DME';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار أدوية الصرف العام - INPATIENT - وثيقة جليانة', 'TST-POL001-013-CAT-DRUG-GENERAL', 'أدوية الصرف العام', 1, mc.id,
  1000, 1000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | وثيقة جليانة | الشركة 750.00 / المشترك 250.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'أدوية الصرف العام', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-DRUG-GENERAL', 'أدوية الصرف العام', 1000
FROM medical_categories mc
WHERE mc.code = 'CAT-DRUG-GENERAL';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار أدوية الصرف العام - OUTPATIENT - وثيقة جليانة', 'TST-POL001-014-CAT-DRUG-GENERAL', 'أدوية الصرف العام', 1, mc.id,
  1500, 1500, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | وثيقة جليانة | الشركة 1125.00 / المشترك 375.00 | اختبار سقف مالي', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'أدوية الصرف العام', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-DRUG-GENERAL', 'أدوية الصرف العام', 1500
FROM medical_categories mc
WHERE mc.code = 'CAT-DRUG-GENERAL';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار تكلفة مرافق واحد للشخص الذي تم إخلاؤه طبياً - INPATIENT - وثيقة جليانة', 'TST-POL001-015-CAT-EVAC-COMPANION', 'تكلفة مرافق واحد للشخص الذي تم إخلاؤه طبياً', 1, mc.id,
  1000, 1000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | وثيقة جليانة | الشركة 750.00 / المشترك 250.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'تكلفة مرافق واحد للشخص الذي تم إخلاؤه طبياً', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-EVAC-COMPANION', 'تكلفة مرافق واحد للشخص الذي تم إخلاؤه طبياً', 1000
FROM medical_categories mc
WHERE mc.code = 'CAT-EVAC-COMPANION';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار كشوفات العيون - OUTPATIENT - وثيقة جليانة', 'TST-POL001-016-CAT-EYE-EXAM', 'كشوفات العيون', 1, mc.id,
  500, 500, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | وثيقة جليانة | الشركة 375.00 / المشترك 125.00 | اختبار سقف مالي', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'كشوفات العيون', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-EYE-EXAM', 'كشوفات العيون', 500
FROM medical_categories mc
WHERE mc.code = 'CAT-EYE-EXAM';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار سفر أحد أفراد العائلة في حالة الإخلاء الطبي - INPATIENT - وثيقة جليانة', 'TST-POL001-017-CAT-FAMILY-TRAVEL', 'سفر أحد أفراد العائلة في حالة الإخلاء الطبي', 1, mc.id,
  1000, 1000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | وثيقة جليانة | الشركة 750.00 / المشترك 250.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'سفر أحد أفراد العائلة في حالة الإخلاء الطبي', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-FAMILY-TRAVEL', 'سفر أحد أفراد العائلة في حالة الإخلاء الطبي', 1000
FROM medical_categories mc
WHERE mc.code = 'CAT-FAMILY-TRAVEL';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار التمريض المنزلي أو رعاية النقاهة بعد الخروج - SPECIAL - وثيقة جليانة', 'TST-POL001-018-CAT-HOME-NURSING', 'التمريض المنزلي أو رعاية النقاهة بعد الخروج', 1, mc.id,
  500, 500, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: SPECIAL | وثيقة جليانة | الشركة 375.00 / المشترك 125.00 | اختبار سقف مالي', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'التمريض المنزلي أو رعاية النقاهة بعد الخروج', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-HOME-NURSING', 'التمريض المنزلي أو رعاية النقاهة بعد الخروج', 500
FROM medical_categories mc
WHERE mc.code = 'CAT-HOME-NURSING';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار العناية الفائقة - INPATIENT - وثيقة جليانة', 'TST-POL001-019-CAT-ICU', 'العناية الفائقة', 1, mc.id,
  1000, 1000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | وثيقة جليانة | الشركة 750.00 / المشترك 250.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'العناية الفائقة', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-ICU', 'العناية الفائقة', 1000
FROM medical_categories mc
WHERE mc.code = 'CAT-ICU';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار التصوير بالرنين المغناطيسي والمقطعي والطبقي - INPATIENT - وثيقة جليانة', 'TST-POL001-020-CAT-IMG-ADV', 'التصوير بالرنين المغناطيسي والمقطعي والطبقي', 1, mc.id,
  1000, 1000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | وثيقة جليانة | الشركة 750.00 / المشترك 250.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'التصوير بالرنين المغناطيسي والمقطعي والطبقي', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-IMG-ADV', 'التصوير بالرنين المغناطيسي والمقطعي والطبقي', 1000
FROM medical_categories mc
WHERE mc.code = 'CAT-IMG-ADV';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار التصوير بالرنين المغناطيسي والمقطعي والطبقي - OUTPATIENT - وثيقة جليانة', 'TST-POL001-021-CAT-IMG-ADV', 'التصوير بالرنين المغناطيسي والمقطعي والطبقي', 1, mc.id,
  750, 750, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | وثيقة جليانة | الشركة 562.50 / المشترك 187.50 | اختبار سقف مالي', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'التصوير بالرنين المغناطيسي والمقطعي والطبقي', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-IMG-ADV', 'التصوير بالرنين المغناطيسي والمقطعي والطبقي', 750
FROM medical_categories mc
WHERE mc.code = 'CAT-IMG-ADV';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار الأشعة والصور التشخيصية - INPATIENT - وثيقة جليانة', 'TST-POL001-022-CAT-IMG-DIAG', 'الأشعة والصور التشخيصية', 1, mc.id,
  1000, 1000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | وثيقة جليانة | الشركة 750.00 / المشترك 250.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'الأشعة والصور التشخيصية', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-IMG-DIAG', 'الأشعة والصور التشخيصية', 1000
FROM medical_categories mc
WHERE mc.code = 'CAT-IMG-DIAG';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار الأشعة والصور التشخيصية - OUTPATIENT - وثيقة جليانة', 'TST-POL001-023-CAT-IMG-DIAG', 'الأشعة والصور التشخيصية', 1, mc.id,
  1500, 1500, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | وثيقة جليانة | الشركة 1125.00 / المشترك 375.00 | اختبار سقف مالي', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'الأشعة والصور التشخيصية', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-IMG-DIAG', 'الأشعة والصور التشخيصية', 1500
FROM medical_categories mc
WHERE mc.code = 'CAT-IMG-DIAG';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار التحاليل الطبية والمختبرات - INPATIENT - وثيقة جليانة', 'TST-POL001-024-CAT-LAB', 'التحاليل الطبية والمختبرات', 1, mc.id,
  1000, 1000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | وثيقة جليانة | الشركة 750.00 / المشترك 250.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'التحاليل الطبية والمختبرات', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-LAB', 'التحاليل الطبية والمختبرات', 1000
FROM medical_categories mc
WHERE mc.code = 'CAT-LAB';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار التحاليل الطبية والمختبرات - OUTPATIENT - وثيقة جليانة', 'TST-POL001-025-CAT-LAB', 'التحاليل الطبية والمختبرات', 1, mc.id,
  1500, 1500, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | وثيقة جليانة | الشركة 1125.00 / المشترك 375.00 | اختبار سقف مالي', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'التحاليل الطبية والمختبرات', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-LAB', 'التحاليل الطبية والمختبرات', 1500
FROM medical_categories mc
WHERE mc.code = 'CAT-LAB';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار مضاعفات الحمل والولادة - INPATIENT - وثيقة جليانة', 'TST-POL001-026-CAT-MAT-COMP', 'مضاعفات الحمل والولادة', 1, mc.id,
  750, 750, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | وثيقة جليانة | الشركة 562.50 / المشترك 187.50 | اختبار سقف مالي', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'مضاعفات الحمل والولادة', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-MAT-COMP', 'مضاعفات الحمل والولادة', 750
FROM medical_categories mc
WHERE mc.code = 'CAT-MAT-COMP';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار الولادة القيصرية - INPATIENT - وثيقة جليانة', 'TST-POL001-027-CAT-MAT-CS', 'الولادة القيصرية', 1, mc.id,
  2000, 2000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | وثيقة جليانة | الشركة 1500.00 / المشترك 500.00 | اختبار سقف مالي', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'الولادة القيصرية', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-MAT-CS', 'الولادة القيصرية', 2000
FROM medical_categories mc
WHERE mc.code = 'CAT-MAT-CS';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار الولادة الطبيعية - INPATIENT - وثيقة جليانة', 'TST-POL001-028-CAT-MAT-NORMAL', 'الولادة الطبيعية', 1, mc.id,
  2000, 2000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | وثيقة جليانة | الشركة 1500.00 / المشترك 500.00 | اختبار سقف مالي', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'الولادة الطبيعية', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-MAT-NORMAL', 'الولادة الطبيعية', 2000
FROM medical_categories mc
WHERE mc.code = 'CAT-MAT-NORMAL';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار الإخلاء الطبي - INPATIENT - وثيقة جليانة', 'TST-POL001-029-CAT-MED-EVAC', 'الإخلاء الطبي', 1, mc.id,
  1000, 1000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | وثيقة جليانة | الشركة 750.00 / المشترك 250.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'الإخلاء الطبي', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-MED-EVAC', 'الإخلاء الطبي', 1000
FROM medical_categories mc
WHERE mc.code = 'CAT-MED-EVAC';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار المستلزمات الطبية - INPATIENT - وثيقة جليانة', 'TST-POL001-030-CAT-MED-SUP', 'المستلزمات الطبية', 1, mc.id,
  1000, 1000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | وثيقة جليانة | الشركة 750.00 / المشترك 250.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'المستلزمات الطبية', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-MED-SUP', 'المستلزمات الطبية', 1000
FROM medical_categories mc
WHERE mc.code = 'CAT-MED-SUP';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار علاج الأورام - INPATIENT - وثيقة جليانة', 'TST-POL001-031-CAT-ONCOLOGY', 'علاج الأورام', 1, mc.id,
  600, 600, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | وثيقة جليانة | الشركة 600.00 / المشترك 0.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'علاج الأورام', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-ONCOLOGY', 'علاج الأورام', 600
FROM medical_categories mc
WHERE mc.code = 'CAT-ONCOLOGY';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار النظارات الطبية - OUTPATIENT - وثيقة جليانة', 'TST-POL001-032-CAT-OPT', 'النظارات الطبية', 1, mc.id,
  500, 500, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | وثيقة جليانة | الشركة 375.00 / المشترك 125.00 | اختبار سقف مالي', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'النظارات الطبية', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-OPT', 'النظارات الطبية', 500
FROM medical_categories mc
WHERE mc.code = 'CAT-OPT';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار العلاج الطبيعي - INPATIENT - وثيقة جليانة', 'TST-POL001-033-CAT-PHYSIO', 'العلاج الطبيعي', 1, mc.id,
  2000, 2000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | وثيقة جليانة | الشركة 1500.00 / المشترك 500.00 | اختبار سقف مالي', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'العلاج الطبيعي', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-PHYSIO', 'العلاج الطبيعي', 2000
FROM medical_categories mc
WHERE mc.code = 'CAT-PHYSIO';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار العلاج الطبيعي - OUTPATIENT - وثيقة جليانة', 'TST-POL001-034-CAT-PHYSIO', 'العلاج الطبيعي', 1, mc.id,
  1000, 1000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | وثيقة جليانة | الشركة 750.00 / المشترك 250.00 | اختبار عدد مرات', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'العلاج الطبيعي', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-PHYSIO', 'العلاج الطبيعي', 1000
FROM medical_categories mc
WHERE mc.code = 'CAT-PHYSIO';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار رسوم الأطباء والجراحين والمستشارين والممارسين - INPATIENT - وثيقة جليانة', 'TST-POL001-035-CAT-PRACT-FEE', 'رسوم الأطباء والجراحين والمستشارين والممارسين', 1, mc.id,
  1000, 1000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | وثيقة جليانة | الشركة 750.00 / المشترك 250.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'رسوم الأطباء والجراحين والمستشارين والممارسين', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-PRACT-FEE', 'رسوم الأطباء والجراحين والمستشارين والممارسين', 1000
FROM medical_categories mc
WHERE mc.code = 'CAT-PRACT-FEE';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار رسوم الأطباء والجراحين والمستشارين والممارسين - OUTPATIENT - وثيقة جليانة', 'TST-POL001-036-CAT-PRACT-FEE', 'رسوم الأطباء والجراحين والمستشارين والممارسين', 1, mc.id,
  1500, 1500, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | وثيقة جليانة | الشركة 1125.00 / المشترك 375.00 | اختبار سقف مالي', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'رسوم الأطباء والجراحين والمستشارين والممارسين', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-PRACT-FEE', 'رسوم الأطباء والجراحين والمستشارين والممارسين', 1500
FROM medical_categories mc
WHERE mc.code = 'CAT-PRACT-FEE';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار أدوية الطب النفسي - INPATIENT - وثيقة جليانة', 'TST-POL001-037-CAT-PSYCH-DRUG', 'أدوية الطب النفسي', 1, mc.id,
  1500, 1500, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | وثيقة جليانة | الشركة 1125.00 / المشترك 375.00 | اختبار سقف مالي', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'أدوية الطب النفسي', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-PSYCH-DRUG', 'أدوية الطب النفسي', 1500
FROM medical_categories mc
WHERE mc.code = 'CAT-PSYCH-DRUG';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار جلسات الطب النفسي - INPATIENT - وثيقة جليانة', 'TST-POL001-038-CAT-PSYCH-SESS', 'جلسات الطب النفسي', 1, mc.id,
  1500, 1500, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | وثيقة جليانة | الشركة 1125.00 / المشترك 375.00 | اختبار سقف مالي', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'جلسات الطب النفسي', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-PSYCH-SESS', 'جلسات الطب النفسي', 1500
FROM medical_categories mc
WHERE mc.code = 'CAT-PSYCH-SESS';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار جلسات الطب النفسي - OUTPATIENT - وثيقة جليانة', 'TST-POL001-039-CAT-PSYCH-SESS', 'جلسات الطب النفسي', 1, mc.id,
  1500, 1500, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | وثيقة جليانة | الشركة 1125.00 / المشترك 375.00 | اختبار سقف مالي', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'جلسات الطب النفسي', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-PSYCH-SESS', 'جلسات الطب النفسي', 1500
FROM medical_categories mc
WHERE mc.code = 'CAT-PSYCH-SESS';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار الإيواء في غرفة خاصة أو قسم - INPATIENT - وثيقة جليانة', 'TST-POL001-040-CAT-ROOM', 'الإيواء في غرفة خاصة أو قسم', 1, mc.id,
  1000, 1000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | وثيقة جليانة | الشركة 750.00 / المشترك 250.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'الإيواء في غرفة خاصة أو قسم', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-ROOM', 'الإيواء في غرفة خاصة أو قسم', 1000
FROM medical_categories mc
WHERE mc.code = 'CAT-ROOM';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار المعدات والمواد الجراحية - INPATIENT - وثيقة جليانة', 'TST-POL001-041-CAT-SURG-MAT', 'المعدات والمواد الجراحية', 1, mc.id,
  1000, 1000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | وثيقة جليانة | الشركة 750.00 / المشترك 250.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'المعدات والمواد الجراحية', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-SURG-MAT', 'المعدات والمواد الجراحية', 1000
FROM medical_categories mc
WHERE mc.code = 'CAT-SURG-MAT';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار العمليات الجراحية الصغرى للمرضى خارج المستشفى - OUTPATIENT - وثيقة جليانة', 'TST-POL001-042-CAT-SURGERY', 'العمليات الجراحية الصغرى للمرضى خارج المستشفى', 1, mc.id,
  1000, 1000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | وثيقة جليانة | الشركة 750.00 / المشترك 250.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'العمليات الجراحية الصغرى للمرضى خارج المستشفى', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-SURGERY', 'العمليات الجراحية الصغرى للمرضى خارج المستشفى', 1000
FROM medical_categories mc
WHERE mc.code = 'CAT-SURGERY';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار زرع الأعضاء - INPATIENT - وثيقة جليانة', 'TST-POL001-043-CAT-TRANSPLANT', 'زرع الأعضاء', 1, mc.id,
  1000, 1000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | وثيقة جليانة | الشركة 750.00 / المشترك 250.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'زرع الأعضاء', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-TRANSPLANT', 'زرع الأعضاء', 1000
FROM medical_categories mc
WHERE mc.code = 'CAT-TRANSPLANT';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار علاج إصابات العمل - INPATIENT - وثيقة جليانة', 'TST-POL001-044-CAT-WORK-INJURY', 'علاج إصابات العمل', 1, mc.id,
  2000, 2000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | وثيقة جليانة | الشركة 1500.00 / المشترك 500.00 | اختبار سقف مالي', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'علاج إصابات العمل', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-WORK-INJURY', 'علاج إصابات العمل', 2000
FROM medical_categories mc
WHERE mc.code = 'CAT-WORK-INJURY';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار عمليات القلب والشرايين - INPATIENT - مصرف الوحدة', 'TST-POL005-045-CAT-CARDIAC-SURGERY', 'عمليات القلب والشرايين', 1, mc.id,
  2000, 2000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | مصرف الوحدة | الشركة 2000.00 / المشترك 0.00 | يتطلب موافقة مسبقة؛ اختبار سقف مالي', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'عمليات القلب والشرايين', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-CARDIAC-SURGERY', 'عمليات القلب والشرايين', 2000
FROM medical_categories mc
WHERE mc.code = 'CAT-CARDIAC-SURGERY';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار فحوصات وتخطيطات القلب - OUTPATIENT - مصرف الوحدة', 'TST-POL005-046-CAT-CARDIO-CHECKUP', 'فحوصات وتخطيطات القلب', 1, mc.id,
  600, 600, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | مصرف الوحدة | الشركة 600.00 / المشترك 0.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'فحوصات وتخطيطات القلب', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-CARDIO-CHECKUP', 'فحوصات وتخطيطات القلب', 600
FROM medical_categories mc
WHERE mc.code = 'CAT-CARDIO-CHECKUP';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار عناية القلب - INPATIENT - مصرف الوحدة', 'TST-POL005-047-CAT-CCU', 'عناية القلب', 1, mc.id,
  2000, 2000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | مصرف الوحدة | الشركة 1500.00 / المشترك 500.00 | يتطلب موافقة مسبقة؛ اختبار سقف مالي', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'عناية القلب', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-CCU', 'عناية القلب', 2000
FROM medical_categories mc
WHERE mc.code = 'CAT-CCU';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار تركيبات الأسنان - OUTPATIENT - مصرف الوحدة', 'TST-POL005-048-CAT-DENT-PROSTHO', 'تركيبات الأسنان', 1, mc.id,
  1000, 1000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | مصرف الوحدة | الشركة 800.00 / المشترك 200.00 | يتطلب موافقة مسبقة', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'تركيبات الأسنان', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-DENT-PROSTHO', 'تركيبات الأسنان', 1000
FROM medical_categories mc
WHERE mc.code = 'CAT-DENT-PROSTHO';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار الكشوفات الطبية - OUTPATIENT - مصرف الوحدة', 'TST-POL005-049-CAT-DIAGNOSTIC', 'الكشوفات الطبية', 1, mc.id,
  500, 500, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | مصرف الوحدة | الشركة 500.00 / المشترك 0.00 | اختبار سقف مالي', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'الكشوفات الطبية', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-DIAGNOSTIC', 'الكشوفات الطبية', 500
FROM medical_categories mc
WHERE mc.code = 'CAT-DIAGNOSTIC';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار أدوية الأمراض المزمنة - OUTPATIENT - مصرف الوحدة', 'TST-POL005-050-CAT-DRUG-CHRONIC', 'أدوية الأمراض المزمنة', 1, mc.id,
  300, 300, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | مصرف الوحدة | الشركة 300.00 / المشترك 0.00 | يتطلب موافقة مسبقة؛ اختبار سقف مالي', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'أدوية الأمراض المزمنة', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-DRUG-CHRONIC', 'أدوية الأمراض المزمنة', 300
FROM medical_categories mc
WHERE mc.code = 'CAT-DRUG-CHRONIC';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار أدوية الصرف العام - OUTPATIENT - مصرف الوحدة', 'TST-POL005-051-CAT-DRUG-GENERAL', 'أدوية الصرف العام', 1, mc.id,
  500, 500, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | مصرف الوحدة | الشركة 500.00 / المشترك 0.00 | اختبار سقف مالي', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'أدوية الصرف العام', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-DRUG-GENERAL', 'أدوية الصرف العام', 500
FROM medical_categories mc
WHERE mc.code = 'CAT-DRUG-GENERAL';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار المناظير - OUTPATIENT - مصرف الوحدة', 'TST-POL005-052-CAT-ENDOSCOPY', 'المناظير', 1, mc.id,
  600, 600, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | مصرف الوحدة | الشركة 600.00 / المشترك 0.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'المناظير', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-ENDOSCOPY', 'المناظير', 600
FROM medical_categories mc
WHERE mc.code = 'CAT-ENDOSCOPY';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار تكلفة مرافق واحد للشخص الذي تم إخلاؤه طبياً - INPATIENT - مصرف الوحدة', 'TST-POL005-053-CAT-EVAC-COMPANION', 'تكلفة مرافق واحد للشخص الذي تم إخلاؤه طبياً', 1, mc.id,
  600, 600, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | مصرف الوحدة | الشركة 600.00 / المشترك 0.00 | يتطلب موافقة مسبقة', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'تكلفة مرافق واحد للشخص الذي تم إخلاؤه طبياً', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-EVAC-COMPANION', 'تكلفة مرافق واحد للشخص الذي تم إخلاؤه طبياً', 600
FROM medical_categories mc
WHERE mc.code = 'CAT-EVAC-COMPANION';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار أدوية أمراض الخصوبة والعقم - OUTPATIENT - مصرف الوحدة', 'TST-POL005-054-CAT-FERTILITY-DRUG', 'أدوية أمراض الخصوبة والعقم', 1, mc.id,
  2000, 2000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | مصرف الوحدة | الشركة 2000.00 / المشترك 0.00 | يتطلب موافقة مسبقة؛ اختبار سقف مالي', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'أدوية أمراض الخصوبة والعقم', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-FERTILITY-DRUG', 'أدوية أمراض الخصوبة والعقم', 2000
FROM medical_categories mc
WHERE mc.code = 'CAT-FERTILITY-DRUG';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار السماعات الطبية - OUTPATIENT - مصرف الوحدة', 'TST-POL005-055-CAT-HEARING-AID', 'السماعات الطبية', 1, mc.id,
  2000, 2000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | مصرف الوحدة | الشركة 2000.00 / المشترك 0.00 | يتطلب موافقة مسبقة؛ اختبار سقف مالي؛ اختبار عدد مرات', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'السماعات الطبية', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-HEARING-AID', 'السماعات الطبية', 2000
FROM medical_categories mc
WHERE mc.code = 'CAT-HEARING-AID';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار العناية الفائقة - INPATIENT - مصرف الوحدة', 'TST-POL005-056-CAT-ICU', 'العناية الفائقة', 1, mc.id,
  2000, 2000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | مصرف الوحدة | الشركة 1500.00 / المشترك 500.00 | يتطلب موافقة مسبقة؛ اختبار سقف مالي', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'العناية الفائقة', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-ICU', 'العناية الفائقة', 2000
FROM medical_categories mc
WHERE mc.code = 'CAT-ICU';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار التصوير بالرنين المغناطيسي والمقطعي والطبقي - OUTPATIENT - مصرف الوحدة', 'TST-POL005-057-CAT-IMG-ADV', 'التصوير بالرنين المغناطيسي والمقطعي والطبقي', 1, mc.id,
  600, 600, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | مصرف الوحدة | الشركة 600.00 / المشترك 0.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'التصوير بالرنين المغناطيسي والمقطعي والطبقي', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-IMG-ADV', 'التصوير بالرنين المغناطيسي والمقطعي والطبقي', 600
FROM medical_categories mc
WHERE mc.code = 'CAT-IMG-ADV';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار الأشعة والصور التشخيصية - OUTPATIENT - مصرف الوحدة', 'TST-POL005-058-CAT-IMG-DIAG', 'الأشعة والصور التشخيصية', 1, mc.id,
  600, 600, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | مصرف الوحدة | الشركة 600.00 / المشترك 0.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'الأشعة والصور التشخيصية', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-IMG-DIAG', 'الأشعة والصور التشخيصية', 600
FROM medical_categories mc
WHERE mc.code = 'CAT-IMG-DIAG';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار التحاليل الطبية والمختبرات - OUTPATIENT - مصرف الوحدة', 'TST-POL005-059-CAT-LAB', 'التحاليل الطبية والمختبرات', 1, mc.id,
  600, 600, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | مصرف الوحدة | الشركة 600.00 / المشترك 0.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'التحاليل الطبية والمختبرات', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-LAB', 'التحاليل الطبية والمختبرات', 600
FROM medical_categories mc
WHERE mc.code = 'CAT-LAB';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار النظارات الطبية - OUTPATIENT - مصرف الوحدة', 'TST-POL005-060-CAT-OPT', 'النظارات الطبية', 1, mc.id,
  500, 500, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | مصرف الوحدة | الشركة 500.00 / المشترك 0.00 | يتطلب موافقة مسبقة؛ اختبار سقف مالي؛ اختبار عدد مرات', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'النظارات الطبية', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-OPT', 'النظارات الطبية', 500
FROM medical_categories mc
WHERE mc.code = 'CAT-OPT';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار العلاج الطبيعي - INPATIENT - مصرف الوحدة', 'TST-POL005-061-CAT-PHYSIO', 'العلاج الطبيعي', 1, mc.id,
  600, 600, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | مصرف الوحدة | الشركة 600.00 / المشترك 0.00 | يتطلب موافقة مسبقة؛ اختبار عدد مرات', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'العلاج الطبيعي', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-PHYSIO', 'العلاج الطبيعي', 600
FROM medical_categories mc
WHERE mc.code = 'CAT-PHYSIO';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار الإيواء في غرفة خاصة أو قسم - INPATIENT - مصرف الوحدة', 'TST-POL005-062-CAT-ROOM', 'الإيواء في غرفة خاصة أو قسم', 1, mc.id,
  2000, 2000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | مصرف الوحدة | الشركة 1500.00 / المشترك 500.00 | يتطلب موافقة مسبقة؛ اختبار سقف مالي', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'الإيواء في غرفة خاصة أو قسم', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-ROOM', 'الإيواء في غرفة خاصة أو قسم', 2000
FROM medical_categories mc
WHERE mc.code = 'CAT-ROOM';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار جلسات علاج النطق للأطفال حتى سن 16 عاماً - OUTPATIENT - مصرف الوحدة', 'TST-POL005-063-CAT-SPEECH-THERAPY', 'جلسات علاج النطق للأطفال حتى سن 16 عاماً', 1, mc.id,
  600, 600, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | مصرف الوحدة | الشركة 600.00 / المشترك 0.00 | يتطلب موافقة مسبقة؛ اختبار عدد مرات', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'جلسات علاج النطق للأطفال حتى سن 16 عاماً', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-SPEECH-THERAPY', 'جلسات علاج النطق للأطفال حتى سن 16 عاماً', 600
FROM medical_categories mc
WHERE mc.code = 'CAT-SPEECH-THERAPY';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار المنشطات والأدوية المرتبطة بها - OUTPATIENT - مصرف الوحدة', 'TST-POL005-064-CAT-STIMULANT-DRUG', 'المنشطات والأدوية المرتبطة بها', 1, mc.id,
  2000, 2000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | مصرف الوحدة | الشركة 2000.00 / المشترك 0.00 | يتطلب موافقة مسبقة؛ اختبار سقف مالي', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'المنشطات والأدوية المرتبطة بها', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-STIMULANT-DRUG', 'المنشطات والأدوية المرتبطة بها', 2000
FROM medical_categories mc
WHERE mc.code = 'CAT-STIMULANT-DRUG';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار العمليات الجراحية الصغرى للمرضى خارج المستشفى - INPATIENT - مصرف الوحدة', 'TST-POL005-065-CAT-SURGERY', 'العمليات الجراحية الصغرى للمرضى خارج المستشفى', 1, mc.id,
  2000, 2000, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: INPATIENT | مصرف الوحدة | الشركة 2000.00 / المشترك 0.00 | يتطلب موافقة مسبقة؛ اختبار سقف مالي', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'العمليات الجراحية الصغرى للمرضى خارج المستشفى', NULL,
  'INPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-SURGERY', 'العمليات الجراحية الصغرى للمرضى خارج المستشفى', 2000
FROM medical_categories mc
WHERE mc.code = 'CAT-SURGERY';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار العمليات الجراحية الصغرى للمرضى خارج المستشفى - OUTPATIENT - مصرف الوحدة', 'TST-POL005-066-CAT-SURGERY', 'العمليات الجراحية الصغرى للمرضى خارج المستشفى', 1, mc.id,
  600, 600, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | مصرف الوحدة | الشركة 600.00 / المشترك 0.00 | ', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'العمليات الجراحية الصغرى للمرضى خارج المستشفى', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-SURGERY', 'العمليات الجراحية الصغرى للمرضى خارج المستشفى', 600
FROM medical_categories mc
WHERE mc.code = 'CAT-SURGERY';


INSERT INTO provider_contract_pricing_items (
  contract_id, service_name, service_code, category_name, quantity, medical_category_id,
  base_price, contract_price, discount_percent, unit, currency, effective_from, effective_to,
  notes, active, created_at, updated_at, created_by, updated_by, sub_category_name, specialty,
  encounter_type, requires_review, review_reason, classification_status, confidence_level,
  classification_source, imported_main_category, imported_sub_category, max_contract_price
)
SELECT
  3101, 'اختبار الحقن العلاجية - OUTPATIENT - مصرف الوحدة', 'TST-POL005-067-CAT-THERAPEUTIC-INJ', 'الحقن العلاجية', 1, mc.id,
  1500, 1500, 0, 'مرة', 'LYD', CURRENT_DATE, DATE '2027-12-31',
  'عينة اختبار قواعد التغطية | سياق القاعدة الأصلي: OUTPATIENT | مصرف الوحدة | الشركة 1500.00 / المشترك 0.00 | يتطلب موافقة مسبقة؛ اختبار سقف مالي؛ اختبار عدد مرات', true, now(), now(), 'codex-test-seed', 'codex-test-seed', 'الحقن العلاجية', NULL,
  'OUTPATIENT', false, NULL, 'APPROVED', 'HIGH',
  'COVERAGE_RULE_TEST_SAMPLE', 'CAT-THERAPEUTIC-INJ', 'الحقن العلاجية', 1500
FROM medical_categories mc
WHERE mc.code = 'CAT-THERAPEUTIC-INJ';

DO $$ BEGIN IF (SELECT count(*) FROM provider_contract_pricing_items WHERE contract_id=3101) <> 67 THEN RAISE EXCEPTION 'Expected 67 pricing items for provider test contract, found %', (SELECT count(*) FROM provider_contract_pricing_items WHERE contract_id=3101); END IF; END $$;
COMMIT;