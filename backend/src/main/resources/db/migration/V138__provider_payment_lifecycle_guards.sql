-- ═══════════════════════════════════════════════════════════════════════════════
-- حماية دورة حياة دفعة المزود: انتقالات الحالة، وعدم قابلية التاريخ للتغيير،
-- وصحة قيد الدفتر — لا مجرد وجوده.
--
-- V137 أنشأ الجداول والثوابت الأساسية، لكنه ترك أربع فجوات:
--   1. يمكن تمثيل دفعة REVERSED لم تُرحَّل أصلاً (لا يشترط posted_at ولا قيد الدفع).
--   2. لا آلة انتقال حالات: POSTED → DRAFT و REVERSED → POSTED كانا ممكنين.
--   3. رأس الدفعة وتخصيصاتها قابلان للتعديل بعد الترحيل — يغيّر تفسير حوالة
--      مُرحَّلة بلا سجل عكس ولا تدقيق.
--   4. القيد الأجنبي يُثبت وجود قيد الدفتر لا صحته: قد يكون لمزود آخر، أو بمبلغ
--      مختلف، أو باتجاه دائن، أو من نوع اعتماد مطالبة.
--
-- V137 مُلتزَم، فلا يُعدَّل ولو لم يصل الإنتاج — تعديل هجرة ملتزمة أخطر من إضافة
-- واحدة جديدة.
-- ═══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- 1) نوعا مرجع مستقلان لدفعات المزود
--
-- إعادة استخدام SETTLEMENT_PAYMENT خطر ملموس لا مجرد التباس تسمية: دلالته
-- «دفع دفعة تسوية» و reference_id فيه معرّف SettlementBatch، وفحص منع التكرار
-- القائم هو existsByReferenceTypeAndReferenceId(SETTLEMENT_PAYMENT, batchId) —
-- فدفعة مزود رقم 5 كانت ستصطدم بدفعة تسوية رقم 5.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE account_transactions
    DROP CONSTRAINT IF EXISTS account_transactions_reference_type_check;

ALTER TABLE account_transactions
    ADD CONSTRAINT account_transactions_reference_type_check
    CHECK (reference_type IN (
        'CLAIM_APPROVAL',
        'CLAIM_REVERSAL',
        'CLAIM_SETTLEMENT',
        'SETTLEMENT_PAYMENT',
        'PROVIDER_PAYMENT',
        'PROVIDER_PAYMENT_REVERSAL',
        'ADJUSTMENT'
    ));

-- ─────────────────────────────────────────────────────────────────────────────
-- 2) قيد العكس منفصل عن قيد الدفع
--
-- العكس حركة تعويضية مستقلة (append-only): لا يُعاد استخدام قيد الدفع الأصلي
-- ولا يُعدَّل. فيلزم عمود ثانٍ، وكلاهما فريد.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE provider_payments
    ADD COLUMN reversal_ledger_transaction_id BIGINT
        REFERENCES account_transactions(id) ON DELETE RESTRICT;

CREATE UNIQUE INDEX ux_provider_payments_reversal_ledger_txn
    ON provider_payments(reversal_ledger_transaction_id)
    WHERE reversal_ledger_transaction_id IS NOT NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3) تقوية حالة REVERSED
--
-- لا يمكن عكس ما لم يُرحَّل: الدفعة المعكوسة يجب أن تحمل أثر ترحيلها الأصلي
-- (قيد الدفع + توقيته) وأثر عكسها (قيد العكس + توقيته + سببه).
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE provider_payments
    DROP CONSTRAINT IF EXISTS chk_provider_payments_reversed_complete;

ALTER TABLE provider_payments
    ADD CONSTRAINT chk_provider_payments_reversed_complete CHECK (
        status <> 'REVERSED' OR (
            ledger_transaction_id IS NOT NULL
            AND posted_at IS NOT NULL
            AND posted_by IS NOT NULL
            AND reversal_ledger_transaction_id IS NOT NULL
            AND reversed_at IS NOT NULL
            AND reversed_by IS NOT NULL
            AND NULLIF(BTRIM(reversal_reason), '') IS NOT NULL
        )
    );

