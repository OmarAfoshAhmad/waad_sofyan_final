-- ============================================================
-- V9: Medical Categories — جداول التصنيف الطبي
--
-- الحقول المعرَّفة تطابق بالكامل:  MedicalCategory.java
-- لا توجد أعمدة يتيمة، لا تكرار.
-- ============================================================

-- ════════════════════════════════════════════════════════
-- الجدول الرئيسي
-- ════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS medical_categories (
    -- PK
    id               BIGINT PRIMARY KEY DEFAULT nextval('medical_category_seq'),

    -- معرّف الأعمال (immutable, UNIQUE)
    code             VARCHAR(50)  NOT NULL,

    -- الأسماء
    name             VARCHAR(200) NOT NULL,
    name_ar          VARCHAR(200),
    name_en          VARCHAR(200),

    -- التسلسل الهرمي
    parent_id        BIGINT,

    -- السياق السريري
    context          VARCHAR(20)  NOT NULL DEFAULT 'ANY'
                         CHECK (context IN ('INPATIENT','OUTPATIENT','OPERATING_ROOM','EMERGENCY','SPECIAL','ANY')),

    -- نسبة التغطية (يُدار من المسؤول)
    coverage_percent DECIMAL(5,2),

    -- الحذف الناعم
    deleted          BOOLEAN   NOT NULL DEFAULT false,
    deleted_at       TIMESTAMP,
    deleted_by       BIGINT,

    -- الحالة
    active           BOOLEAN   NOT NULL DEFAULT true,

    -- التدقيق
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_medical_categories_code    UNIQUE (code),
    CONSTRAINT fk_medical_category_parent    FOREIGN KEY (parent_id)
        REFERENCES medical_categories(id) ON DELETE RESTRICT
);

-- فهارس
CREATE INDEX IF NOT EXISTS idx_medical_categories_code      ON medical_categories(code);
CREATE INDEX IF NOT EXISTS idx_medical_categories_active    ON medical_categories(active);
CREATE INDEX IF NOT EXISTS idx_medical_categories_parent_id ON medical_categories(parent_id);
CREATE INDEX IF NOT EXISTS idx_medical_categories_deleted   ON medical_categories(deleted) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_medical_categories_del_code  ON medical_categories(deleted, code);

-- ════════════════════════════════════════════════════════
-- جدول الجذور (Many-to-Many self-join)
-- ════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS medical_category_roots (
    category_id BIGINT    NOT NULL,
    root_id     BIGINT    NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (category_id, root_id),
    CONSTRAINT fk_mcr_category FOREIGN KEY (category_id)
        REFERENCES medical_categories(id) ON DELETE CASCADE,
    CONSTRAINT fk_mcr_root     FOREIGN KEY (root_id)
        REFERENCES medical_categories(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_mcr_root_id ON medical_category_roots(root_id);

