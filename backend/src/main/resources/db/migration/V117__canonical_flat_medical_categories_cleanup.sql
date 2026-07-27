-- Canonical flat medical categories cleanup.
-- Purpose:
-- 1) Retire legacy parent/multi-root category hierarchy.
-- 2) Canonicalize the approved 46-category list used by Jaliana + Unit Bank benefit rules.
-- 3) Migrate legacy CAT-DRUG rules/references to CAT-DRUG-GENERAL.
-- 4) Remove empty numeric training categories and legacy CAT-DRUG.

DO $$
DECLARE
    old_drug_id bigint;
    general_drug_id bigint;
BEGIN
    SELECT id INTO old_drug_id FROM medical_categories WHERE code = 'CAT-DRUG' LIMIT 1;
    SELECT id INTO general_drug_id FROM medical_categories WHERE code = 'CAT-DRUG-GENERAL' LIMIT 1;

    IF old_drug_id IS NOT NULL AND general_drug_id IS NOT NULL THEN
        UPDATE benefit_policy_rules SET medical_category_id = general_drug_id WHERE medical_category_id = old_drug_id;
        UPDATE benefit_policy_template_rules SET medical_category_id = general_drug_id WHERE medical_category_id = old_drug_id;
        UPDATE medical_services SET category_id = general_drug_id WHERE category_id = old_drug_id;
        UPDATE medical_service_categories SET category_id = general_drug_id WHERE category_id = old_drug_id;
        UPDATE medical_specialties SET category_id = general_drug_id WHERE category_id = old_drug_id;
        UPDATE provider_contract_pricing_items SET medical_category_id = general_drug_id WHERE medical_category_id = old_drug_id;
        UPDATE benefit_policy_excluded_categories SET category_code = 'CAT-DRUG-GENERAL' WHERE category_code = 'CAT-DRUG';
    END IF;
END $$;

DELETE FROM medical_category_roots;
UPDATE medical_categories SET parent_id = NULL;