-- كل دفعة مرحلة يجب أن تحمل هوية طلب؛ وإلا لا يمكن ضمان أن إعادة الطلب
-- لن تنشئ حوالة وقيداً ثانيين مستقلين بالقيمة نفسها.
ALTER TABLE provider_payments
    ADD CONSTRAINT chk_provider_payments_posted_identity CHECK (
        status = 'DRAFT' OR NULLIF(BTRIM(idempotency_key), '') IS NOT NULL
    );

-- V137 كان يمنع NULL فقط؛ السبب الفارغ ليس سبباً تدقيقياً.
ALTER TABLE provider_payment_allocations
    DROP CONSTRAINT IF EXISTS chk_allocation_manual_needs_reason;
ALTER TABLE provider_payment_allocations
    ADD CONSTRAINT chk_allocation_manual_needs_reason CHECK (
        allocation_method <> 'MANUAL' OR NULLIF(BTRIM(override_reason), '') IS NOT NULL
    );

-- ما لم يُعكس لا يحمل قيد عكس.
ALTER TABLE provider_payments
    ADD CONSTRAINT chk_provider_payments_reversal_only_when_reversed CHECK (
        status = 'REVERSED' OR reversal_ledger_transaction_id IS NULL
    );

-- ─────────────────────────────────────────────────────────────────────────────
-- 4) آلة انتقال الحالات + عدم قابلية التاريخ للتغيير
--
-- الانتقالات المسموحة حصراً:  DRAFT → POSTED → REVERSED
-- وما عداها مرفوض، بما فيه العودة إلى DRAFT أو إعادة ترحيل دفعة معكوسة.
--
-- وبعد الترحيل تُجمَّد الحقول التجارية: أي تصحيح يكون بعكس الدفعة وإنشاء بديلة،
-- فيبقى الدفتر append-only ويبقى أثر التدقيق كاملاً.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION enforce_provider_payment_lifecycle()
RETURNS TRIGGER AS $$
BEGIN
    -- الانتقالات
    IF OLD.status <> NEW.status THEN
        IF NOT (
            (OLD.status = 'DRAFT'  AND NEW.status = 'POSTED')
         OR (OLD.status = 'POSTED' AND NEW.status = 'REVERSED')
        ) THEN
            RAISE EXCEPTION
                'انتقال حالة غير مسموح للدفعة %: % ← %. المسموح: DRAFT ← POSTED ← REVERSED فقط.',
                NEW.id, OLD.status, NEW.status;
        END IF;
    END IF;

    -- تجميد الحقول التجارية بعد الترحيل
    IF OLD.status IN ('POSTED', 'REVERSED') THEN
        IF NEW.provider_id      IS DISTINCT FROM OLD.provider_id
        OR NEW.amount           IS DISTINCT FROM OLD.amount
        OR NEW.payment_date     IS DISTINCT FROM OLD.payment_date
        OR NEW.payment_method   IS DISTINCT FROM OLD.payment_method
        OR NEW.reference_number IS DISTINCT FROM OLD.reference_number
        OR NEW.idempotency_key  IS DISTINCT FROM OLD.idempotency_key
        OR NEW.notes            IS DISTINCT FROM OLD.notes
        OR NEW.attachment_path  IS DISTINCT FROM OLD.attachment_path THEN
            RAISE EXCEPTION
                'لا يمكن تعديل بيانات دفعة % في حالة % — اعكسها وأنشئ دفعة بديلة.',
                NEW.id, OLD.status;
        END IF;

        -- قيد الدفع الأصلي وتوقيته لا يُعاد كتابتهما بعد الترحيل
        IF NEW.ledger_transaction_id IS DISTINCT FROM OLD.ledger_transaction_id
        OR NEW.posted_at             IS DISTINCT FROM OLD.posted_at
        OR NEW.posted_by             IS DISTINCT FROM OLD.posted_by THEN
            RAISE EXCEPTION
                'لا يمكن تغيير قيد الدفتر أو توقيت ترحيل الدفعة % بعد الترحيل.', NEW.id;
        END IF;
    END IF;

    -- بيانات العكس نهائية بمجرد تسجيلها
    IF OLD.status = 'REVERSED' THEN
        IF NEW.reversal_ledger_transaction_id IS DISTINCT FROM OLD.reversal_ledger_transaction_id
        OR NEW.reversed_at                    IS DISTINCT FROM OLD.reversed_at
        OR NEW.reversed_by                    IS DISTINCT FROM OLD.reversed_by
        OR NEW.reversal_reason                IS DISTINCT FROM OLD.reversal_reason THEN
            RAISE EXCEPTION 'لا يمكن تعديل بيانات عكس الدفعة % بعد تسجيلها.', NEW.id;
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_provider_payment_lifecycle
    BEFORE UPDATE ON provider_payments
    FOR EACH ROW EXECUTE FUNCTION enforce_provider_payment_lifecycle();

