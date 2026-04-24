-- V46: Add applied_discount_percent snapshot to claims
-- Captures the exact discount % used when the provider account was credited.
-- Prevents stale-discount bugs when a contract discount changes between claim
-- creation and approval.

ALTER TABLE claims
    ADD COLUMN IF NOT EXISTS applied_discount_percent NUMERIC(5,2) NULL;

COMMENT ON COLUMN claims.applied_discount_percent IS
    'نسبة الخصم المُطبّقة فعلياً على هذه المطالبة لحساب نصيب مقدم الخدمة. تُسجَّل مرة واحدة عند اعتماد المطالبة. مرجع ثابت للتدقيق المالي.';
