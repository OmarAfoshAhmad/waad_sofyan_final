-- ============================================================
-- V30: إصلاح عمودين تعذّر استخدامهما
--
-- الخطأ 1: GET /providers/by-employer/1 [500]
--   الاستعلام JPQL يستخدم pae.active ولكن
--   provider_allowed_employers (V4) لا يملك عمود active
--
-- الخطأ 2: GET /provider-contracts [500]
--   الكيان ModernProviderContract يتوقع: auto_renew, contract_code,
--   start_date, end_date, pricing_model, ... كلها مفقودة من V12
-- ============================================================

-- ════════════════════════════════════════════════════════════
-- 1. إصلاح provider_allowed_employers
-- ════════════════════════════════════════════════════════════

ALTER TABLE provider_allowed_employers
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT true;

ALTER TABLE provider_allowed_employers
    ADD COLUMN IF NOT EXISTS notes VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_pae_active ON provider_allowed_employers(active);

-- ════════════════════════════════════════════════════════════
-- 2. إصلاح provider_contracts
--    الأعمدة الجديدة المطلوبة من ModernProviderContract entity
-- ════════════════════════════════════════════════════════════

-- 2a. contract_code — مطلوب NOT NULL UNIQUE بواسطة الكيان
ALTER TABLE provider_contracts
    ADD COLUMN IF NOT EXISTS contract_code VARCHAR(50);

-- توليد قيمة مؤقتة للصفوف الموجودة (إن وُجدت) قبل إضافة NOT NULL
UPDATE provider_contracts
    SET contract_code = 'CON-LEGACY-' || id
    WHERE contract_code IS NULL;

ALTER TABLE provider_contracts ALTER COLUMN contract_code SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_contracts_contract_code
    ON provider_contracts(contract_code);

-- 2b. pricing_model — NOT NULL DEFAULT 'DISCOUNT'
ALTER TABLE provider_contracts
    ADD COLUMN IF NOT EXISTS pricing_model VARCHAR(20);

UPDATE provider_contracts SET pricing_model = 'DISCOUNT' WHERE pricing_model IS NULL;

ALTER TABLE provider_contracts ALTER COLUMN pricing_model SET NOT NULL;
ALTER TABLE provider_contracts ALTER COLUMN pricing_model SET DEFAULT 'DISCOUNT';

-- 2c. start_date / end_date (الكيان يستخدم start_date بدل contract_start_date)
ALTER TABLE provider_contracts
    ADD COLUMN IF NOT EXISTS start_date DATE;

ALTER TABLE provider_contracts
    ADD COLUMN IF NOT EXISTS end_date DATE;

-- نسخ القيم من الأعمدة القديمة (إن وُجدت قيم)
UPDATE provider_contracts
    SET start_date = contract_start_date
    WHERE start_date IS NULL AND contract_start_date IS NOT NULL;

UPDATE provider_contracts
    SET end_date = contract_end_date
    WHERE end_date IS NULL AND contract_end_date IS NOT NULL;

-- للصفوف التي ليس لها تاريخ بداية نضع تاريخاً افتراضياً
UPDATE provider_contracts
    SET start_date = CURRENT_DATE
    WHERE start_date IS NULL;

ALTER TABLE provider_contracts ALTER COLUMN start_date SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_contracts_start_date ON provider_contracts(start_date);
CREATE INDEX IF NOT EXISTS idx_contracts_end_date   ON provider_contracts(end_date);

-- 2d. signed_date
ALTER TABLE provider_contracts
    ADD COLUMN IF NOT EXISTS signed_date DATE;

-- 2e. total_value (deprecated — محتفظ للتوافق)
ALTER TABLE provider_contracts
    ADD COLUMN IF NOT EXISTS total_value NUMERIC(15,2);

-- 2f. currency
ALTER TABLE provider_contracts
    ADD COLUMN IF NOT EXISTS currency VARCHAR(3);

UPDATE provider_contracts SET currency = 'LYD' WHERE currency IS NULL;

-- 2g. auto_renew — NOT NULL DEFAULT false
ALTER TABLE provider_contracts
    ADD COLUMN IF NOT EXISTS auto_renew BOOLEAN;

UPDATE provider_contracts SET auto_renew = false WHERE auto_renew IS NULL;

ALTER TABLE provider_contracts ALTER COLUMN auto_renew SET NOT NULL;
ALTER TABLE provider_contracts ALTER COLUMN auto_renew SET DEFAULT false;

-- 2h. بيانات التواصل
ALTER TABLE provider_contracts
    ADD COLUMN IF NOT EXISTS contact_person VARCHAR(100);

ALTER TABLE provider_contracts
    ADD COLUMN IF NOT EXISTS contact_phone VARCHAR(50);

ALTER TABLE provider_contracts
    ADD COLUMN IF NOT EXISTS contact_email VARCHAR(100);

-- 2i. notes
ALTER TABLE provider_contracts
    ADD COLUMN IF NOT EXISTS notes VARCHAR(2000);

-- 2j. discount_rate (deprecated — محتفظ للتوافق)
ALTER TABLE provider_contracts
    ADD COLUMN IF NOT EXISTS discount_rate NUMERIC(5,2);

-- ════════════════════════════════════════════════════════════
-- 3. تحرير قيود NOT NULL التي تتعارض مع الكيان الجديد
-- ════════════════════════════════════════════════════════════

-- المزود القديم كان يتطلب employer_id إلزامياً
-- الكيان الجديد ModernProviderContract لا يملك حقل employerId
ALTER TABLE provider_contracts ALTER COLUMN employer_id DROP NOT NULL;

-- contract_number كان NOT NULL في V12 لكن الكيان الجديد يسمح بـ null
ALTER TABLE provider_contracts ALTER COLUMN contract_number DROP NOT NULL;