-- الدفعة المُرحَّلة لا تُحذف: أثرها في الدفتر قائم.
CREATE OR REPLACE FUNCTION prevent_posted_payment_delete()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status IN ('POSTED', 'REVERSED') THEN
        RAISE EXCEPTION
            'لا يمكن حذف دفعة في حالة % — العكس هو الإجراء الصحيح.', OLD.status;
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_posted_payment_delete
    BEFORE DELETE ON provider_payments
    FOR EACH ROW EXECUTE FUNCTION prevent_posted_payment_delete();

-- ─────────────────────────────────────────────────────────────────────────────
-- 5) تجميد التخصيصات بعد الترحيل
--
-- التخصيص تفسير الحوالة. تعديله بعد الترحيل يغيّر معنى مال تحرّك فعلاً.
-- العقد الناتج: خدمة الترحيل تُنهي التخصيصات ثم تُبدّل الحالة، لا العكس.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION enforce_allocation_immutable_after_post()
RETURNS TRIGGER AS $$
DECLARE
    parent_status VARCHAR(20);
    parent_id     BIGINT;
BEGIN
    -- في UPDATE قد يُنقل الصف من دفعة إلى أخرى. يجب فحص الأب القديم أولاً؛
    -- ففحص NEW فقط كان يسمح بسرقة تخصيص من دفعة POSTED إلى مسودة.
    IF TG_OP = 'UPDATE' AND OLD.payment_id IS DISTINCT FROM NEW.payment_id THEN
        SELECT status INTO parent_status FROM provider_payments WHERE id = OLD.payment_id;
        IF parent_status IS NOT NULL AND parent_status <> 'DRAFT' THEN
            RAISE EXCEPTION
                'لا يمكن نقل تخصيص من الدفعة % في حالة % — التخصيصات مجمّدة.',
                OLD.payment_id, parent_status;
        END IF;
    END IF;

    IF TG_OP = 'DELETE' THEN
        parent_id := OLD.payment_id;
    ELSE
        parent_id := NEW.payment_id;
    END IF;

    SELECT status INTO parent_status FROM provider_payments WHERE id = parent_id;

    IF parent_status IS NULL THEN
        RETURN COALESCE(NEW, OLD); -- الرأس يُحذف في نفس المعاملة
    END IF;

    IF parent_status <> 'DRAFT' THEN
        RAISE EXCEPTION
            'لا يمكن تعديل تخصيصات الدفعة % في حالة % — التخصيصات تُجمَّد عند الترحيل.',
            parent_id, parent_status;
    END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_allocation_immutable_after_post
    BEFORE INSERT OR UPDATE OR DELETE ON provider_payment_allocations
    FOR EACH ROW EXECUTE FUNCTION enforce_allocation_immutable_after_post();

