-- ============================================================
-- V34: تنظيف شامل للإنتاج — حذف الأعمدة اليتيمة والمكررة
--      وإصلاح الأنواع وإضافة القيود المفقودة
--
-- الجداول المُعالجة:
--   ① providers                    (V3 + V29)
--   ② provider_admin_documents     (V4 + V32)
--   ③ provider_contracts           (V12 + V30)
--   ④ employers                    (V2)
--   ⑤ benefit_policies             (V11 + V26/V27 + V33)
--   ⑥ benefit_policy_rules         (V11 + V33)
--
-- المبدأ: كل عمود ليس له حقل في الكيان يُحذف.
--         كل قيد مفقود من الكيان يُضاف.
-- ============================================================


-- ════════════════════════════════════════════════════════════
-- ① providers — 22 عمود يتيم + قيود مفقودة
-- ════════════════════════════════════════════════════════════

-- أعمدة استُبدلت في V29 (البيانات نُسخت للأعمدة الجديدة):
--   provider_name → name
--   contact_email → email
--   contact_phone → phone
--   tax_company_code → tax_number
--   provider_status → network_status
ALTER TABLE providers DROP COLUMN IF EXISTS provider_name;
ALTER TABLE providers DROP COLUMN IF EXISTS provider_name_ar;
ALTER TABLE providers DROP COLUMN IF EXISTS contact_person;
ALTER TABLE providers DROP COLUMN IF EXISTS contact_email;
ALTER TABLE providers DROP COLUMN IF EXISTS contact_phone;
ALTER TABLE providers DROP COLUMN IF EXISTS tax_company_code;
ALTER TABLE providers DROP COLUMN IF EXISTS provider_status;

-- أعمدة يتيمة بدون أي حقل في Provider.java
ALTER TABLE providers DROP COLUMN IF EXISTS region;
ALTER TABLE providers DROP COLUMN IF EXISTS bank_name;
ALTER TABLE providers DROP COLUMN IF EXISTS bank_account_number;
ALTER TABLE providers DROP COLUMN IF EXISTS iban;
ALTER TABLE providers DROP COLUMN IF EXISTS principal_name;
ALTER TABLE providers DROP COLUMN IF EXISTS principal_phone;
ALTER TABLE providers DROP COLUMN IF EXISTS principal_email;
ALTER TABLE providers DROP COLUMN IF EXISTS principal_mobile;
ALTER TABLE providers DROP COLUMN IF EXISTS principal_address;
ALTER TABLE providers DROP COLUMN IF EXISTS secondary_contact;
ALTER TABLE providers DROP COLUMN IF EXISTS secondary_contact_phone;
ALTER TABLE providers DROP COLUMN IF EXISTS secondary_contact_email;
ALTER TABLE providers DROP COLUMN IF EXISTS accounting_person;
ALTER TABLE providers DROP COLUMN IF EXISTS accounting_phone;
ALTER TABLE providers DROP COLUMN IF EXISTS accounting_email;

-- إصلاح NOT NULL المفقود:
--   Provider.active: @Column(nullable=false) — V3: DEFAULT true بدون NOT NULL
UPDATE providers SET active = true WHERE active IS NULL;
ALTER TABLE providers ALTER COLUMN active SET NOT NULL;

--   Provider.allowAllEmployers: @Column(nullable=false) — V3: DEFAULT false بدون NOT NULL
UPDATE providers SET allow_all_employers = false WHERE allow_all_employers IS NULL;
ALTER TABLE providers ALTER COLUMN allow_all_employers SET NOT NULL;

-- إضافة CHECK على network_status (الكيان: NetworkTier enum)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'providers'
          AND constraint_name = 'chk_provider_network_status'
    ) THEN
        ALTER TABLE providers ADD CONSTRAINT chk_provider_network_status
            CHECK (network_status IS NULL OR network_status IN ('IN_NETWORK','OUT_OF_NETWORK','PREFERRED'));
    END IF;
