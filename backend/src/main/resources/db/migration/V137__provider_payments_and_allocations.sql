-- ═══════════════════════════════════════════════════════════════════════════════
-- نموذج دفعات مقدمي الخدمة — الحوالة كوحدة، والتخصيص تفسير لها
--
-- الواقع التشغيلي: الدفع حوالة بنكية واحدة لمقدم الخدمة (نصف المستحق أو كامله)،
-- ثم تُوزَّع على الوثائق والفترات حسب استحقاقها. النموذج القديم (payment_records)
-- مفتاحه (مزود × جهة عمل × سنة × شهر)، فيُجبر المحاسب على تفتيت الحوالة الواحدة.
--
-- ولا يوجد بيانات إنتاج (لا دفعات ولا مطالبات مُرحَّلة)، لذلك لا هجرة بيانات هنا:
-- الجداول الجديدة تبدأ فارغة، ويُسحب المسار القديم في مرحلة لاحقة بعد تفعيل الجديد.
--
-- انظر: backend/docs/design/provider-payment-model.md
-- ═══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- رأس الدفعة: مستند الحوالة النقدية الفعلية
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE provider_payments (
    id                     BIGSERIAL PRIMARY KEY,
    provider_id            BIGINT         NOT NULL REFERENCES providers(id) ON DELETE RESTRICT,

    amount                 NUMERIC(15,2)  NOT NULL CHECK (amount > 0),
    payment_date           DATE           NOT NULL,
    payment_method         VARCHAR(50)    NOT NULL,

    -- مرجع بنكي تجاري. منفصل تماماً عن idempotency_key: الأول يصف الحوالة،
    -- والثاني يمنع تكرار الطلب. خلطهما هو عيب المسار القديم.
    reference_number       VARCHAR(100),

    -- منع تكرار الطلب على مستوى النظام. فريد عالمياً عند وجوده.
    idempotency_key        VARCHAR(120),

    status                 VARCHAR(20)    NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'POSTED', 'REVERSED')),

    notes                  VARCHAR(1000),
    attachment_path        VARCHAR(500),

    -- ربط الدفعة بقيد الدفتر الوحيد الناتج عنها (يُملأ عند الترحيل).
    ledger_transaction_id  BIGINT         REFERENCES account_transactions(id) ON DELETE RESTRICT,

    posted_at              TIMESTAMP,
    posted_by              VARCHAR(150),
    reversed_at            TIMESTAMP,
    reversed_by            VARCHAR(150),
    reversal_reason        VARCHAR(1000),

    created_at             TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by             VARCHAR(150),
    updated_at             TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by             VARCHAR(150),
    version                BIGINT         NOT NULL DEFAULT 0,

    -- الحالة تفرض اكتمال بياناتها: لا دفعة مُرحَّلة بلا قيد دفتر ولا توقيت،
    -- ولا دفعة معكوسة بلا سبب. يمنع هذا الحالات نصف المكتملة من الأساس.
    CONSTRAINT chk_provider_payments_posted_complete CHECK (
        status <> 'POSTED' OR (ledger_transaction_id IS NOT NULL AND posted_at IS NOT NULL)
    ),
    CONSTRAINT chk_provider_payments_reversed_complete CHECK (
        status <> 'REVERSED' OR (reversed_at IS NOT NULL AND reversal_reason IS NOT NULL)
    ),
    -- المسودة لم تُرحَّل بعد، فلا يجوز أن تحمل قيد دفتر.
    CONSTRAINT chk_provider_payments_draft_has_no_ledger CHECK (
        status <> 'DRAFT' OR ledger_transaction_id IS NULL
    )
);

