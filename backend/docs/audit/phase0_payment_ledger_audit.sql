-- ═══════════════════════════════════════════════════════════════════════════════
-- المرحلة صفر — تدقيق الإنتاج قبل إعادة نمذجة الدفعات
--
-- قراءة فقط. لا INSERT / UPDATE / DELETE / ALTER. آمن على الإنتاج.
--
-- السبب: تبيّن من الكود أن مسارَي الدفع منفصلان بنيوياً:
--   • PaymentService.addPayment()  ← يُنشئ payment_records فقط. تبعياته الوحيدة
--     PaymentRecordRepository و PaymentAuditLogRepository — لا يملك أي مرجع
--     لحساب المزود، فهو عاجز عن تحديثه.
--   • ProviderAccountService.debitOnInstallmentPayment() ← يُحدّث الدفتر ولا
--     يُنشئ مستند دفع.
-- فالنتيجة: totalPaid ≠ SUM(payment_records)، وقد تُسجَّل الحوالة الواحدة مرتين.
--
-- هذا التدقيق يقيس الضرر الفعلي ليقرر: إصلاح كود فقط، أم كود + هجرة تصحيح بيانات.
--
-- ─────────────────────────────────────────────────────────────────────────────
-- التشغيل
-- ─────────────────────────────────────────────────────────────────────────────
-- على قاعدة محلية (psql مباشرة — عدّل اسم المستخدم/القاعدة عند الحاجة):
--
--   psql -v ON_ERROR_STOP=1 -U postgres -d tba_waad_system \
--     -f backend/docs/audit/phase0_payment_ledger_audit.sql \
--     | tee "phase0_payment_audit_$(date +%F_%H%M%S).txt"
--
-- على الإنتاج (الملف على المضيف لا داخل الحاوية، لذا يُمرَّر عبر stdin):
--
--   cd /opt/waadapp
--   docker compose exec -T db \
--     psql -v ON_ERROR_STOP=1 -U postgres -d tba_waad_system \
--     < backend/docs/audit/phase0_payment_ledger_audit.sql \
--     | tee "phase0_payment_audit_$(date +%F_%H%M%S).txt"
--
-- ثم تحقق من رمز الخروج:  echo $?   ← يجب أن يكون 0
--
-- ملاحظة: -f داخل الحاوية لا يعمل لأن الملف غير موجود في نظام ملفاتها.
-- ─────────────────────────────────────────────────────────────────────────────

\set ON_ERROR_STOP on
\pset pager off
\timing off

-- الأمان مفروض من PostgreSQL نفسه، لا مجرد افتراض بأن الاستعلامات لا تكتب:
--   • READ ONLY  → أي محاولة كتابة تفشل بخطأ من المحرك
--   • ROLLBACK   → لا شيء يُثبَّت حتى لو حدث ما لم يُتوقَّع
--   • timeouts   → لا استعلام أو قفل يعلّق الإنتاج
SET statement_timeout = '60s';
SET lock_timeout = '5s';
BEGIN TRANSACTION READ ONLY;

\echo '════════════════════════════════════════════════════════════════'
\echo ' [1] حجم الاستخدام — هل استُخدم مسار الدفعات إنتاجياً أصلاً؟'
\echo '════════════════════════════════════════════════════════════════'
SELECT COUNT(*)                                                   AS total_rows,
       COUNT(*) FILTER (WHERE NOT is_deleted)                     AS active_rows,
       COUNT(*) FILTER (WHERE is_deleted)                         AS deleted_rows,
       COALESCE(SUM(amount) FILTER (WHERE NOT is_deleted), 0)     AS active_amount,
       COUNT(DISTINCT provider_id)                                AS providers_touched,
       MIN(payment_date)                                          AS first_payment,
       MAX(payment_date)                                          AS last_payment
FROM payment_records;

\echo ''
\echo '════════════════════════════════════════════════════════════════'
\echo ' [2] المطابقة الثلاثية لكل مزود — المستند / الدفتر / الرصيد'
\echo '════════════════════════════════════════════════════════════════'
\echo ' doc_sum      = مجموع مستندات الدفع (payment_records النشطة)'
\echo ' ledger_net   = صافي قيود الدفتر من نوع SETTLEMENT_PAYMENT (مدين - دائن)'
\echo ' total_paid   = الرصيد التراكمي المخزّن في provider_accounts'
\echo ' يجب أن تتساوى الثلاثة. أي فرق هو انكشاف فعلي.'
\echo ''
WITH doc AS (
    SELECT provider_id, COALESCE(SUM(amount), 0) AS doc_sum, COUNT(*) AS doc_count
    FROM payment_records WHERE NOT is_deleted GROUP BY provider_id
),
ledger AS (
    SELECT pa.provider_id,
           COALESCE(SUM(CASE WHEN at.transaction_type = 'DEBIT'  THEN at.amount
                             WHEN at.transaction_type = 'CREDIT' THEN -at.amount END), 0) AS ledger_net,
           COUNT(*) AS ledger_count
    FROM account_transactions at
    JOIN provider_accounts pa ON pa.id = at.provider_account_id
    WHERE at.reference_type = 'SETTLEMENT_PAYMENT'
    GROUP BY pa.provider_id
),
acct AS (
    SELECT provider_id, total_paid, total_approved, running_balance FROM provider_accounts
)
SELECT COALESCE(d.provider_id, l.provider_id, a.provider_id) AS provider_id,
       p.name                                                AS provider_name,
       COALESCE(d.doc_count, 0)                              AS doc_count,
       COALESCE(d.doc_sum, 0)                                AS doc_sum,
       COALESCE(l.ledger_count, 0)                           AS ledger_count,
       COALESCE(l.ledger_net, 0)                             AS ledger_net,
       COALESCE(a.total_paid, 0)                             AS total_paid,
       COALESCE(d.doc_sum, 0) - COALESCE(a.total_paid, 0)    AS gap_doc_vs_paid,
       COALESCE(l.ledger_net, 0) - COALESCE(a.total_paid, 0) AS gap_ledger_vs_paid