END $$;


-- ════════════════════════════════════════════════════════════
-- ② provider_admin_documents — 4 أعمدة مستبدلة/يتيمة
-- ════════════════════════════════════════════════════════════

-- أعمدة استُبدلت في V32 (البيانات نُسخت):
--   document_type → type
--   document_name → file_name
ALTER TABLE provider_admin_documents DROP COLUMN IF EXISTS document_type;
ALTER TABLE provider_admin_documents DROP COLUMN IF EXISTS document_name;

-- أعمدة يتيمة
ALTER TABLE provider_admin_documents DROP COLUMN IF EXISTS file_size;
ALTER TABLE provider_admin_documents DROP COLUMN IF EXISTS uploaded_by;


-- ════════════════════════════════════════════════════════════
-- ③ provider_contracts — أعمدة وقيود قديمة
-- ════════════════════════════════════════════════════════════

-- حذف الفهارس والقيود المرتبطة بالأعمدة القديمة
DROP INDEX IF EXISTS uq_active_contract_per_provider;
DROP INDEX IF EXISTS idx_contracts_status;
DROP INDEX IF EXISTS idx_contracts_employer;

ALTER TABLE provider_contracts DROP CONSTRAINT IF EXISTS fk_contract_employer;
ALTER TABLE provider_contracts DROP CONSTRAINT IF EXISTS chk_contract_dates;

-- أعمدة استُبدلت في V30:
--   contract_start_date → start_date
--   contract_end_date   → end_date
--   contract_status     → status
ALTER TABLE provider_contracts DROP COLUMN IF EXISTS contract_start_date;
ALTER TABLE provider_contracts DROP COLUMN IF EXISTS contract_end_date;
ALTER TABLE provider_contracts DROP COLUMN IF EXISTS contract_status;

-- أعمدة يتيمة
ALTER TABLE provider_contracts DROP COLUMN IF EXISTS max_sessions_per_service;
ALTER TABLE provider_contracts DROP COLUMN IF EXISTS requires_preauthorization;
ALTER TABLE provider_contracts DROP COLUMN IF EXISTS employer_id;

-- إعادة إنشاء الفهارس بالأعمدة الحالية
CREATE INDEX IF NOT EXISTS idx_contracts_status
    ON provider_contracts(status);

CREATE UNIQUE INDEX IF NOT EXISTS uq_active_contract_per_provider
    ON provider_contracts(provider_id)
    WHERE status = 'ACTIVE' AND active = true;

