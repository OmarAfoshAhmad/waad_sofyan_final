-- ============================================================
-- V31: إصلاح قيد NOT NULL على provider_name في جدول providers
--
-- المشكلة:
--   V3 أنشأ provider_name NOT NULL
--   V29 أضاف عمود name بديلاً (يستخدمه الكيان)، لكن لم يُلغِ
--   قيد NOT NULL من provider_name.
--   النتيجة: INSERT جديد يملأ name لكن يترك provider_name = NULL
--   → ERROR: null value in column "provider_name" of relation "providers"
--
-- الإصلاح: إلغاء NOT NULL من provider_name ومزامنته دائماً مع name
-- ============================================================

-- 1. إلغاء NOT NULL من العمود القديم
ALTER TABLE providers ALTER COLUMN provider_name DROP NOT NULL;

-- 2. مزامنة القيم الموجودة (name → provider_name) لتجنب بيانات مفقودة
UPDATE providers SET provider_name = name WHERE provider_name IS NULL AND name IS NOT NULL;

-- 3. أيضاً provider_name_ar كثيراً ما يُفقد — إلغاء NOT NULL إن كان موجوداً
ALTER TABLE providers ALTER COLUMN provider_name_ar DROP NOT NULL;
