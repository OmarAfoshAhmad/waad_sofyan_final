-- ============================================================
-- V32: إصلاح شامل لجداول provider_admin_documents و
--      provider_contract_pricing_items بالاعتماد على الكيانات
--
-- الفجوات المكتشفة:
--
-- ① provider_admin_documents (V4 ↔ ProviderAdminDocument.java):
--    V4 أنشأ:    document_name NOT NULL, document_type NOT NULL, file_path NOT NULL
--    الكيان يتوقع: file_name     NOT NULL, type           NOT NULL, file_path nullable
--    أعمدة مفقودة: file_url, document_number, expiry_date, notes, created_at, updated_at
--
-- ② provider_contract_pricing_items (V12 ↔ ProviderContractPricingItem.java):
--    V12: medical_category_id BIGINT NOT NULL
--    الكيان: @JoinColumn(name="medical_category_id") — بدون nullable=false
--    → يمنع إنشاء بنود التسعير بدون ربط بفئة طبية
-- ============================================================

-- ════════════════════════════════════════════════════════════
-- 1. إصلاح provider_admin_documents
-- ════════════════════════════════════════════════════════════

-- 1a. إضافة عمود type (الكيان: @Column(name="type"))
--     V4 كان يستخدم document_type — ننسخ القيم ثم نجعله NOT NULL
ALTER TABLE provider_admin_documents
    ADD COLUMN IF NOT EXISTS type VARCHAR(50);

UPDATE provider_admin_documents
    SET type = document_type
    WHERE type IS NULL AND document_type IS NOT NULL;

UPDATE provider_admin_documents
    SET type = 'OTHER'
    WHERE type IS NULL;

ALTER TABLE provider_admin_documents ALTER COLUMN type SET NOT NULL;

-- 1b. إضافة عمود file_name (الكيان: @Column(name="file_name"))
--     V4 كان يستخدم document_name — ننسخ القيم ثم نجعله NOT NULL
ALTER TABLE provider_admin_documents
    ADD COLUMN IF NOT EXISTS file_name VARCHAR(255);

UPDATE provider_admin_documents
    SET file_name = document_name
    WHERE file_name IS NULL AND document_name IS NOT NULL;

UPDATE provider_admin_documents
    SET file_name = 'unknown'
    WHERE file_name IS NULL;

ALTER TABLE provider_admin_documents ALTER COLUMN file_name SET NOT NULL;

-- 1c. إضافة file_url (الكيان: @Column(name="file_url", length=500))
ALTER TABLE provider_admin_documents
    ADD COLUMN IF NOT EXISTS file_url VARCHAR(500);

-- 1d. تحرير NOT NULL من file_path (الكيان يسمح بـ null)
ALTER TABLE provider_admin_documents ALTER COLUMN file_path DROP NOT NULL;

-- 1e. إضافة document_number (الكيان: @Column(name="document_number", length=100))
ALTER TABLE provider_admin_documents
    ADD COLUMN IF NOT EXISTS document_number VARCHAR(100);

-- 1f. إضافة expiry_date (الكيان: @Column(name="expiry_date"))
ALTER TABLE provider_admin_documents
    ADD COLUMN IF NOT EXISTS expiry_date DATE;

-- 1g. إضافة notes (الكيان: @Column(name="notes", columnDefinition="TEXT"))
ALTER TABLE provider_admin_documents
    ADD COLUMN IF NOT EXISTS notes TEXT;

-- 1h. إضافة created_at (الكيان: @CreatedDate @Column(name="created_at", nullable=false, updatable=false))
ALTER TABLE provider_admin_documents
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;

UPDATE provider_admin_documents
    SET created_at = uploaded_at
    WHERE created_at IS NULL AND uploaded_at IS NOT NULL;

UPDATE provider_admin_documents
    SET created_at = CURRENT_TIMESTAMP
    WHERE created_at IS NULL;

ALTER TABLE provider_admin_documents ALTER COLUMN created_at SET NOT NULL;

-- 1i. إضافة updated_at (الكيان: @LastModifiedDate @Column(name="updated_at", nullable=false))
ALTER TABLE provider_admin_documents
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

UPDATE provider_admin_documents
    SET updated_at = uploaded_at
    WHERE updated_at IS NULL AND uploaded_at IS NOT NULL;

UPDATE provider_admin_documents
    SET updated_at = CURRENT_TIMESTAMP
    WHERE updated_at IS NULL;

ALTER TABLE provider_admin_documents ALTER COLUMN updated_at SET NOT NULL;

-- فهرس على type للأداء
CREATE INDEX IF NOT EXISTS idx_provider_admin_docs_type
    ON provider_admin_documents(type);

-- ════════════════════════════════════════════════════════════
-- 2. إصلاح provider_contract_pricing_items
-- ════════════════════════════════════════════════════════════

-- الكيان ProviderContractPricingItem.medicalCategory لا يملك nullable=false
-- لكن V12 أنشأ العمود بـ NOT NULL → يمنع INSERT بدون فئة طبية
ALTER TABLE provider_contract_pricing_items
    ALTER COLUMN medical_category_id DROP NOT NULL;

-- ════════════════════════════════════════════════════════════
-- 3. تنظيف CHECK constraint في providers
--    الكيان Provider.ProviderType لا يملك قيمة 'OTHER'
--    → إزالة القيمة من القيد لمنع تناقض البيانات مستقبلاً
-- ════════════════════════════════════════════════════════════

-- نحذف القيد القديم أولاً ثم نُعيد إنشاءه بدون 'OTHER'
-- (PostgreSQL لا يدعم ALTER CHECK مباشرة)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'providers'
          AND constraint_type = 'CHECK'
          AND constraint_name LIKE '%provider_type%'
    ) THEN
        -- حذف القيود CHECK المرتبطة بـ provider_type
        EXECUTE (
            SELECT 'ALTER TABLE providers DROP CONSTRAINT ' || constraint_name
            FROM information_schema.table_constraints
            WHERE table_name = 'providers'
              AND constraint_type = 'CHECK'
              AND constraint_name LIKE '%provider_type%'
            LIMIT 1
        );
    END IF;
END
$$;

ALTER TABLE providers
    ADD CONSTRAINT chk_provider_type
    CHECK (provider_type IN ('HOSPITAL','CLINIC','LAB','PHARMACY','RADIOLOGY'));
