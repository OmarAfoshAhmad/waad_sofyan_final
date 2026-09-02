-- Monetary benefit ceilings in Waad documents cap the eligible gross service
-- amount before co-pay splitting.  A 4,000 maternity ceiling with 75% coverage
-- allows 4,000 gross, then splits it into 3,000 company + 1,000 member; it must
-- not allow 5,333.33 gross just because the company's share remains 4,000.
--
-- Older structure/import paths defaulted buckets to COMPANY_SHARE.  That made
-- the ceiling measure the insurer's payable share instead of the benefit's
-- covered gross value.  Keep the enum for historical snapshots and explicit
-- future modelling, but normalize active policy buckets that carry a monetary
-- amount limit to the document semantics used by the claim engine.

UPDATE benefit_limit_buckets
SET consumption_basis = 'ELIGIBLE_AMOUNT'
WHERE amount_limit IS NOT NULL
  AND consumption_basis = 'COMPANY_SHARE';

COMMENT ON COLUMN benefit_limit_buckets.consumption_basis IS
    'For monetary benefit ceilings, ELIGIBLE_AMOUNT is the Waad document default: '
    'the cap measures gross eligible service amount before company/member co-pay split. '
    'COMPANY_SHARE is retained only for explicit legacy/special modelling and snapshots.';