-- إضافة CHECK constraints مفقودة
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'provider_contracts'
          AND constraint_name = 'chk_provider_contract_status'
    ) THEN
        ALTER TABLE provider_contracts ADD CONSTRAINT chk_provider_contract_status
            CHECK (status IN ('DRAFT','ACTIVE','SUSPENDED','EXPIRED','TERMINATED'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'provider_contracts'
          AND constraint_name = 'chk_provider_contract_pricing'
    ) THEN
        ALTER TABLE provider_contracts ADD CONSTRAINT chk_provider_contract_pricing
            CHECK (pricing_model IN ('FIXED','DISCOUNT','TIERED','NEGOTIATED'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'provider_contracts'
          AND constraint_name = 'chk_provider_contract_dates'
    ) THEN
        ALTER TABLE provider_contracts ADD CONSTRAINT chk_provider_contract_dates
            CHECK (end_date IS NULL OR end_date >= start_date);
    END IF;
END $$;


-- ════════════════════════════════════════════════════════════
-- ④ employers — عمود مكرر
-- ════════════════════════════════════════════════════════════

-- commercial_registration_number مكرر مع cr_number
ALTER TABLE employers DROP COLUMN IF EXISTS commercial_registration_number;


-- ════════════════════════════════════════════════════════════
-- ⑤ benefit_policies — أعمدة يتيمة + إصلاح نوع البيانات
-- ════════════════════════════════════════════════════════════

-- حذف القيد القديم الذي يشير لأعمدة سنحذفها
ALTER TABLE benefit_policies DROP CONSTRAINT IF EXISTS chk_policy_dates;

-- أعمدة يتيمة بدون حقل في BenefitPolicy.java
ALTER TABLE benefit_policies DROP COLUMN IF EXISTS per_visit_limit;
ALTER TABLE benefit_policies DROP COLUMN IF EXISTS deductible_amount;
ALTER TABLE benefit_policies DROP COLUMN IF EXISTS copay_percentage;
ALTER TABLE benefit_policies DROP COLUMN IF EXISTS policy_type;
ALTER TABLE benefit_policies DROP COLUMN IF EXISTS effective_date;
ALTER TABLE benefit_policies DROP COLUMN IF EXISTS expiry_date;

-- إصلاح حجم annual_limit: V11 أنشأه NUMERIC(12,2) — الكيان precision=15
ALTER TABLE benefit_policies ALTER COLUMN annual_limit TYPE NUMERIC(15,2);

-- إضافة NOT NULL على active:
--   BenefitPolicy.active: @Column(nullable=false) — V11 فقط DEFAULT true
UPDATE benefit_policies SET active = true WHERE active IS NULL;
ALTER TABLE benefit_policies ALTER COLUMN active SET NOT NULL;

-- CHECK لنطاق التواريخ (يمنع end_date < start_date)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'benefit_policies'
          AND constraint_name = 'chk_policy_date_range'
    ) THEN
        ALTER TABLE benefit_policies ADD CONSTRAINT chk_policy_date_range
            CHECK (end_date >= start_date);
    END IF;
END $$;


-- ════════════════════════════════════════════════════════════
-- ⑥ benefit_policy_rules — 6 أعمدة يتيمة + قيد فريد مفقود
-- ════════════════════════════════════════════════════════════

-- أعمدة استُبدلت أو لم تعد تُستخدم بعد V228:
--   service_category   → أُلغيت (قواعد مستوى الخدمة حُذفت)
--   coverage_percentage → مكررة مع coverage_percent (نوع مختلف)
--   max_sessions_per_year → مكرر مع times_limit
--   requires_preauth   → مكرر مع requires_pre_approval
--   max_amount_per_session → يتيم
--   max_amount_per_year → يتيم
ALTER TABLE benefit_policy_rules DROP COLUMN IF EXISTS service_category;
ALTER TABLE benefit_policy_rules DROP COLUMN IF EXISTS coverage_percentage;
ALTER TABLE benefit_policy_rules DROP COLUMN IF EXISTS max_sessions_per_year;
ALTER TABLE benefit_policy_rules DROP COLUMN IF EXISTS requires_preauth;
ALTER TABLE benefit_policy_rules DROP COLUMN IF EXISTS max_amount_per_session;
ALTER TABLE benefit_policy_rules DROP COLUMN IF EXISTS max_amount_per_year;

-- إضافة القيد الفريد المفقود من الكيان:
--   @UniqueConstraint(name = "uk_bpr_policy_category",
--                     columnNames = {"benefit_policy_id","medical_category_id"})
-- حذف التكرارات أولاً (الاحتفاظ بالأحدث لكل زوج)
DELETE FROM benefit_policy_rules a
    USING benefit_policy_rules b
    WHERE a.benefit_policy_id = b.benefit_policy_id
      AND a.medical_category_id IS NOT DISTINCT FROM b.medical_category_id
      AND a.id < b.id;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'benefit_policy_rules'
          AND constraint_name = 'uk_bpr_policy_category'
    ) THEN
        ALTER TABLE benefit_policy_rules
            ADD CONSTRAINT uk_bpr_policy_category
            UNIQUE (benefit_policy_id, medical_category_id);
    END IF;
END $$;
