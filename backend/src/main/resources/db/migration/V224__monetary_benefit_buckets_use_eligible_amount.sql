-- =============================================================================
-- V224: Reassert WAAD's monetary benefit-ceiling axis after context-rule fixes.
--
-- V214 established the financial rule: a monetary benefit ceiling caps the
-- eligible gross service amount before company/member co-pay split.
--
-- Example: a 1,500 benefit ceiling at 75% coverage accepts 1,500 gross, then
-- splits it into 1,125 company + 375 member. A 1,950 invoice therefore has
-- 450 refused by the ceiling. It must not be accepted just because the
-- company's share (1,462.50) is still below 1,500.
--
-- Some buckets may have been created or imported after V214 with the legacy
-- default COMPANY_SHARE. Normalize every monetary bucket again; historical
-- snapshots keep their own recorded axis and are not touched.
-- =============================================================================

UPDATE benefit_limit_buckets
SET consumption_basis = 'ELIGIBLE_AMOUNT',
    updated_at = CURRENT_TIMESTAMP
WHERE amount_limit IS NOT NULL
  AND consumption_basis = 'COMPANY_SHARE';

DO $$
DECLARE
    remaining_count INTEGER;
BEGIN
    SELECT COUNT(*)
    INTO remaining_count
    FROM benefit_limit_buckets
    WHERE amount_limit IS NOT NULL
      AND consumption_basis = 'COMPANY_SHARE';

    IF remaining_count > 0 THEN
        RAISE EXCEPTION 'V224: % monetary benefit buckets still measure COMPANY_SHARE', remaining_count;
    END IF;

    RAISE NOTICE 'V224: all monetary benefit buckets now measure ELIGIBLE_AMOUNT';
END $$;
