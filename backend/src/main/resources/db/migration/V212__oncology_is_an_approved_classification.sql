-- =============================================================================
-- V212: علاج الأورام تصنيف تأميني معتمد مستقل.
--
-- قرار عمل: الأورام بند مستقل بنسبته الخاصة، لا يذوب في «إيواء». وثيقة جليانة
-- تحمل بالفعل قاعدة CAT-ONCOLOGY بنسبة 100% بينما كل ما عداها 75%، فلو صُنِّفت
-- خدمات الأورام على وعاء الإيواء لفقد المستفيد ربع تغطيته -- خطأ مالي لا خطأ
-- تسمية.
--
-- التسميات بصيغتها بعد التطبيع الذي يطبّقه MedicalDictionaryNormalizer، وقد
-- تحقّقت من مطابقتها للصيغ المزروعة سابقاً. ثلاث كتابات لأن المزوّدين يكتبونها
-- بأشكال مختلفة، وكلها تشير إلى نفس التصنيف.
--
-- السياق هنا قيمة المصدر الافتراضية: قرار التغطية يؤخذ من سياق المطالبة الذي
-- يختاره المدخِل. اختير INPATIENT لأنه السياق الذي تحمل فيه الوثيقة قاعدة
-- الأورام اليوم؛ علاج الأورام في العيادات الخارجية يحتاج قاعدته في سياقه.
-- =============================================================================

INSERT INTO claim_context_source_aliases
    (source_alias, normalized_alias, claim_context_code, medical_category_code, requires_review)
VALUES
    ('علاج الأورام', 'علاج الاورام', 'INPATIENT', 'CAT-ONCOLOGY', false),
    ('الأورام',      'الاورام',      'INPATIENT', 'CAT-ONCOLOGY', false),
    ('أورام',        'اورام',        'INPATIENT', 'CAT-ONCOLOGY', false)

ON CONFLICT (normalized_alias, COALESCE(provider_id, 0)) DO UPDATE SET
    claim_context_code    = EXCLUDED.claim_context_code,
    medical_category_code = EXCLUDED.medical_category_code,
    requires_review       = EXCLUDED.requires_review,
    active                = true;

-- نفس حارس V211: تسمية تشير إلى تصنيف غير موجود تعيد العطل الذي أُصلح --
-- مطابقة تنجح ثم تُهمَل لأن هدفها مفقود.
DO $$
DECLARE missing TEXT;
BEGIN
    SELECT string_agg(a.medical_category_code, ', ')
      INTO missing
      FROM claim_context_source_aliases a
      LEFT JOIN medical_categories c
             ON c.code = a.medical_category_code AND c.active AND NOT c.deleted
     WHERE a.active
       AND a.medical_category_code IS NOT NULL
       AND c.code IS NULL;

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'V212: تسميات تشير إلى تصنيفات غير موجودة: %', missing;
    END IF;
END $$;
