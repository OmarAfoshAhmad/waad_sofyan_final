-- =================================================================================
-- V44: إضافة قيود CHECK على الأعمدة المالية في claims و claim_lines
-- الهدف: منع إدخال قيم سالبة على مستوى قاعدة البيانات
-- ملاحظة: requested_amount, unit_price, total_amount لديها بالفعل CHECK
-- =================================================================================

-- ===== claims: قيود المبالغ المالية =====
ALTER TABLE claims
    ADD CONSTRAINT chk_claims_approved_amount
    CHECK (approved_amount IS NULL OR approved_amount >= 0);

ALTER TABLE claims
    ADD CONSTRAINT chk_claims_paid_amount
    CHECK (paid_amount IS NULL OR paid_amount >= 0);

ALTER TABLE claims
    ADD CONSTRAINT chk_claims_patient_share
    CHECK (patient_share IS NULL OR patient_share >= 0);

ALTER TABLE claims
    ADD CONSTRAINT chk_claims_refused_amount
    CHECK (refused_amount IS NULL OR refused_amount >= 0);

ALTER TABLE claims
    ADD CONSTRAINT chk_claims_patient_copay
    CHECK (patient_copay IS NULL OR patient_copay >= 0);

ALTER TABLE claims
    ADD CONSTRAINT chk_claims_net_provider_amount
    CHECK (net_provider_amount IS NULL OR net_provider_amount >= 0);

ALTER TABLE claims
    ADD CONSTRAINT chk_claims_deductible_applied
    CHECK (deductible_applied IS NULL OR deductible_applied >= 0);

ALTER TABLE claims
    ADD CONSTRAINT chk_claims_copay_percent
    CHECK (copay_percent IS NULL OR (copay_percent >= 0 AND copay_percent <= 100));

-- ===== claim_lines: قيود المبالغ المالية =====
ALTER TABLE claim_lines
    ADD CONSTRAINT chk_claim_lines_approved_amount
    CHECK (approved_amount IS NULL OR approved_amount >= 0);

ALTER TABLE claim_lines
    ADD CONSTRAINT chk_claim_lines_refused_amount
    CHECK (refused_amount IS NULL OR refused_amount >= 0);

ALTER TABLE claim_lines
    ADD CONSTRAINT chk_claim_lines_quantity_positive
    CHECK (quantity > 0);

ALTER TABLE claim_lines
    ADD CONSTRAINT chk_claim_lines_benefit_limit
    CHECK (benefit_limit IS NULL OR benefit_limit >= 0);

ALTER TABLE claim_lines
    ADD CONSTRAINT chk_claim_lines_coverage_percent
    CHECK (coverage_percent_snapshot IS NULL OR (coverage_percent_snapshot >= 0 AND coverage_percent_snapshot <= 100));
