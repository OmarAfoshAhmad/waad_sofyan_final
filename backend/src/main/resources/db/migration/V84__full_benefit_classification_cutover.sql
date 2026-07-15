-- =============================================================================
-- V84: Full cutover to context-independent medical categories.
--
-- This installation has no legacy production data. The old seeded classification
-- is intentionally replaced, not migrated or kept as a compatibility layer.
-- Approved naming source:
--   قائمة التصنيفات المعتمدة النهائي_كامل.xlsx
-- =============================================================================

-- Remove seeded/empty references that depend on the old dictionary.
DELETE FROM benefit_policy_template_rules;
DELETE FROM benefit_policy_rules;
DELETE FROM service_specialty_insurance_map;
DELETE FROM medical_category_roots;
UPDATE visits SET medical_category_id = NULL;
UPDATE claim_lines SET service_category_id = NULL, applied_category_id = NULL;
DELETE FROM provider_contract_pricing_items;
DELETE FROM medical_categories;

-- A category can be valid in multiple encounter contexts. Context is never a
-- parent category and never part of the category's name.
CREATE TABLE IF NOT EXISTS medical_category_contexts (
    category_id  BIGINT NOT NULL REFERENCES medical_categories(id) ON DELETE CASCADE,
    context_type VARCHAR(20) NOT NULL,
    is_default   BOOLEAN NOT NULL DEFAULT false,
    is_active    BOOLEAN NOT NULL DEFAULT true,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (category_id, context_type),
    CONSTRAINT chk_medical_category_context CHECK (context_type IN
        ('INPATIENT','OUTPATIENT','OPERATING_ROOM','EMERGENCY','SPECIAL','ANY'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_medical_category_default_context
    ON medical_category_contexts(category_id)
    WHERE is_default = true AND is_active = true;

-- Final approved medical-service dictionary. Names use the approved workbook's
-- terms, with the spelling corrections approved during review.
INSERT INTO medical_categories(code, name, name_ar, name_en, parent_id, context,
                               coverage_percent, deleted, active, created_at, updated_at)
VALUES
 ('CAT-OPT',          'نظارة طبية',                                         'نظارة طبية',                                         NULL, NULL, 'OUTPATIENT', NULL, false, true, NOW(), NOW()),
 ('CAT-ROOM',         'الإيواء غرفة خاصة',                                  'الإيواء غرفة خاصة',                                  NULL, NULL, 'INPATIENT',  NULL, false, true, NOW(), NOW()),
 ('CAT-SURGERY',      'العمليات الجراحية',                                  'العمليات الجراحية',                                  NULL, NULL, 'ANY',        NULL, false, true, NOW(), NOW()),
 ('CAT-DRUG',         'الدواء',                                             'الدواء',                                             NULL, NULL, 'ANY',        NULL, false, true, NOW(), NOW()),
 ('CAT-MED-SUP',      'المستلزمات الطبية',                                  'المستلزمات الطبية',                                  NULL, NULL, 'ANY',        NULL, false, true, NOW(), NOW()),
 ('CAT-ICU',          'العناية الفائقة',                                    'العناية الفائقة',                                    NULL, NULL, 'INPATIENT',  NULL, false, true, NOW(), NOW()),
 ('CAT-CCU',          'عناية القلب',                                        'عناية القلب',                                        NULL, NULL, 'INPATIENT',  NULL, false, true, NOW(), NOW()),
 ('CAT-PRACT-FEE',    'رسوم الأطباء والجراحين والمستشارين والممارسين',       'رسوم الأطباء والجراحين والمستشارين والممارسين',       NULL, NULL, 'ANY',        NULL, false, true, NOW(), NOW()),
 ('CAT-ANESTHESIA',   'نفقات التخدير',                                      'نفقات التخدير',                                      NULL, NULL, 'ANY',        NULL, false, true, NOW(), NOW()),
 ('CAT-SURG-MAT',     'المعدات والمواد الجراحية',                            'المعدات والمواد الجراحية',                            NULL, NULL, 'ANY',        NULL, false, true, NOW(), NOW()),
 ('CAT-DIAGNOSTIC',   'الكشوف التشخيصية',                                   'الكشوف التشخيصية',                                   NULL, NULL, 'ANY',        NULL, false, true, NOW(), NOW()),
 ('CAT-DAY-CARE',     'العلاج والرعاية اليومية',                             'العلاج والرعاية اليومية',                             NULL, NULL, 'INPATIENT',  NULL, false, true, NOW(), NOW()),
 ('CAT-DENT-EMERG',   'علاج الاسنان بالطوارئ للمريض داخل مستشفى',            'علاج الاسنان بالطوارئ للمريض داخل مستشفى',            NULL, NULL, 'INPATIENT',  NULL, false, true, NOW(), NOW()),
 ('CAT-AMBULANCE',    'الاسعاف المحلي',                                     'الاسعاف المحلي',                                     NULL, NULL, 'EMERGENCY',  NULL, false, true, NOW(), NOW()),
 ('CAT-HOME-NURSING', 'التمريض في المنزل أو النقاهة ( بديل الاقامة بعد الخروج )', 'التمريض في المنزل أو النقاهة ( بديل الاقامة بعد الخروج )', NULL, NULL, 'SPECIAL', NULL, false, true, NOW(), NOW()),
 ('CAT-PHYSIO',       'العلاج الطبيعي',                                     'العلاج الطبيعي',                                     NULL, NULL, 'ANY',        NULL, false, true, NOW(), NOW()),
 ('CAT-IMG-ADV',      'التصوير بالرنين المغناطيسي و المقطعي و الطبقي',       'التصوير بالرنين المغناطيسي و المقطعي و الطبقي',       NULL, NULL, 'ANY',        NULL, false, true, NOW(), NOW()),
 ('CAT-LAB',          'تحاليل و مختبرات',                                   'تحاليل و مختبرات',                                   NULL, NULL, 'ANY',        NULL, false, true, NOW(), NOW()),
 ('CAT-IMG-DIAG',     'اشعة سينية و اشعة تشخيصية',                          'اشعة سينية و اشعة تشخيصية',                          NULL, NULL, 'ANY',        NULL, false, true, NOW(), NOW()),
 ('CAT-TRANSPLANT',   'زرع الاعضاء',                                        'زرع الاعضاء',                                        NULL, NULL, 'INPATIENT',  NULL, false, true, NOW(), NOW()),
 ('CAT-PSYCH-DRUG',   'الطب النفسي ( أدوية )',                              'الطب النفسي ( أدوية )',                              NULL, NULL, 'ANY',        NULL, false, true, NOW(), NOW()),
 ('CAT-PSYCH-SESS',   'الطب النفسي ( جلسات )',                              'الطب النفسي ( جلسات )',                              NULL, NULL, 'ANY',        NULL, false, true, NOW(), NOW()),
 ('CAT-ONCOLOGY',     'علاج الاورام',                                       'علاج الاورام',                                       NULL, NULL, 'ANY',        NULL, false, true, NOW(), NOW()),
 ('CAT-DIALYSIS',     'الغسيل الكلوي',                                      'الغسيل الكلوي',                                      NULL, NULL, 'ANY',        NULL, false, true, NOW(), NOW()),
 ('CAT-MAT-NORMAL',   'الولادة الطبيعية',                                   'الولادة الطبيعية',                                   NULL, NULL, 'INPATIENT',  NULL, false, true, NOW(), NOW()),
 ('CAT-MAT-CS',       'الولادة القيصرية',                                   'الولادة القيصرية',                                   NULL, NULL, 'INPATIENT',  NULL, false, true, NOW(), NOW()),
 ('CAT-MAT-COMP',     'مضاعفات الحمل و الولادة',                             'مضاعفات الحمل و الولادة',                             NULL, NULL, 'ANY',        NULL, false, true, NOW(), NOW()),
 ('CAT-DME',          'الاجهزه و المعدات الطبية و فق تقرير الطبيب المختص',  'الاجهزه و المعدات الطبية و فق تقرير الطبيب المختص',  NULL, NULL, 'OUTPATIENT', NULL, false, true, NOW(), NOW()),
 ('CAT-DENT-ROUTINE', 'علاج الاسنان الروتيني ( كشف- خلع- حشو- تنظيف )',      'علاج الاسنان الروتيني ( كشف- خلع- حشو- تنظيف )',      NULL, NULL, 'OUTPATIENT', NULL, false, true, NOW(), NOW()),
 ('CAT-DENT-PROSTHO', 'علاج الاسنان ( تركيب )',                             'علاج الاسنان ( تركيب )',                             NULL, NULL, 'OUTPATIENT', NULL, false, true, NOW(), NOW()),
 ('CAT-DENT-ORTHO',   'علاج الاسنان ( تقويم )',                             'علاج الاسنان ( تقويم )',                             NULL, NULL, 'OUTPATIENT', NULL, false, true, NOW(), NOW()),
 ('CAT-DENT-IMPLANT', 'علاج الاسنان ( زراعة )',                             'علاج الاسنان ( زراعة )',                             NULL, NULL, 'OUTPATIENT', NULL, false, true, NOW(), NOW()),
 ('CAT-EYE-EXAM',     'كشوف العيون',                                        'كشوف العيون',                                        NULL, NULL, 'OUTPATIENT', NULL, false, true, NOW(), NOW());

-- Backfill the many-context relation from the final classification definition.
INSERT INTO medical_category_contexts(category_id, context_type, is_default)
SELECT id,
       CASE context
           WHEN 'ANY' THEN 'OUTPATIENT'
           ELSE context
       END,
       true
FROM medical_categories;

INSERT INTO medical_category_contexts(category_id, context_type, is_default)
SELECT id, 'INPATIENT', false
FROM medical_categories
WHERE context = 'ANY'
ON CONFLICT DO NOTHING;

-- Context is now represented only by medical_category_contexts.
ALTER TABLE medical_categories DROP COLUMN IF EXISTS context;

-- Policy rules are unique per category and encounter context.
ALTER TABLE benefit_policy_rules
    ADD COLUMN IF NOT EXISTS encounter_type VARCHAR(20) NOT NULL DEFAULT 'OUTPATIENT',
    ADD COLUMN IF NOT EXISTS copay_percentage NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS inheritance_enabled BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS priority INTEGER NOT NULL DEFAULT 100;

ALTER TABLE benefit_policy_rules DROP CONSTRAINT IF EXISTS uk_bpr_policy_category;
ALTER TABLE benefit_policy_rules DROP CONSTRAINT IF EXISTS uk_bpr_policy_category_context;
ALTER TABLE benefit_policy_rules DROP CONSTRAINT IF EXISTS chk_bpr_encounter_type;
ALTER TABLE benefit_policy_rules ADD CONSTRAINT chk_bpr_encounter_type CHECK
    (encounter_type IN ('INPATIENT','OUTPATIENT','OPERATING_ROOM','EMERGENCY','SPECIAL','ANY'));
ALTER TABLE benefit_policy_rules DROP CONSTRAINT IF EXISTS chk_bpr_copay_percentage;
ALTER TABLE benefit_policy_rules ADD CONSTRAINT chk_bpr_copay_percentage CHECK
    (copay_percentage IS NULL OR copay_percentage BETWEEN 0 AND 100);

CREATE UNIQUE INDEX uq_bpr_policy_category_context_active
    ON benefit_policy_rules(benefit_policy_id, medical_category_id, encounter_type)
    WHERE deleted = false;
CREATE INDEX idx_bpr_context_resolution
    ON benefit_policy_rules(benefit_policy_id, medical_category_id, encounter_type, priority)
    WHERE active = true AND deleted = false;

-- Global benefit definitions separate non-medical expenses from service categories.
CREATE TABLE benefit_definitions (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(50) NOT NULL UNIQUE,
    name_ar     VARCHAR(255) NOT NULL,
    benefit_type VARCHAR(20) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_benefit_definition_type CHECK (benefit_type IN ('MEDICAL_SERVICE','SPECIAL_EXPENSE'))
);

INSERT INTO benefit_definitions(code, name_ar, benefit_type) VALUES
 ('BEN-WORK-INJURY', 'تكلفة اصابات العمل', 'SPECIAL_EXPENSE'),
 ('BEN-EVACUATION', 'الاخلاء الطبي', 'SPECIAL_EXPENSE'),
 ('BEN-COMPANION', 'تكلفة شخص مرافق واحد للشخص الذي تم اخلاءه', 'SPECIAL_EXPENSE'),
 ('BEN-FAMILY-TRAVEL', 'تكلفة السفر لاحد افراد عائلة المؤمن عليه في حالة الاخلاء', 'SPECIAL_EXPENSE'),
 ('BEN-RARE', 'خدمات نادرة', 'SPECIAL_EXPENSE');

-- Benefit groups and reusable financial/count buckets per policy.
CREATE TABLE benefit_groups (
    id               BIGSERIAL PRIMARY KEY,
    policy_id        BIGINT NOT NULL REFERENCES benefit_policies(id) ON DELETE CASCADE,
    code             VARCHAR(50) NOT NULL,
    name_ar          VARCHAR(255) NOT NULL,
    context_type     VARCHAR(20) NOT NULL DEFAULT 'ANY',
    aggregation_mode VARCHAR(20) NOT NULL,
    active           BOOLEAN NOT NULL DEFAULT true,
    version          BIGINT NOT NULL DEFAULT 0,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(policy_id, code),
    CONSTRAINT chk_benefit_group_context CHECK (context_type IN
        ('INPATIENT','OUTPATIENT','OPERATING_ROOM','EMERGENCY','SPECIAL','ANY')),
    CONSTRAINT chk_benefit_group_mode CHECK (aggregation_mode IN ('INDIVIDUAL','SHARED','HIERARCHICAL'))
);

CREATE TABLE benefit_limit_buckets (
    id                BIGSERIAL PRIMARY KEY,
    policy_id         BIGINT NOT NULL REFERENCES benefit_policies(id) ON DELETE CASCADE,
    benefit_group_id  BIGINT NOT NULL REFERENCES benefit_groups(id) ON DELETE CASCADE,
    code              VARCHAR(50) NOT NULL,
    name_ar           VARCHAR(255) NOT NULL,
    context_type      VARCHAR(20) NOT NULL DEFAULT 'ANY',
    amount_limit      NUMERIC(15,2),
    times_limit       INTEGER,
    days_limit        INTEGER,
    period_type       VARCHAR(20) NOT NULL DEFAULT 'POLICY_PERIOD',
    counting_method   VARCHAR(20) NOT NULL DEFAULT 'EACH_LINE',
    consumption_basis VARCHAR(20) NOT NULL DEFAULT 'COMPANY_SHARE',
    parent_bucket_id  BIGINT REFERENCES benefit_limit_buckets(id) ON DELETE RESTRICT,
    shared            BOOLEAN NOT NULL DEFAULT false,
    active            BOOLEAN NOT NULL DEFAULT true,
    version           BIGINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(policy_id, code),
    CONSTRAINT chk_bucket_limits CHECK
        ((amount_limit IS NULL OR amount_limit >= 0) AND
         (times_limit IS NULL OR times_limit >= 0) AND
         (days_limit IS NULL OR days_limit >= 0)),
    CONSTRAINT chk_bucket_not_self CHECK (parent_bucket_id IS NULL OR parent_bucket_id <> id),
    CONSTRAINT chk_bucket_context CHECK (context_type IN
        ('INPATIENT','OUTPATIENT','OPERATING_ROOM','EMERGENCY','SPECIAL','ANY')),
    CONSTRAINT chk_bucket_period CHECK (period_type IN
        ('PER_SERVICE','PER_VISIT','DAILY','MONTHLY','ANNUAL','POLICY_PERIOD','LIFETIME')),
    CONSTRAINT chk_bucket_counting CHECK (counting_method IN ('EACH_LINE','EACH_UNIT','PER_VISIT','PER_DAY')),
    CONSTRAINT chk_bucket_basis CHECK (consumption_basis IN ('ELIGIBLE_AMOUNT','COMPANY_SHARE'))
);

CREATE TABLE benefit_rule_buckets (
    id                BIGSERIAL PRIMARY KEY,
    rule_id           BIGINT NOT NULL REFERENCES benefit_policy_rules(id) ON DELETE CASCADE,
    bucket_id         BIGINT NOT NULL REFERENCES benefit_limit_buckets(id) ON DELETE RESTRICT,
    consumption_order INTEGER NOT NULL DEFAULT 1,
    consumption_mode  VARCHAR(20) NOT NULL DEFAULT 'PRIMARY',
    mandatory         BOOLEAN NOT NULL DEFAULT true,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(rule_id, bucket_id),
    CONSTRAINT chk_rule_bucket_mode CHECK (consumption_mode IN ('PRIMARY','SHARED','PARENT','SUB_LIMIT'))
);

-- Claim context is explicit and independent from medical category.
ALTER TABLE claims
    ADD COLUMN encounter_type VARCHAR(20) NOT NULL DEFAULT 'OUTPATIENT';
ALTER TABLE claims ADD CONSTRAINT chk_claim_encounter_type CHECK
    (encounter_type IN ('INPATIENT','OUTPATIENT','OPERATING_ROOM','EMERGENCY','SPECIAL'));
ALTER TABLE claims DROP COLUMN IF EXISTS manual_category_enabled;
ALTER TABLE claims DROP COLUMN IF EXISTS primary_category_code;

ALTER TABLE claim_lines
    ADD COLUMN applied_rule_id BIGINT REFERENCES benefit_policy_rules(id) ON DELETE RESTRICT,
    ADD COLUMN applied_context VARCHAR(20),
    ADD COLUMN bucket_snapshot_json JSONB,
    ADD COLUMN calculation_version INTEGER NOT NULL DEFAULT 1;

-- Append-only reversible bucket ledger prevents double consumption.
CREATE TABLE benefit_bucket_consumptions (
    id                  BIGSERIAL PRIMARY KEY,
    claim_id            BIGINT NOT NULL REFERENCES claims(id) ON DELETE RESTRICT,
    claim_line_id       BIGINT NOT NULL REFERENCES claim_lines(id) ON DELETE RESTRICT,
    policy_id           BIGINT NOT NULL REFERENCES benefit_policies(id) ON DELETE RESTRICT,
    member_id           BIGINT NOT NULL REFERENCES members(id) ON DELETE RESTRICT,
    bucket_id           BIGINT NOT NULL REFERENCES benefit_limit_buckets(id) ON DELETE RESTRICT,
    period_start        DATE NOT NULL,
    period_end          DATE,
    approved_amount     NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (approved_amount >= 0),
    times_consumed      INTEGER NOT NULL DEFAULT 0 CHECK (times_consumed >= 0),
    status              VARCHAR(20) NOT NULL,
    calculation_version INTEGER NOT NULL,
    idempotency_key     VARCHAR(180) NOT NULL UNIQUE,
    reversal_of_id      BIGINT REFERENCES benefit_bucket_consumptions(id) ON DELETE RESTRICT,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    committed_at        TIMESTAMP,
    reversed_at         TIMESTAMP,
    CONSTRAINT chk_bucket_consumption_status CHECK (status IN ('RESERVED','COMMITTED','REVERSED'))
);

CREATE INDEX idx_bucket_consumption_balance
    ON benefit_bucket_consumptions(member_id, bucket_id, period_start, period_end, status);
CREATE INDEX idx_bucket_consumption_claim
    ON benefit_bucket_consumptions(claim_id, claim_line_id, calculation_version);

CREATE TABLE benefit_configuration_audit (
    id          BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(40) NOT NULL,
    entity_id   BIGINT NOT NULL,
    action      VARCHAR(30) NOT NULL,
    user_id     BIGINT REFERENCES users(id),
    reason      VARCHAR(500),
    before_data JSONB,
    after_data  JSONB,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE claim_encounter_audit (
    id                      BIGSERIAL PRIMARY KEY,
    claim_id                BIGINT NOT NULL REFERENCES claims(id) ON DELETE RESTRICT,
    previous_encounter_type VARCHAR(20),
    new_encounter_type      VARCHAR(20) NOT NULL,
    user_id                 BIGINT REFERENCES users(id),
    reason                  VARCHAR(500),
    before_calculation      JSONB,
    after_calculation       JSONB,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