UPDATE medical_categories SET name='الكشوفات الطبية', name_ar='الكشوفات الطبية', active=true, deleted=false WHERE code='CAT-DIAGNOSTIC';
UPDATE medical_categories SET name='أدوية الصرف العام', name_ar='أدوية الصرف العام', active=true, deleted=false WHERE code='CAT-DRUG-GENERAL';
UPDATE medical_categories SET name='الأشعة والصور التشخيصية', name_ar='الأشعة والصور التشخيصية', active=true, deleted=false WHERE code='CAT-IMG-DIAG';
UPDATE medical_categories SET name='التصوير بالرنين المغناطيسي والمقطعي والطبقي', name_ar='التصوير بالرنين المغناطيسي والمقطعي والطبقي', active=true, deleted=false WHERE code='CAT-IMG-ADV';
UPDATE medical_categories SET name='المناظير', name_ar='المناظير', active=true, deleted=false WHERE code='CAT-ENDOSCOPY';
UPDATE medical_categories SET name='فحوصات وتخطيطات القلب', name_ar='فحوصات وتخطيطات القلب', active=true, deleted=false WHERE code='CAT-CARDIO-CHECKUP';
UPDATE medical_categories SET name='التحاليل الطبية والمختبرات', name_ar='التحاليل الطبية والمختبرات', active=true, deleted=false WHERE code='CAT-LAB';
UPDATE medical_categories SET name='العمليات الجراحية الصغرى للمرضى خارج المستشفى', name_ar='العمليات الجراحية الصغرى للمرضى خارج المستشفى', active=true, deleted=false WHERE code='CAT-SURGERY';
UPDATE medical_categories SET name='عمليات القلب والشرايين', name_ar='عمليات القلب والشرايين', active=true, deleted=false WHERE code='CAT-CARDIAC-SURGERY';
UPDATE medical_categories SET name='الإيواء في غرفة خاصة أو قسم', name_ar='الإيواء في غرفة خاصة أو قسم', active=true, deleted=false WHERE code='CAT-ROOM';
UPDATE medical_categories SET name='العناية الفائقة', name_ar='العناية الفائقة', active=true, deleted=false WHERE code='CAT-ICU';
UPDATE medical_categories SET name='عناية القلب', name_ar='عناية القلب', active=true, deleted=false WHERE code='CAT-CCU';
UPDATE medical_categories SET name='العلاج الطبيعي', name_ar='العلاج الطبيعي', active=true, deleted=false WHERE code='CAT-PHYSIO';
UPDATE medical_categories SET name='جلسات علاج النطق للأطفال حتى سن 16 عاماً', name_ar='جلسات علاج النطق للأطفال حتى سن 16 عاماً', active=true, deleted=false WHERE code='CAT-SPEECH-THERAPY';
UPDATE medical_categories SET name='أدوية أمراض الخصوبة والعقم', name_ar='أدوية أمراض الخصوبة والعقم', active=true, deleted=false WHERE code='CAT-FERTILITY-DRUG';
UPDATE medical_categories SET name='الحقن العلاجية', name_ar='الحقن العلاجية', active=true, deleted=false WHERE code='CAT-THERAPEUTIC-INJ';
UPDATE medical_categories SET name='النظارات الطبية', name_ar='النظارات الطبية', active=true, deleted=false WHERE code='CAT-OPT';
UPDATE medical_categories SET name='تركيبات الأسنان', name_ar='تركيبات الأسنان', active=true, deleted=false WHERE code='CAT-DENT-PROSTHO';
UPDATE medical_categories SET name='الأجهزة والمعدات الطبية وفق تقرير الطبيب المختص', name_ar='الأجهزة والمعدات الطبية وفق تقرير الطبيب المختص', active=true, deleted=false WHERE code='CAT-DME';
UPDATE medical_categories SET name='أدوية الأمراض المزمنة', name_ar='أدوية الأمراض المزمنة', active=true, deleted=false WHERE code='CAT-DRUG-CHRONIC';
UPDATE medical_categories SET name='المنشطات والأدوية المرتبطة بها', name_ar='المنشطات والأدوية المرتبطة بها', active=true, deleted=false WHERE code='CAT-STIMULANT-DRUG';
UPDATE medical_categories SET name='تكلفة مرافق واحد للشخص الذي تم إخلاؤه طبياً', name_ar='تكلفة مرافق واحد للشخص الذي تم إخلاؤه طبياً', active=true, deleted=false WHERE code='CAT-EVAC-COMPANION';
UPDATE medical_categories SET name='السماعات الطبية', name_ar='السماعات الطبية', active=true, deleted=false WHERE code='CAT-HEARING-AID';
UPDATE medical_categories SET name='المستلزمات الطبية', name_ar='المستلزمات الطبية', active=true, deleted=false WHERE code='CAT-MED-SUP';
UPDATE medical_categories SET name='رسوم الأطباء والجراحين والمستشارين والممارسين', name_ar='رسوم الأطباء والجراحين والمستشارين والممارسين', active=true, deleted=false WHERE code='CAT-PRACT-FEE';
UPDATE medical_categories SET name='المعدات والمواد الجراحية', name_ar='المعدات والمواد الجراحية', active=true, deleted=false WHERE code='CAT-SURG-MAT';
UPDATE medical_categories SET name='نفقات التخدير', name_ar='نفقات التخدير', active=true, deleted=false WHERE code='CAT-ANESTHESIA';
UPDATE medical_categories SET name='العلاج والرعاية اليومية', name_ar='العلاج والرعاية اليومية', active=true, deleted=false WHERE code='CAT-DAY-CARE';
UPDATE medical_categories SET name='علاج الأسنان الطارئ للمريض داخل المستشفى', name_ar='علاج الأسنان الطارئ للمريض داخل المستشفى', active=true, deleted=false WHERE code='CAT-DENT-EMERG';
UPDATE medical_categories SET name='الإسعاف المحلي', name_ar='الإسعاف المحلي', active=true, deleted=false WHERE code='CAT-AMBULANCE';
UPDATE medical_categories SET name='التمريض المنزلي أو رعاية النقاهة بعد الخروج', name_ar='التمريض المنزلي أو رعاية النقاهة بعد الخروج', active=true, deleted=false WHERE code='CAT-HOME-NURSING';
UPDATE medical_categories SET name='أدوية الطب النفسي', name_ar='أدوية الطب النفسي', active=true, deleted=false WHERE code='CAT-PSYCH-DRUG';
UPDATE medical_categories SET name='جلسات الطب النفسي', name_ar='جلسات الطب النفسي', active=true, deleted=false WHERE code='CAT-PSYCH-SESS';
UPDATE medical_categories SET name='علاج الأورام', name_ar='علاج الأورام', active=true, deleted=false WHERE code='CAT-ONCOLOGY';
UPDATE medical_categories SET name='الغسيل الكلوي', name_ar='الغسيل الكلوي', active=true, deleted=false WHERE code='CAT-DIALYSIS';
UPDATE medical_categories SET name='الولادة الطبيعية', name_ar='الولادة الطبيعية', active=true, deleted=false WHERE code='CAT-MAT-NORMAL';
UPDATE medical_categories SET name='الولادة القيصرية', name_ar='الولادة القيصرية', active=true, deleted=false WHERE code='CAT-MAT-CS';
UPDATE medical_categories SET name='مضاعفات الحمل والولادة', name_ar='مضاعفات الحمل والولادة', active=true, deleted=false WHERE code='CAT-MAT-COMP';
UPDATE medical_categories SET name='علاج الأسنان الروتيني', name_ar='علاج الأسنان الروتيني', active=true, deleted=false WHERE code='CAT-DENT-ROUTINE';
UPDATE medical_categories SET name='تقويم الأسنان', name_ar='تقويم الأسنان', active=true, deleted=false WHERE code='CAT-DENT-ORTHO';
UPDATE medical_categories SET name='زراعة الأسنان', name_ar='زراعة الأسنان', active=true, deleted=false WHERE code='CAT-DENT-IMPLANT';
UPDATE medical_categories SET name='كشوفات العيون', name_ar='كشوفات العيون', active=true, deleted=false WHERE code='CAT-EYE-EXAM';
UPDATE medical_categories SET name='زرع الأعضاء', name_ar='زرع الأعضاء', active=true, deleted=false WHERE code='CAT-TRANSPLANT';
UPDATE medical_categories SET name='علاج إصابات العمل', name_ar='علاج إصابات العمل', active=true, deleted=false WHERE code='CAT-WORK-INJURY';
UPDATE medical_categories SET name='سفر أحد أفراد العائلة في حالة الإخلاء الطبي', name_ar='سفر أحد أفراد العائلة في حالة الإخلاء الطبي', active=true, deleted=false WHERE code='CAT-FAMILY-TRAVEL';
UPDATE medical_categories SET name='الإخلاء الطبي', name_ar='الإخلاء الطبي', active=true, deleted=false WHERE code='CAT-MED-EVAC';

DELETE FROM medical_category_contexts
WHERE category_id IN (
    SELECT id FROM medical_categories
    WHERE code ~ '^[0-9]+$' OR code = 'CAT-DRUG' OR trim(coalesce(name,'')) = '' OR trim(coalesce(name_ar,'')) = ''
);
DELETE FROM medical_categories
WHERE code ~ '^[0-9]+$' OR code = 'CAT-DRUG' OR trim(coalesce(name,'')) = '' OR trim(coalesce(name_ar,'')) = '';

-- Guardrails: no hierarchy edges should survive after this migration.
UPDATE medical_categories SET parent_id = NULL;
DELETE FROM medical_category_roots;
