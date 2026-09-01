-- =============================================================================
-- V211: كل تصنيف تأميني معتمد له تسمية يفهمها النظام.
--
-- المرجع: «التصنيفات التأمينية المعتمدة.xlsx» — خمسة عشر بنداً.
-- كان جدول التسميات يغطي ثمانية منها فقط، فكانت البقية تصل إلى شاشة المراجعة
-- بلا تصنيف مهما كان الملف صحيحاً، لأن المُحلِّل لا يعرف اسمها.
--
-- بندان من الخمسة عشر ليسا تصنيفَي تغطية:
--   «ولادة طبيعية وقيصرية» سياقٌ (MATERNITY)، فتحدّد تسميتُه السياق وحده.
--   «طب نفسي» مؤجَّل بقرار حتى يُحسم فصل الجلسات عن الأدوية.
--
-- التسميات مكتوبة بصيغتها بعد التطبيع الذي يطبّقه MedicalDictionaryNormalizer:
-- أ/إ/آ ← ا، ة ← ه، ى ← ي، حذف التشكيل والتطويل، وتوحيد المسافات. أي انحراف عن
-- هذه الصيغة يعني تسمية لا تُطابَق أبداً، فتفشل بصمت.
--
-- السياق هنا قيمة المصدر الافتراضية لا قرار تغطية: قرار التغطية يؤخذ من سياق
-- المطالبة الذي يختاره المدخِل، لا من بند التسعير.
-- =============================================================================

INSERT INTO claim_context_source_aliases
    (source_alias, normalized_alias, claim_context_code, medical_category_code, requires_review)
VALUES
    -- تصنيفات لا لبس فيها: تُصنَّف تلقائياً.
    ('إصابات عمل', 'اصابات عمل',
     'OUTPATIENT', 'CAT-WORK-INJURY', false),

    ('مضاعفات حمل', 'مضاعفات حمل',
     'PREGNANCY_COMPLICATIONS', 'CAT-MAT-COMP', false),

    ('علاجات وأدوية روتينية', 'علاجات وادويه روتينيه',
     'OUTPATIENT', 'CAT-DRUG-GENERAL', false),

    ('أجهزة ومعدات', 'اجهزه ومعدات',
     'OUTPATIENT', 'CAT-DME', false),

    -- النظارة الطبية تُصنَّف تلقائياً لأن الوعاء الجامع موجود فعلاً:
    -- CAT-COV-EYE-OPTICAL أبٌ لـ CAT-EYE-EXAM و CAT-OPT، فقاعدة واحدة عليه
    -- تكفي الاثنين بالوراثة. هذا هو النمط الصحيح الذي تفتقده الحالتان أدناه.
    ('النظارة الطبية', 'النظاره الطبيه',
     'OUTPATIENT', 'CAT-COV-EYE-OPTICAL', false),

    -- الولادة سياقٌ لا تصنيف. فهذه التسمية تحدّد السياق وحده وتترك التصنيف
    -- فارغاً: الخدمة نفسها (ولادة طبيعية أو قيصرية) هي ما يحدّد التصنيف، لا
    -- عنوان القائمة. وترك التصنيف فارغاً يوجب المراجعة بحكم قيد V201 نفسه --
    -- «تسمية تُعتمد تلقائياً بلا تصنيف تغطية» ممنوعة -- وهو القيد الصحيح هنا:
    -- السياق معروف، والتصنيف يُختار عند المراجعة.
    ('ولادة طبيعية وقيصرية', 'ولاده طبيعيه وقيصريه',
     'MATERNITY', NULL, true)

    -- «طب نفسي» مؤجَّل بقرار: تصنيفه يحتاج فصلاً بين الجلسات والأدوية لم يُحسم
    -- بعد، وتسمية مؤقتة تختار أحد الفرعين تكتب قراراً لم يُتخذ.

ON CONFLICT (normalized_alias, COALESCE(provider_id, 0)) DO UPDATE SET
    claim_context_code    = EXCLUDED.claim_context_code,
    medical_category_code = EXCLUDED.medical_category_code,
    requires_review       = EXCLUDED.requires_review,
    active                = true;

-- لا تسمية بلا تصنيف موجود: تسمية تشير إلى رمز غير موجود تعيد الحالة التي
-- تُصلحها هذه الهجرة — مطابقة تنجح ثم تُهمَل لأن هدفها مفقود.
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
        RAISE EXCEPTION 'V211: تسميات تشير إلى تصنيفات غير موجودة: %', missing;
    END IF;
END $$;
