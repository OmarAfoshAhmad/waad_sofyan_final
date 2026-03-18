-- ============================================================
-- V35: تنظيف medical_categories — حذف الأعمدة اليتيمة
--
-- السبب: V9 الأصلي أنشأ أعمدة قديمة (category_name, category_code, ...)
--        لا يوجد لها أي حقل مُعيَّن في MedicalCategory.java،
--        مما تسبّب في خطأ NOT NULL عند الإنشاء والتعديل.
--
-- الأعمدة المحذوفة:
--   category_name    VARCHAR(255) NOT NULL  → legacy (كان قبل إضافة name)
--   category_name_ar VARCHAR(255)           → legacy (كان قبل إضافة name_ar)
--   category_code    VARCHAR(50)  NOT NULL  → legacy (كان قبل إضافة code)
--   description      TEXT                   → محذوف من الكيان
--   created_by       VARCHAR(255)           → محذوف من الكيان
--   updated_by       VARCHAR(255)           → محذوف من الكيان
-- ============================================================

-- ────────────────────────────────────────────────────────────
-- 1) حذف الأعمدة اليتيمة
-- ────────────────────────────────────────────────────────────
ALTER TABLE medical_categories DROP COLUMN IF EXISTS category_name;
ALTER TABLE medical_categories DROP COLUMN IF EXISTS category_name_ar;
ALTER TABLE medical_categories DROP COLUMN IF EXISTS category_code;
ALTER TABLE medical_categories DROP COLUMN IF EXISTS description;
ALTER TABLE medical_categories DROP COLUMN IF EXISTS created_by;
ALTER TABLE medical_categories DROP COLUMN IF EXISTS updated_by;

-- ────────────────────────────────────────────────────────────
-- 2) ضبط قيود NOT NULL لتتوافق مع الكيان
--    (V9 الأصلي أنشأها بـ DEFAULT فقط بدون NOT NULL)
-- ────────────────────────────────────────────────────────────
UPDATE medical_categories SET active     = true  WHERE active     IS NULL;
UPDATE medical_categories SET created_at = NOW() WHERE created_at IS NULL;
UPDATE medical_categories SET updated_at = NOW() WHERE updated_at IS NULL;

ALTER TABLE medical_categories ALTER COLUMN active     SET NOT NULL;
ALTER TABLE medical_categories ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE medical_categories ALTER COLUMN updated_at SET NOT NULL;

-- ────────────────────────────────────────────────────────────
-- 3) إضافة فهرس اسمه موحّد إن لم يكن موجوداً
--    (V9 الأصلي أنشأه باسم idx_medical_categories_deleted_code)
-- ────────────────────────────────────────────────────────────
DROP INDEX IF EXISTS idx_medical_categories_deleted_code;
CREATE INDEX IF NOT EXISTS idx_medical_categories_del_code
    ON medical_categories(deleted, code);

-- ────────────────────────────────────────────────────────────
-- 4) إضافة اسم مقيّد صريح للـ UNIQUE على code (إن لم يكن)
-- ────────────────────────────────────────────────────────────
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
         WHERE table_name = 'medical_categories'
           AND constraint_name = 'uq_medical_categories_code'
    ) THEN
        ALTER TABLE medical_categories
            ADD CONSTRAINT uq_medical_categories_code UNIQUE (code);
    END IF;
END $$;
