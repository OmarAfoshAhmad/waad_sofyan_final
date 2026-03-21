-- V40: Add DB-level CHECK constraint for coverage_percent in benefit_policy_rules
-- JPA @Min(0)/@Max(100) annotations enforce this at the application layer,
-- but without a DB constraint a direct INSERT/UPDATE could bypass it.
-- NULL is allowed (means "use policy default").
ALTER TABLE benefit_policy_rules
    ADD CONSTRAINT chk_bpr_coverage_percent
    CHECK (coverage_percent IS NULL OR (coverage_percent >= 0 AND coverage_percent <= 100));
