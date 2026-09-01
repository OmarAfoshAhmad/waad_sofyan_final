-- =============================================================================
-- V213: تصنيفات التغطية تُعلن السياقات التي تصحّ فيها.
--
-- التصنيفات التي أنشأتها V200/V201/V207 -- وهي التي تصنّف إليها قوائم الأسعار
-- 80% من خدماتها -- أُنشئت بلا صفوف في medical_category_contexts. وتصنيف بلا
-- سياق معلن يعني أن مستورِد قواعد التغطية يرفض أي قاعدة عليه، فيستحيل إعداد
-- التغطية لأكثر من ثمانية عشر ألف خدمة مسعّرة.
--
-- محرّك التغطية لا يتأثر بهذا (يتخطى الفحص عند غياب السياقات)، فالأثر كان
-- محصوراً في الاستيراد -- وهذا بالضبط ما يجعل العطل صامتاً حتى تُحاول الإعداد.
--
-- السياقات أدناه هي ما يصحّ فيه كل تصنيف فعلاً:
--   الإيواء إيواءً فقط، والعيادات الخارجية والأسنان والنظارات خارجيةً فقط،
--   وأشعة وتحاليل ورسوم الأطباء في الاثنين لأنها تقع داخل الإيواء وخارجه.
-- =============================================================================

INSERT INTO medical_category_contexts (category_id, context_type, is_default, is_active)
SELECT c.id, v.context_type, v.is_default, true
FROM medical_categories c
JOIN (VALUES
        ('CAT-COV-INPATIENT',     'INPATIENT',  true),
        ('CAT-COV-OUTPATIENT',    'OUTPATIENT', true),
        ('CAT-COV-DIAG-FEES',     'OUTPATIENT', true),
        ('CAT-COV-DIAG-FEES',     'INPATIENT',  false),
        ('CAT-COV-EYE-OPTICAL',   'OUTPATIENT', true),
        ('CAT-COV-DENT-ADVANCED', 'OUTPATIENT', true),
        ('CAT-DENT-COSMETIC',     'OUTPATIENT', true)
     ) AS v(code, context_type, is_default)
  ON v.code = c.code
WHERE c.active AND NOT c.deleted
ON CONFLICT (category_id, context_type) DO UPDATE SET
    is_active = true;

-- لا تصنيف تغطية بلا سياق بعد اليوم: تصنيف تصنّف إليه قوائم الأسعار ولا يقبل
-- قاعدة تغطية هو تصنيف لا يمكن أن يُغطّى، وذلك أسوأ من غيابه.
DO $$
DECLARE orphans TEXT;
BEGIN
    SELECT string_agg(c.code, ', ' ORDER BY c.code)
      INTO orphans
      FROM medical_categories c
     WHERE c.active AND NOT c.deleted
       AND c.code LIKE 'CAT-COV-%'
       AND NOT EXISTS (SELECT 1 FROM medical_category_contexts mc
                        WHERE mc.category_id = c.id AND mc.is_active);

    IF orphans IS NOT NULL THEN
        RAISE EXCEPTION 'V213: تصنيفات تغطية بلا سياق معلن: %', orphans;
    END IF;
END $$;