-- ─────────────────────────────────────────────────────────────────────────────
-- 6) صحة قيد الدفتر — لا مجرد وجوده
--
-- المفتاح الأجنبي يضمن أن الصف موجود فقط. هذه الثوابت تضمن أنه القيد الصحيح:
-- نفس المزود، ونفس المبلغ، والنوع والاتجاه الصحيحان، ومرجعه يشير للدفعة نفسها.
-- CHECK لا يستطيع قراءة جدول آخر، فيلزم Constraint Trigger.
-- مؤجَّل حتى COMMIT ليتمكن الترحيل من إنشاء القيد والدفعة في أي ترتيب.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION assert_provider_payment_ledger_matches()
RETURNS TRIGGER AS $$
DECLARE
    txn        RECORD;
    account_pk BIGINT;
BEGIN
    SELECT id INTO account_pk FROM provider_accounts WHERE provider_id = NEW.provider_id;

    -- قيد الدفع
    IF NEW.ledger_transaction_id IS NOT NULL THEN
        SELECT * INTO txn FROM account_transactions WHERE id = NEW.ledger_transaction_id;

        IF txn.provider_account_id IS DISTINCT FROM account_pk THEN
            RAISE EXCEPTION 'قيد دفتر الدفعة % يخص حساب مزود آخر.', NEW.id;
        END IF;
        IF txn.reference_type <> 'PROVIDER_PAYMENT' THEN
            RAISE EXCEPTION 'قيد دفتر الدفعة % نوعه % بدل PROVIDER_PAYMENT.', NEW.id, txn.reference_type;
        END IF;
        IF txn.reference_id IS DISTINCT FROM NEW.id THEN
            RAISE EXCEPTION 'قيد دفتر الدفعة % يشير إلى مرجع مختلف (%).', NEW.id, txn.reference_id;
        END IF;
        IF txn.amount <> NEW.amount THEN
            RAISE EXCEPTION 'مبلغ قيد الدفتر (%) لا يساوي مبلغ الدفعة % (%).', txn.amount, NEW.id, NEW.amount;
        END IF;
        IF txn.transaction_type <> 'DEBIT' THEN
            RAISE EXCEPTION 'قيد دفع الدفعة % يجب أن يكون DEBIT لا %.', NEW.id, txn.transaction_type;
        END IF;
    END IF;

    -- قيد العكس: نفس الشروط باتجاه معاكس ونوع مستقل
    IF NEW.reversal_ledger_transaction_id IS NOT NULL THEN
        SELECT * INTO txn FROM account_transactions WHERE id = NEW.reversal_ledger_transaction_id;

        IF txn.provider_account_id IS DISTINCT FROM account_pk THEN
            RAISE EXCEPTION 'قيد عكس الدفعة % يخص حساب مزود آخر.', NEW.id;
        END IF;
        IF txn.reference_type <> 'PROVIDER_PAYMENT_REVERSAL' THEN
            RAISE EXCEPTION 'قيد عكس الدفعة % نوعه % بدل PROVIDER_PAYMENT_REVERSAL.', NEW.id, txn.reference_type;
        END IF;
        IF txn.reference_id IS DISTINCT FROM NEW.id THEN
            RAISE EXCEPTION 'قيد عكس الدفعة % يشير إلى مرجع مختلف (%).', NEW.id, txn.reference_id;
        END IF;
        IF txn.amount <> NEW.amount THEN
            RAISE EXCEPTION 'مبلغ قيد العكس (%) لا يساوي مبلغ الدفعة % (%).', txn.amount, NEW.id, NEW.amount;
        END IF;
        IF txn.transaction_type <> 'CREDIT' THEN
            RAISE EXCEPTION 'قيد عكس الدفعة % يجب أن يكون CREDIT لا %.', NEW.id, txn.transaction_type;
        END IF;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_provider_payment_ledger_matches
    AFTER INSERT OR UPDATE ON provider_payments
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION assert_provider_payment_ledger_matches();
