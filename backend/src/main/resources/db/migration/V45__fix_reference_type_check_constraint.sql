-- =================================================================================
-- V45: إصلاح قيد reference_type في جدول account_transactions
-- السبب: القيد الأصلي (V22) لا يتضمن CLAIM_REVERSAL و CLAIM_SETTLEMENT
--        مما يؤدي إلى فشل عمليات عكس المطالبات والتسويات الفردية صامتاً
-- =================================================================================

-- 1. حذف القيد القديم الذي يسمح فقط بـ CLAIM_APPROVAL, SETTLEMENT_PAYMENT, ADJUSTMENT
ALTER TABLE account_transactions
    DROP CONSTRAINT IF EXISTS account_transactions_reference_type_check;

-- 2. إضافة القيد الجديد بجميع القيم الصحيحة من enum ReferenceType
ALTER TABLE account_transactions
    ADD CONSTRAINT account_transactions_reference_type_check
    CHECK (reference_type IN (
        'CLAIM_APPROVAL',
        'CLAIM_REVERSAL',
        'CLAIM_SETTLEMENT',
        'SETTLEMENT_PAYMENT',
        'ADJUSTMENT'
    ));
