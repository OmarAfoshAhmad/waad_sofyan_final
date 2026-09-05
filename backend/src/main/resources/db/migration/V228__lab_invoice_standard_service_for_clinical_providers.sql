-- Make the manual laboratory invoice service available to clinical providers
-- too. It remains classified as CAT-COV-DIAG-FEES, so ceilings and coverage
-- are still resolved by the benefit-policy rule for each claim context.

INSERT INTO provider_service_defaults (provider_type, service_code, sort_order)
VALUES
    ('HOSPITAL', 'SYS-LAB-INVOICE', 90),
    ('CLINIC', 'SYS-LAB-INVOICE', 90)
ON CONFLICT (provider_type, service_code) DO UPDATE SET
    auto_apply = true,
    active = true,
    sort_order = EXCLUDED.sort_order,
    updated_at = now();