FROM doc d
FULL OUTER JOIN ledger l ON l.provider_id = d.provider_id
FULL OUTER JOIN acct   a ON a.provider_id = COALESCE(d.provider_id, l.provider_id)
LEFT JOIN providers p ON p.id = COALESCE(d.provider_id, l.provider_id, a.provider_id)
WHERE COALESCE(d.doc_sum, 0) <> COALESCE(a.total_paid, 0)
   OR COALESCE(l.ledger_net, 0) <> COALESCE(a.total_paid, 0)
ORDER BY ABS(COALESCE(d.doc_sum, 0) - COALESCE(a.total_paid, 0)) DESC;

\echo ''
\echo '════════════════════════════════════════════════════════════════'
\echo ' [3] الإجمالي الكلي — حجم المال غير المرحَّل'
\echo '════════════════════════════════════════════════════════════════'
SELECT (SELECT COALESCE(SUM(amount), 0) FROM payment_records WHERE NOT is_deleted) AS all_documents,
       (SELECT COALESCE(SUM(CASE WHEN transaction_type = 'DEBIT' THEN amount ELSE -amount END), 0)
          FROM account_transactions WHERE reference_type = 'SETTLEMENT_PAYMENT')   AS all_ledger_net,
       (SELECT COALESCE(SUM(total_paid), 0) FROM provider_accounts)                AS all_total_paid;

\echo ''
\echo '════════════════════════════════════════════════════════════════'
\echo ' [4] مستند بلا قيد دفتر — دفعات لم تصل الحساب إطلاقاً'
\echo '════════════════════════════════════════════════════════════════'
\echo ' هذه الحالة المتوقعة لكل ما أُدخل من شاشة (إدارة الدفعات والتسديدات).'
\echo ''
SELECT pr.provider_id, p.name AS provider_name,
       COUNT(*) AS orphan_doc_count, SUM(pr.amount) AS orphan_doc_amount
FROM payment_records pr
LEFT JOIN providers p ON p.id = pr.provider_id
WHERE NOT pr.is_deleted
  AND NOT EXISTS (
      SELECT 1 FROM account_transactions at
      JOIN provider_accounts pa ON pa.id = at.provider_account_id
      WHERE pa.provider_id = pr.provider_id
        AND at.reference_type = 'SETTLEMENT_PAYMENT'
  )
GROUP BY pr.provider_id, p.name
ORDER BY orphan_doc_amount DESC;

\echo ''
\echo '════════════════════════════════════════════════════════════════'
\echo ' [5] قيد دفتر بلا مستند — خصم من الحساب بلا سجل دفع'
\echo '════════════════════════════════════════════════════════════════'
SELECT pa.provider_id, p.name AS provider_name,
       COUNT(*) AS ledger_entry_count, SUM(at.amount) AS ledger_amount,
       MIN(at.transaction_date) AS first_entry, MAX(at.transaction_date) AS last_entry
FROM account_transactions at
JOIN provider_accounts pa ON pa.id = at.provider_account_id
LEFT JOIN providers p ON p.id = pa.provider_id
WHERE at.reference_type = 'SETTLEMENT_PAYMENT'
  AND NOT EXISTS (
      SELECT 1 FROM payment_records pr
      WHERE pr.provider_id = pa.provider_id AND NOT pr.is_deleted
  )
GROUP BY pa.provider_id, p.name
ORDER BY ledger_amount DESC;

\echo ''
\echo '════════════════════════════════════════════════════════════════'
\echo ' [6] اشتباه التفتيت — حوالة واحدة سُجّلت كعدة صفوف'
\echo '════════════════════════════════════════════════════════════════'
\echo ' التشابه لا يُثبت أنها حوالة واحدة. هذه قائمة مراجعة بشرية فقط،'
\echo ' ولا يجوز الدمج الآلي بناءً عليها وحدها.'
\echo ''
SELECT provider_id, payment_date, payment_method,
       COALESCE(reference_number, '(بلا مرجع)') AS reference_number,
       COUNT(*)      AS split_count,
       SUM(amount)   AS combined_amount,
       ARRAY_AGG(id ORDER BY id)          AS payment_ids,
       ARRAY_AGG(DISTINCT employer_id)    AS employers,
       CASE WHEN reference_number IS NOT NULL AND reference_number <> '' THEN 'مرتفعة'
            ELSE 'منخفضة' END             AS confidence
