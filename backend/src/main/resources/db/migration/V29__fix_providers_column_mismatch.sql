-- ============================================================
-- V29: إصلاح تعارض أعمدة جدول providers
--
-- الكيان Provider يتوقع أعمدة بأسماء مختلفة عمّا أنشأه V3.
-- هذا يسبب خطأ 500 عند جلب قائمة المزودين:
--   "column does not exist" (Position ~93 in the SELECT query)
--
-- الفجوات المكتشفة:
--   Entity field        → DB column (V3)
--   name                → provider_name (اسم مختلف)
--   phone               → contact_phone (اسم مختلف)
--   email               → contact_email (اسم مختلف)
--   tax_number          → tax_company_code (اسم مختلف)
--   network_status      → provider_status (اسم مختلف)
--   contract_start_date → (مفقود تماماً)
--   contract_end_date   → (مفقود تماماً)
--   default_discount_rate → (مفقود تماماً)
-- ============================================================

-- ── 1. name ──────────────────────────────────────────────────
-- الكيان يستخدم @Column(name = "name") — DB لديه provider_name
ALTER TABLE providers ADD COLUMN IF NOT EXISTS name VARCHAR(200);
UPDATE providers SET name = provider_name WHERE name IS NULL;
ALTER TABLE providers ALTER COLUMN name SET NOT NULL;

-- ── 2. phone ─────────────────────────────────────────────────
ALTER TABLE providers ADD COLUMN IF NOT EXISTS phone VARCHAR(50);
UPDATE providers SET phone = contact_phone WHERE phone IS NULL AND contact_phone IS NOT NULL;

-- ── 3. email ─────────────────────────────────────────────────
ALTER TABLE providers ADD COLUMN IF NOT EXISTS email VARCHAR(100);
UPDATE providers SET email = contact_email WHERE email IS NULL AND contact_email IS NOT NULL;

-- ── 4. tax_number ────────────────────────────────────────────
ALTER TABLE providers ADD COLUMN IF NOT EXISTS tax_number VARCHAR(50);
UPDATE providers SET tax_number = tax_company_code WHERE tax_number IS NULL AND tax_company_code IS NOT NULL;

-- ── 5. network_status ────────────────────────────────────────
-- الكيان يستخدم @Column network_status بقيم: IN_NETWORK, OUT_OF_NETWORK, PREFERRED
-- DB كان provider_status بقيم مختلفة
ALTER TABLE providers ADD COLUMN IF NOT EXISTS network_status VARCHAR(20);

-- ── 6. contract_start_date ───────────────────────────────────
ALTER TABLE providers ADD COLUMN IF NOT EXISTS contract_start_date DATE;

-- ── 7. contract_end_date ─────────────────────────────────────
ALTER TABLE providers ADD COLUMN IF NOT EXISTS contract_end_date DATE;

-- ── 8. default_discount_rate ─────────────────────────────────
ALTER TABLE providers ADD COLUMN IF NOT EXISTS default_discount_rate NUMERIC(5,2);
