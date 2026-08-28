-- ============================================================================
-- بيانات اختبار متصفح لنافذة سقوف المستفيدين
--
-- تزرع الحالات الأربع المحجوبة في دورة المتصفح: حجز نشط، تجاوز السقف،
-- وثيقة بلا سقف، وحساب EMPLOYER_ADMIN من جهة أخرى.
--
-- كل صف يحمل البادئة 'UAT-CEIL-' ليمكن حذفه كاملاً بقسم التنظيف في الأسفل.
-- لا يلمس هذا السكربت أي صف قائم.
--
-- التشغيل: على قاعدة بيانات تطوير فقط، وبإذن صريح في كل مرة.
--   psql -v ON_ERROR_STOP=1 -f docs/CEILING_UAT_FIXTURES.sql
-- ============================================================================

BEGIN;

-- ── جهتا عمل ووثائقهما ──────────────────────────────────────────────────────
WITH e AS (
    INSERT INTO employers (name, code, active)
    VALUES ('UAT-CEIL جهة أ', 'UAT-CEIL-A', true),
           ('UAT-CEIL جهة ب', 'UAT-CEIL-B', true)
    RETURNING id, code
)
INSERT INTO benefit_policies (name, policy_code, employer_id, start_date, end_date,
                              annual_limit, default_coverage_percent, status, active)
SELECT 'UAT-CEIL وثيقة ' || e.code, 'UAT-CEIL-POL-' || e.code, e.id,
       DATE_TRUNC('year', CURRENT_DATE)::date,
       (DATE_TRUNC('year', CURRENT_DATE) + INTERVAL '1 year - 1 day')::date,
       60000.00, 80, 'ACTIVE', true
FROM e;

-- وثيقة بلا سقف: annual_limit عمود NOT NULL منذ V33، و«بلا سقف» تُكتب صفراً
INSERT INTO benefit_policies (name, policy_code, employer_id, start_date, end_date,
                              annual_limit, default_coverage_percent, status, active)
SELECT 'UAT-CEIL وثيقة بلا سقف', 'UAT-CEIL-POL-NOLIMIT', id,
       DATE_TRUNC('year', CURRENT_DATE)::date,
       (DATE_TRUNC('year', CURRENT_DATE) + INTERVAL '1 year - 1 day')::date,
       0.00, 80, 'ACTIVE', true
FROM employers WHERE code = 'UAT-CEIL-A';

-- ── ثلاثة مستفيدين: حجز نشط، تجاوز، بلا سقف ────────────────────────────────
INSERT INTO members (full_name, card_number, employer_id, benefit_policy_id, status, active)
SELECT v.label, v.card, p.employer_id, p.id, 'ACTIVE', true
FROM (VALUES
        ('UAT-CEIL محجوز',  'UAT-CEIL-RESERVED', 'UAT-CEIL-POL-UAT-CEIL-A'),
        ('UAT-CEIL متجاوز', 'UAT-CEIL-EXCEEDED', 'UAT-CEIL-POL-UAT-CEIL-A'),
        ('UAT-CEIL بلا سقف','UAT-CEIL-NOLIMIT',  'UAT-CEIL-POL-NOLIMIT')
     ) AS v(label, card, policy_code)
JOIN benefit_policies p ON p.policy_code = v.policy_code;

-- التعيينات المؤرَّخة. بدونها يقرأ النظام الأعضاء كـ NOT_CONFIGURED، لأن
-- المؤشر القديم benefit_policy_id للعرض فقط ولا يُبنى عليه قرار.
INSERT INTO member_policy_assignments (member_id, policy_id, assignment_start_date, assignment_source)
SELECT m.id, m.benefit_policy_id, CURRENT_DATE - 60, 'MANUAL'
FROM members m WHERE m.card_number LIKE 'UAT-CEIL-%';

INSERT INTO member_employer_assignments (member_id, employer_id, assignment_start_date,
                                         assignment_reason, assignment_source)
SELECT m.id, m.employer_id, CURRENT_DATE - 60, 'UAT-CEIL fixture', 'MANUAL'
FROM members m WHERE m.card_number LIKE 'UAT-CEIL-%';

-- ── الحركات ────────────────────────────────────────────────────────────────
INSERT INTO member_opening_balance_batches (batch_reference, reason, performed_by, source_reference)
VALUES ('UAT-CEIL-BATCH', 'رصيد افتتاحي لاختبار المتصفح', 'uat', 'UAT-CEIL');

-- المستفيد «محجوز»: مستهلك 10,000 ومحجوز 5,000
--   المتوقع في العمود: المتاح 45,000 · المتبقي الفعلي 50,000
INSERT INTO benefit_bucket_consumptions (policy_id, member_id, period_start, period_end,
        approved_amount, times_consumed, calculation_version, idempotency_key,
        status, source_type, limit_scope, opening_batch_id, created_at)
SELECT m.benefit_policy_id, m.id,
       DATE_TRUNC('year', CURRENT_DATE)::date,
       (DATE_TRUNC('year', CURRENT_DATE) + INTERVAL '1 year - 1 day')::date,
       10000.00, 0, 1, 'UAT-CEIL-C-RESERVED', 'COMMITTED', 'OPENING_IMPORT', 'POLICY_GENERAL',
       b.id, now()
FROM members m, member_opening_balance_batches b
WHERE m.card_number = 'UAT-CEIL-RESERVED' AND b.batch_reference = 'UAT-CEIL-BATCH';

INSERT INTO pre_authorizations (member_id, policy_id, status, request_date, created_at, updated_at)
SELECT m.id, m.benefit_policy_id, 'APPROVED', now(), now(), now()
FROM members m WHERE m.card_number = 'UAT-CEIL-RESERVED';

