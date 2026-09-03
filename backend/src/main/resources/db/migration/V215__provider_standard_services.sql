-- Pharmacy and optics invoices are not priced from a contract list -- there
-- is no per-item price to negotiate, only the invoice total the member
-- brings in. Forcing them through ProviderContractPricingItem meant either
-- a fake zero-price catalog row or the service not existing in claim entry
-- at all. This gives MedicalService an explicit pricing mode instead, and
-- seeds four standard, invoice-priced services against categories that
-- already exist -- no parallel category, no new coverage engine.

ALTER TABLE medical_services
    ADD COLUMN IF NOT EXISTS pricing_mode VARCHAR(20) NOT NULL DEFAULT 'CONTRACT_PRICE';

-- Fixed at save time so a later change to MedicalService.pricing_mode never
-- reclassifies a claim line that already happened.
ALTER TABLE claim_lines
    ADD COLUMN IF NOT EXISTS amount_source VARCHAR(20) NOT NULL DEFAULT 'CONTRACT_PRICE';

DO $$
DECLARE
    cat_drug_general   BIGINT;
    cat_drug_chronic   BIGINT;
    cat_oncology       BIGINT;
    cat_eye_optical    BIGINT;
BEGIN
    SELECT id INTO cat_drug_general FROM medical_categories WHERE code = 'CAT-DRUG-GENERAL';
    SELECT id INTO cat_drug_chronic FROM medical_categories WHERE code = 'CAT-DRUG-CHRONIC';
    SELECT id INTO cat_oncology     FROM medical_categories WHERE code = 'CAT-ONCOLOGY';
    SELECT id INTO cat_eye_optical  FROM medical_categories WHERE code = 'CAT-COV-EYE-OPTICAL';

    IF cat_drug_general IS NULL THEN
        RAISE EXCEPTION 'V215: medical category CAT-DRUG-GENERAL not found -- standard service seed aborted, no substitute category will be created';
    END IF;
    IF cat_drug_chronic IS NULL THEN
        RAISE EXCEPTION 'V215: medical category CAT-DRUG-CHRONIC not found -- standard service seed aborted, no substitute category will be created';
    END IF;
    IF cat_oncology IS NULL THEN
        RAISE EXCEPTION 'V215: medical category CAT-ONCOLOGY not found -- standard service seed aborted, no substitute category will be created';
    END IF;
    IF cat_eye_optical IS NULL THEN
        RAISE EXCEPTION 'V215: medical category CAT-COV-EYE-OPTICAL not found -- standard service seed aborted, no substitute category will be created';
    END IF;

    INSERT INTO medical_services (code, status, name, category_id, name_ar, name_en, is_master, deleted, active, pricing_mode, created_at, updated_at)
    VALUES
        ('SYS-DRUG-GENERAL', 'ACTIVE', 'فاتورة أدوية روتينية', cat_drug_general, 'فاتورة أدوية روتينية', 'Routine medication invoice', true, false, true, 'MANUAL_AMOUNT', now(), now()),
        ('SYS-DRUG-CHRONIC', 'ACTIVE', 'فاتورة أدوية مزمنة', cat_drug_chronic, 'فاتورة أدوية مزمنة', 'Chronic medication invoice', true, false, true, 'MANUAL_AMOUNT', now(), now()),
        ('SYS-DRUG-ONCOLOGY', 'ACTIVE', 'فاتورة أدوية أورام', cat_oncology, 'فاتورة أدوية أورام', 'Oncology medication invoice', true, false, true, 'MANUAL_AMOUNT', now(), now()),
        ('SYS-OPTICAL-GLASSES', 'ACTIVE', 'تركيب نظارة', cat_eye_optical, 'تركيب نظارة', 'Eyeglasses fitting', true, false, true, 'MANUAL_AMOUNT', now(), now())
    ON CONFLICT (code) DO UPDATE SET
        pricing_mode = EXCLUDED.pricing_mode,
        category_id = EXCLUDED.category_id;
END $$;

-- Which standard service is suggested/auto-applied for which facility type.
-- This picks a service, it does not decide coverage -- the benefit policy
-- rule engine still owns that, unchanged, keyed by the category above.
CREATE TABLE IF NOT EXISTS provider_service_defaults (
    id          BIGSERIAL PRIMARY KEY,
    provider_type VARCHAR(30) NOT NULL,
    service_code  VARCHAR(50) NOT NULL,
    auto_apply    BOOLEAN NOT NULL DEFAULT true,
    active        BOOLEAN NOT NULL DEFAULT true,
    sort_order    INTEGER NOT NULL DEFAULT 0,
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_provider_service_defaults UNIQUE (provider_type, service_code)
);

INSERT INTO provider_service_defaults (provider_type, service_code, sort_order) VALUES
    ('PHARMACY', 'SYS-DRUG-GENERAL', 1),
    ('PHARMACY', 'SYS-DRUG-CHRONIC', 2),
    ('PHARMACY', 'SYS-DRUG-ONCOLOGY', 3),
    ('OPTICS', 'SYS-OPTICAL-GLASSES', 1)
ON CONFLICT (provider_type, service_code) DO NOTHING;

-- Bulk-provisioning these standard services across many providers is a
-- distinct, sensitive administrative action from PROVIDER_MANAGE (editing
-- one provider's own record) -- it can touch every pharmacy or optics
-- facility in one call, so it gets its own grant rather than riding on an
-- existing one.
INSERT INTO rbac_permissions (code, category, display_name_ar, sensitive)
VALUES ('PROVIDER_STANDARD_SERVICES_MANAGE', 'PROVIDERS',
        'تطبيق الخدمات المهنية القياسية على مقدمي الخدمة', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO rbac_role_permissions (role_code, permission_code, granted_by)
VALUES ('SUPER_ADMIN', 'PROVIDER_STANDARD_SERVICES_MANAGE', 'MIGRATION_V215')
ON CONFLICT (role_code, permission_code) DO NOTHING;
