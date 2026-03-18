-- ============================================================
-- V33: تنظيف شامل لثلاثة جداول بالاعتماد على الكيانات
--
-- الجداول المُعالجة:
--   ① benefit_policies      (V11 ↔ BenefitPolicy.java)
--   ② benefit_policy_rules  (V11 ↔ BenefitPolicyRule.java)
--   ③ provider_contracts    (V12 + V30 ↔ ModernProviderContract.java)
--
-- (employers / V2 مطابق للكيان — لا إجراء مطلوب)
-- ============================================================

-- ════════════════════════════════════════════════════════════
-- ① benefit_policies
-- ════════════════════════════════════════════════════════════

-- 1a. policy_code: الكيان يسمح بـ null (@Column length=50, لا nullable=false)
--     V11 أنشأه NOT NULL UNIQUE → نحذف قيد NOT NULL
ALTER TABLE benefit_policies ALTER COLUMN policy_code DROP NOT NULL;

-- 1b. status: V11 CHECK يسمح فقط بـ ('DRAFT','ACTIVE')
--     الكيان BenefitPolicyStatus يملك: DRAFT, ACTIVE, EXPIRED, SUSPENDED, CANCELLED
--     → نحذف القيد القديم ونُعيد بناءه ليشمل جميع قيم الـ enum
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'benefit_policies'
          AND constraint_type = 'CHECK'
          AND constraint_name IN (
              SELECT constraint_name
              FROM information_schema.check_constraints
              WHERE check_clause LIKE '%status%'
                AND constraint_name IN (
                    SELECT constraint_name FROM information_schema.table_constraints
                    WHERE table_name = 'benefit_policies' AND constraint_type = 'CHECK'
                )
          )
    ) THEN
        EXECUTE (
            SELECT 'ALTER TABLE benefit_policies DROP CONSTRAINT ' || tc.constraint_name
            FROM information_schema.table_constraints tc
            JOIN information_schema.check_constraints cc
              ON tc.constraint_name = cc.constraint_name
            WHERE tc.table_name = 'benefit_policies'
              AND tc.constraint_type = 'CHECK'
              AND cc.check_clause LIKE '%status%'
            LIMIT 1
        );
    END IF;
END
$$;

ALTER TABLE benefit_policies
    ADD CONSTRAINT chk_benefit_policy_status
    CHECK (status IN ('DRAFT','ACTIVE','EXPIRED','SUSPENDED','CANCELLED'));

-- 1c. start_date: الكيان @Column(name="start_date", nullable=false) — V11: nullable
UPDATE benefit_policies
    SET start_date = CURRENT_DATE
    WHERE start_date IS NULL;

ALTER TABLE benefit_policies ALTER COLUMN start_date SET NOT NULL;

-- 1d. end_date: الكيان @Column(name="end_date", nullable=false) — V11: nullable
UPDATE benefit_policies
    SET end_date = (start_date + INTERVAL '1 year')::DATE
    WHERE end_date IS NULL;

ALTER TABLE benefit_policies ALTER COLUMN end_date SET NOT NULL;

-- 1e. annual_limit: الكيان @Column(nullable=false, precision=15, scale=2) — V11: NUMERIC(12,2) nullable
UPDATE benefit_policies
    SET annual_limit = 0.00
    WHERE annual_limit IS NULL;

ALTER TABLE benefit_policies ALTER COLUMN annual_limit SET NOT NULL;

-- 1f. default_coverage_percent: الكيان @Column(nullable=false) — V11: INTEGER DEFAULT 80 nullable
UPDATE benefit_policies
    SET default_coverage_percent = 80
    WHERE default_coverage_percent IS NULL;

ALTER TABLE benefit_policies ALTER COLUMN default_coverage_percent SET NOT NULL;

-- ════════════════════════════════════════════════════════════
-- ② benefit_policy_rules
-- ════════════════════════════════════════════════════════════

-- 2a. requires_pre_approval: الكيان @Column(nullable=false) — V11: BOOLEAN DEFAULT false (nullable)
UPDATE benefit_policy_rules
    SET requires_pre_approval = false
    WHERE requires_pre_approval IS NULL;

ALTER TABLE benefit_policy_rules ALTER COLUMN requires_pre_approval SET NOT NULL;

-- 2b. active: الكيان @Column(nullable=false) — V11: BOOLEAN DEFAULT true (nullable)
UPDATE benefit_policy_rules
    SET active = true
    WHERE active IS NULL;

ALTER TABLE benefit_policy_rules ALTER COLUMN active SET NOT NULL;

-- ════════════════════════════════════════════════════════════
-- ③ provider_contracts
-- ════════════════════════════════════════════════════════════

-- 3a. status: الكيان @Column(nullable=false, length=20) — V12: VARCHAR(20) DEFAULT 'DRAFT' nullable
UPDATE provider_contracts
    SET status = 'DRAFT'
    WHERE status IS NULL;

ALTER TABLE provider_contracts ALTER COLUMN status SET NOT NULL;
ALTER TABLE provider_contracts ALTER COLUMN status SET DEFAULT 'DRAFT';

-- 3b. active: الكيان @Column(nullable=false) — V12: BOOLEAN DEFAULT true nullable
UPDATE provider_contracts
    SET active = true
    WHERE active IS NULL;

ALTER TABLE provider_contracts ALTER COLUMN active SET NOT NULL;

-- 3c. discount_percent: الكيان @Builder.Default = BigDecimal.ZERO (يُكتب دائماً)
--     مجرد تأكيد DEFAULT لمنع null من مصادر خارجية
ALTER TABLE provider_contracts ALTER COLUMN discount_percent SET DEFAULT 0.00;