FROM payment_records
WHERE NOT is_deleted
GROUP BY provider_id, payment_date, payment_method, reference_number
HAVING COUNT(*) > 1
ORDER BY confidence DESC, split_count DESC, combined_amount DESC;

\echo ''
\echo '════════════════════════════════════════════════════════════════'
\echo ' [7] سجلات بلا رقم مرجعي — لا يمكن ربطها بحوالة بنكية'
\echo '════════════════════════════════════════════════════════════════'
SELECT COUNT(*) AS rows_without_reference,
       COALESCE(SUM(amount), 0) AS amount_without_reference
FROM payment_records
WHERE NOT is_deleted AND (reference_number IS NULL OR reference_number = '');

\echo ''
\echo '════════════════════════════════════════════════════════════════'
\echo ' [8] دفعات عُدّلت أو حُذفت — هل قابلها قيد فرق أو عكس؟'
\echo '════════════════════════════════════════════════════════════════'
SELECT pal.action_type,
       COUNT(*)                        AS audit_entries,
       COUNT(DISTINCT pal.payment_id)  AS payments_affected,
       COALESCE(SUM(COALESCE(pal.new_amount, 0) - COALESCE(pal.old_amount, 0)), 0) AS net_amount_change
FROM payment_audit_logs pal
GROUP BY pal.action_type
ORDER BY pal.action_type;

\echo ''
\echo '════════════════════════════════════════════════════════════════'
\echo ' [9] سلامة الدفتر ذاته — running_balance = total_approved - total_paid'
\echo '════════════════════════════════════════════════════════════════'
SELECT pa.provider_id, p.name AS provider_name,
       pa.total_approved, pa.total_paid, pa.running_balance,
       pa.total_approved - pa.total_paid                        AS expected_balance,
       pa.running_balance - (pa.total_approved - pa.total_paid) AS drift
FROM provider_accounts pa
LEFT JOIN providers p ON p.id = pa.provider_id
WHERE pa.running_balance <> pa.total_approved - pa.total_paid
ORDER BY ABS(pa.running_balance - (pa.total_approved - pa.total_paid)) DESC;

\echo ''
\echo '════════════════════════════════════════════════════════════════'
\echo ' [10] دفع زائد — مدفوع يتجاوز المعتمد (رصيد دائن مخفي)'
\echo '════════════════════════════════════════════════════════════════'
SELECT pa.provider_id, p.name AS provider_name,
       pa.total_approved, pa.total_paid,
       pa.total_paid - pa.total_approved AS overpaid_amount
FROM provider_accounts pa
LEFT JOIN providers p ON p.id = pa.provider_id
WHERE pa.total_paid > pa.total_approved
ORDER BY (pa.total_paid - pa.total_approved) DESC;

\echo ''
\echo '════════════════════════════════════════════════════════════════'
\echo ' [11] دقة الأرقام — كسور تحت المنزلتين قبل توحيد NUMERIC(15,2)'
\echo '════════════════════════════════════════════════════════════════'
\echo ' لا يجوز تقريب بيانات الإنتاج بصمت. أي صف هنا يجب أن يُقرَّر يدوياً.'
\echo ''
SELECT 'payment_records.amount' AS source, COUNT(*) AS rows_with_sub_cent
FROM payment_records WHERE amount <> ROUND(amount, 2)
UNION ALL
SELECT 'payment_audit_logs.old_amount', COUNT(*)
FROM payment_audit_logs WHERE old_amount IS NOT NULL AND old_amount <> ROUND(old_amount, 2)
UNION ALL
SELECT 'payment_audit_logs.new_amount', COUNT(*)
FROM payment_audit_logs WHERE new_amount IS NOT NULL AND new_amount <> ROUND(new_amount, 2)
UNION ALL
SELECT 'account_transactions.amount', COUNT(*)
FROM account_transactions WHERE amount <> ROUND(amount, 2);

\echo ''
\echo '════════════════════════════════════════════════════════════════'
\echo ' [12] دفعات لمزودين بلا حساب — لا يمكن ترحيلها للدفتر'
\echo '════════════════════════════════════════════════════════════════'
SELECT pr.provider_id, p.name AS provider_name,
       COUNT(*) AS payment_count, SUM(pr.amount) AS payment_amount
FROM payment_records pr
LEFT JOIN providers p ON p.id = pr.provider_id
WHERE NOT pr.is_deleted
  AND NOT EXISTS (SELECT 1 FROM provider_accounts pa WHERE pa.provider_id = pr.provider_id)
GROUP BY pr.provider_id, p.name
ORDER BY payment_amount DESC;

ROLLBACK;

\echo ''
\echo '════════════════════════════════════════════════════════════════'
\echo ' انتهى التدقيق — المعاملة READ ONLY أُلغيت (ROLLBACK).'
\echo ' لم تُعدَّل أي بيانات، ولا يمكن أن تُعدَّل بحكم قيد المحرك نفسه.'
\echo '════════════════════════════════════════════════════════════════'
