-- Adds a manual-amount standard service for laboratory invoices.
-- This mirrors the V215 standard invoice services: it reuses the approved
-- diagnostics/professional-fees category and does not create or rename any
-- medical category. Coverage remains entirely owned by benefit-policy rules.

DO $$
DECLARE
    cat_diag_fees BIGINT;
BEGIN
    SELECT id INTO cat_diag_fees FROM medical_categories WHERE code = 'CAT-COV-DIAG-FEES';

    IF cat_diag_fees IS NULL THEN
        RAISE EXCEPTION 'V225: medical category CAT-COV-DIAG-FEES not found -- lab invoice standard service seed aborted';
    END IF;

    INSERT INTO medical_services (
        code,
        status,
        name,
        category_id,
        name_ar,
        name_en,
        is_master,
        deleted,
        active,
        pricing_mode,
        created_at,
        updated_at
    )
    VALUES (
        'SYS-LAB-INVOICE',
        'ACTIVE',
        'فاتورة تحاليل طبية',
        cat_diag_fees,
        'فاتورة تحاليل طبية',
        'Medical laboratory invoice',
        true,
        false,
        true,
        'MANUAL_AMOUNT',
        now(),
        now()
    )
    ON CONFLICT (code) DO UPDATE SET
        name = EXCLUDED.name,
        name_ar = EXCLUDED.name_ar,
        name_en = EXCLUDED.name_en,
        category_id = EXCLUDED.category_id,
        pricing_mode = EXCLUDED.pricing_mode,
        active = true,
        deleted = false,
        updated_at = now();
END $$;

INSERT INTO provider_service_defaults (provider_type, service_code, sort_order)
VALUES ('LAB', 'SYS-LAB-INVOICE', 1)
ON CONFLICT (provider_type, service_code) DO UPDATE SET
    auto_apply = true,
    active = true,
    sort_order = EXCLUDED.sort_order,
    updated_at = now();