-- مفتاح منع التكرار: فريد عند وجوده فقط (الدفعات النقدية بلا مفتاح غير متأثرة).
CREATE UNIQUE INDEX ux_provider_payments_idempotency
    ON provider_payments(idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- كل قيد دفتر يخص دفعة واحدة على الأكثر — يمنع ترحيل الدفعة مرتين على نفس القيد.
CREATE UNIQUE INDEX ux_provider_payments_ledger_txn
    ON provider_payments(ledger_transaction_id)
    WHERE ledger_transaction_id IS NOT NULL;

CREATE INDEX idx_provider_payments_provider_status_date
    ON provider_payments(provider_id, status, payment_date);
CREATE INDEX idx_provider_payments_provider_reference
    ON provider_payments(provider_id, reference_number);

-- ─────────────────────────────────────────────────────────────────────────────
-- التخصيص: تفسير لكيفية توزيع الحوالة على الوثائق والفترات
--
-- تفسيري بحت: لا يخصم من رصيد المزود إطلاقاً. الخصم يحدث مرة واحدة فقط عند
-- ترحيل رأس الدفعة. هذا هو الثابت الذي يمنع الخصم المزدوج.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE provider_payment_allocations (
    id                        BIGSERIAL PRIMARY KEY,
    payment_id                BIGINT        NOT NULL REFERENCES provider_payments(id) ON DELETE RESTRICT,
    employer_id               BIGINT        NOT NULL REFERENCES employers(id) ON DELETE RESTRICT,

    target_year               INTEGER       NOT NULL CHECK (target_year BETWEEN 2000 AND 2200),
    target_month              INTEGER       NOT NULL CHECK (target_month BETWEEN 1 AND 12),

    amount                    NUMERIC(15,2) NOT NULL CHECK (amount > 0),

    -- لقطة الاستحقاق وقت التخصيص: تُثبت لماذا اقترح النظام هذه القيمة، حتى لو
    -- تغيّر الاستحقاق لاحقاً بمطالبة بأثر رجعي.
    outstanding_at_allocation NUMERIC(15,2),

    allocation_method         VARCHAR(30)   NOT NULL DEFAULT 'AUTO_FIFO'
        CHECK (allocation_method IN ('AUTO_FIFO', 'AUTO_PROPORTIONAL', 'MANUAL')),
    override_reason           VARCHAR(1000),

    created_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                VARCHAR(150),
    version                   BIGINT        NOT NULL DEFAULT 0,

    -- التعديل اليدوي يجب أن يُبرَّر: الانحراف عن FIFO قرار محاسبي لا تفصيلة.
    CONSTRAINT chk_allocation_manual_needs_reason CHECK (
        allocation_method <> 'MANUAL' OR override_reason IS NOT NULL
    ),
    -- تقاطع (جهة عمل × سنة × شهر) لا يتكرر داخل الدفعة الواحدة.
    CONSTRAINT ux_allocation_payment_target
        UNIQUE (payment_id, employer_id, target_year, target_month)
);

CREATE INDEX idx_allocations_payment ON provider_payment_allocations(payment_id);
CREATE INDEX idx_allocations_target
    ON provider_payment_allocations(employer_id, target_year, target_month);

-- ─────────────────────────────────────────────────────────────────────────────
-- ثابت اكتمال التخصيص — مشروط بالحالة
--
--   DRAFT  : Σ allocations ≤ amount   (النقص مسموح؛ الدفعة قيد التحضير)
--   POSTED : Σ allocations ≤ amount   (الفائض يبقى معلناً كـ unallocated)
--
-- يُفرض في القاعدة لأن الثابت المفروض تطبيقياً ينحرف. التحقق يعمل عند تغيير
-- التخصيصات وعند تغيير مبلغ/حالة الرأس معاً، فلا يمكن الالتفاف عليه من أي جهة.
-- DEFERRABLE: يسمح بإدراج الرأس وتخصيصاته في نفس المعاملة بأي ترتيب.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION assert_payment_allocation_within_amount()
RETURNS TRIGGER AS $$
DECLARE
    target_payment_id BIGINT;
    payment_amount    NUMERIC(15,2);
    allocated_total   NUMERIC(15,2);
BEGIN
    target_payment_id := COALESCE(NEW.payment_id, OLD.payment_id);

    SELECT amount INTO payment_amount
    FROM provider_payments WHERE id = target_payment_id;

    IF payment_amount IS NULL THEN
        RETURN NULL; -- الرأس حُذف في نفس المعاملة؛ لا شيء يُتحقق منه
    END IF;

    SELECT COALESCE(SUM(amount), 0) INTO allocated_total
    FROM provider_payment_allocations WHERE payment_id = target_payment_id;

    IF allocated_total > payment_amount THEN
        RAISE EXCEPTION
            'مجموع تخصيصات الدفعة (%) يتجاوز مبلغها (%) — الدفعة %',
            allocated_total, payment_amount, target_payment_id;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_allocation_within_payment_amount
    AFTER INSERT OR UPDATE OR DELETE ON provider_payment_allocations
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION assert_payment_allocation_within_amount();

-- تخفيض مبلغ الرأس تحت مجموع تخصيصاته يجب أن يفشل أيضاً — وإلا صار الالتفاف
-- على الثابت ممكناً من جهة الرأس بدل جهة التخصيصات.
CREATE OR REPLACE FUNCTION assert_payment_amount_covers_allocations()
RETURNS TRIGGER AS $$
DECLARE
    allocated_total NUMERIC(15,2);
BEGIN
    SELECT COALESCE(SUM(amount), 0) INTO allocated_total
    FROM provider_payment_allocations WHERE payment_id = NEW.id;

    IF allocated_total > NEW.amount THEN
        RAISE EXCEPTION
            'لا يمكن خفض مبلغ الدفعة % إلى % لأن تخصيصاتها تبلغ %',
            NEW.id, NEW.amount, allocated_total;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_payment_amount_covers_allocations
    AFTER UPDATE OF amount ON provider_payments
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION assert_payment_amount_covers_allocations();