INSERT INTO pre_authorization_lines (pre_authorization_id, requested_amount)
SELECT pa.id, 5000.00
FROM pre_authorizations pa
JOIN members m ON m.id = pa.member_id
WHERE m.card_number = 'UAT-CEIL-RESERVED';

INSERT INTO benefit_bucket_consumptions (policy_id, member_id, preauth_id, preauth_line_id,
        member_policy_assignment_id, period_start, period_end, approved_amount, times_consumed,
        calculation_version, idempotency_key, status, source_type, limit_scope, created_at)
SELECT m.benefit_policy_id, m.id, pa.id, pl.id, mpa.id,
       DATE_TRUNC('year', CURRENT_DATE)::date,
       (DATE_TRUNC('year', CURRENT_DATE) + INTERVAL '1 year - 1 day')::date,
       5000.00, 0, 1, 'UAT-CEIL-R-RESERVED', 'RESERVED', 'PREAUTH', 'POLICY_GENERAL', now()
FROM members m
JOIN pre_authorizations pa ON pa.member_id = m.id
JOIN pre_authorization_lines pl ON pl.pre_authorization_id = pa.id
JOIN member_policy_assignments mpa ON mpa.member_id = m.id
WHERE m.card_number = 'UAT-CEIL-RESERVED';

-- المستفيد «متجاوز»: مستهلك 65,000 من سقف 60,000
--   المتوقع: المتاح -5,000 مع تنبيه تجاوز، بلا قصّ إلى صفر
INSERT INTO benefit_bucket_consumptions (policy_id, member_id, period_start, period_end,
        approved_amount, times_consumed, calculation_version, idempotency_key,
        status, source_type, limit_scope, opening_batch_id, created_at)
SELECT m.benefit_policy_id, m.id,
       DATE_TRUNC('year', CURRENT_DATE)::date,
       (DATE_TRUNC('year', CURRENT_DATE) + INTERVAL '1 year - 1 day')::date,
       65000.00, 0, 1, 'UAT-CEIL-C-EXCEEDED', 'COMMITTED', 'OPENING_IMPORT', 'POLICY_GENERAL',
       b.id, now()
FROM members m, member_opening_balance_batches b
WHERE m.card_number = 'UAT-CEIL-EXCEEDED' AND b.batch_reference = 'UAT-CEIL-BATCH';

-- المستفيد «بلا سقف» لا يحتاج حركات: المتوقع «بلا سقف» بلا شريط ولا نسبة.

COMMIT;

-- ============================================================================
-- حسابا الاختبار: EMPLOYER_ADMIN على «جهة ب» لاختبار الرفض الكامل، وDATA_ENTRY
-- لاختبار اختفاء العمود.
--
-- كلمة المرور منسوخة من hash حساب superadmin القائم في نفس القاعدة، فلا يدخل
-- المستودعَ أي hash ثابت يصير بيانات اعتماد معروفة تنتقل مع الكود. أي أن
-- كلمة مرورهما هي كلمة مرور المشرف في تلك البيئة.
-- ============================================================================
INSERT INTO users (username, email, password, full_name, user_type, employer_id,
                   created_at, updated_at, authorization_version)
SELECT 'uat-ceil-admin-b', 'uat-ceil-admin-b@waad.ly', u.password,
       'UAT-CEIL Admin B', 'EMPLOYER_ADMIN', e.id, now(), now(), 1
FROM users u, employers e
WHERE u.username = 'superadmin' AND e.code = 'UAT-CEIL-B'
ON CONFLICT (username) DO NOTHING;

INSERT INTO users (username, email, password, full_name, user_type, employer_id,
                   created_at, updated_at, authorization_version)
SELECT 'uat-ceil-entry', 'uat-ceil-entry@waad.ly', u.password,
       'UAT-CEIL Data Entry', 'DATA_ENTRY', e.id, now(), now(), 1
FROM users u, employers e
WHERE u.username = 'superadmin' AND e.code = 'UAT-CEIL-A'
ON CONFLICT (username) DO NOTHING;

-- ============================================================================
-- التنظيف — يحذف ما زرعه هذا السكربت فقط
-- ============================================================================
-- BEGIN;
-- DELETE FROM benefit_bucket_consumptions WHERE idempotency_key LIKE 'UAT-CEIL-%';
-- DELETE FROM pre_authorization_lines WHERE pre_authorization_id IN (
--     SELECT pa.id FROM pre_authorizations pa JOIN members m ON m.id = pa.member_id
--     WHERE m.card_number LIKE 'UAT-CEIL-%');
-- DELETE FROM pre_authorizations WHERE member_id IN (
--     SELECT id FROM members WHERE card_number LIKE 'UAT-CEIL-%');
-- DELETE FROM member_policy_assignments WHERE member_id IN (
--     SELECT id FROM members WHERE card_number LIKE 'UAT-CEIL-%');
-- DELETE FROM member_employer_assignments WHERE member_id IN (
--     SELECT id FROM members WHERE card_number LIKE 'UAT-CEIL-%');
-- DELETE FROM members WHERE card_number LIKE 'UAT-CEIL-%';
-- DELETE FROM member_opening_balance_batches WHERE batch_reference = 'UAT-CEIL-BATCH';
-- DELETE FROM users WHERE username LIKE 'uat-ceil-%';
-- DELETE FROM benefit_policies WHERE policy_code LIKE 'UAT-CEIL-%';
-- DELETE FROM employers WHERE code LIKE 'UAT-CEIL-%';
-- COMMIT;
