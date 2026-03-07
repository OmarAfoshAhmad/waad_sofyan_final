-- ═══════════════════════════════════════════════════════════════════════════
-- V100: Seed feature flags and configurable UI/system settings
--
-- Feature Flags: control claim entry modes (DB-driven, toggled from Settings)
-- System Settings: UI appearance, member numbering, eligibility rules
-- ═══════════════════════════════════════════════════════════════════════════

-- ────────────────────────────────────────────────────────────────────────────
-- 1. Feature Flags — Claim Entry Modes
-- ────────────────────────────────────────────────────────────────────────────
INSERT INTO feature_flags (flag_key, flag_name, description, enabled, created_by, created_at, updated_at)
VALUES
    (
        'PROVIDER_PORTAL_ENABLED',
        'بوابة الخدمة المباشرة',
        'تفعيل بوابة إدخال المطالبات المباشرة عبر مزودي الخدمة. عند التعطيل يعمل النظام في وضع الدفعات الشهرية فقط.',
        false,
        'SYSTEM',
        NOW(),
        NOW()
    ),
    (
        'DIRECT_CLAIM_SUBMISSION_ENABLED',
        'التقديم المباشر للمطالبات',
        'السماح بإنشاء مطالبات فردية مباشرة من بوابة المزود. يتطلب تفعيل PROVIDER_PORTAL_ENABLED أيضاً.',
        false,
        'SYSTEM',
        NOW(),
        NOW()
    ),
    (
        'BATCH_CLAIMS_ENABLED',
        'نظام الدفعات الشهرية',
        'تفعيل إدخال المطالبات عبر الدفعات الشهرية. هذا هو المسار الأساسي الحالي لإدخال المطالبات.',
        true,
        'SYSTEM',
        NOW(),
        NOW()
    )
ON CONFLICT (flag_key) DO NOTHING;

-- ────────────────────────────────────────────────────────────────────────────
-- 2. System Settings — UI / Appearance
-- ────────────────────────────────────────────────────────────────────────────
INSERT INTO system_settings (setting_key, setting_value, value_type, description, category, is_editable, default_value, validation_rules, active, created_at, updated_at)
VALUES
    (
        'LOGO_URL',
        '',
        'STRING',
        'رابط شعار النظام المعروض في الشريط العلوي. اتركه فارغاً لاستخدام الشعار الافتراضي.',
        'UI',
        true,
        '',
        NULL,
        true,
        NOW(),
        NOW()
    ),
    (
        'FONT_FAMILY',
        'Tajawal',
        'STRING',
        'نوع الخط الأساسي للنظام. يجب أن يكون من الخطوط المدعومة.',
        'UI',
        true,
        'Tajawal',
        'allowed:Tajawal,Cairo,Almarai,Noto Naskh Arabic',
        true,
        NOW(),
        NOW()
    ),
    (
        'FONT_SIZE_BASE',
        '14',
        'INTEGER',
        'حجم الخط الأساسي للنظام بالبكسل.',
        'UI',
        true,
        '14',
        'min:12,max:18',
        true,
        NOW(),
        NOW()
    ),
    (
        'SYSTEM_NAME_AR',
        'نظام واعد الطبي',
        'STRING',
        'اسم النظام باللغة العربية — يظهر في العنوان والتقارير.',
        'UI',
        true,
        'نظام واعد الطبي',
        'maxlength:60',
        true,
        NOW(),
        NOW()
    ),
    (
        'SYSTEM_NAME_EN',
        'TBA WAAD System',
        'STRING',
        'System name in English — appears in reports and API responses.',
        'UI',
        true,
        'TBA WAAD System',
        'maxlength:60',
        true,
        NOW(),
        NOW()
    )
ON CONFLICT (setting_key) DO NOTHING;

-- ────────────────────────────────────────────────────────────────────────────
-- 3. System Settings — Member Numbering
-- ────────────────────────────────────────────────────────────────────────────
INSERT INTO system_settings (setting_key, setting_value, value_type, description, category, is_editable, default_value, validation_rules, active, created_at, updated_at)
VALUES
    (
        'BENEFICIARY_NUMBER_FORMAT',
        'PREFIX_SEQUENCE',
        'STRING',
        'صيغة ترقيم المستفيدين: PREFIX_SEQUENCE = بادئة + تسلسل، YEAR_SEQUENCE = سنة + تسلسل، SEQUENTIAL = تسلسل فقط.',
        'MEMBERS',
        true,
        'PREFIX_SEQUENCE',
        'allowed:PREFIX_SEQUENCE,YEAR_SEQUENCE,SEQUENTIAL',
        true,
        NOW(),
        NOW()
    ),
    (
        'BENEFICIARY_NUMBER_PREFIX',
        'MEM',
        'STRING',
        'البادئة الثابتة في رقم المستفيد (مثال: MEM يُنتج MEM000001). تُستخدم مع PREFIX_SEQUENCE فقط.',
        'MEMBERS',
        true,
        'MEM',
        'maxlength:10',
        true,
        NOW(),
        NOW()
    ),
    (
        'BENEFICIARY_NUMBER_DIGITS',
        '6',
        'INTEGER',
        'عدد الأرقام في الجزء التسلسلي من رقم المستفيد (مثال: 6 يُنتج 000001).',
        'MEMBERS',
        true,
        '6',
        'min:4,max:10',
        true,
        NOW(),
        NOW()
    )
ON CONFLICT (setting_key) DO NOTHING;

-- ────────────────────────────────────────────────────────────────────────────
-- 4. System Settings — Eligibility Rules
-- ────────────────────────────────────────────────────────────────────────────
INSERT INTO system_settings (setting_key, setting_value, value_type, description, category, is_editable, default_value, validation_rules, active, created_at, updated_at)
VALUES
    (
        'ELIGIBILITY_STRICT_MODE',
        'false',
        'BOOLEAN',
        'الوضع الصارم للأهلية: عند التفعيل يُرفض أي طلب خارج نطاق التغطية تلقائياً دون استثناء.',
        'ELIGIBILITY',
        true,
        'false',
        NULL,
        true,
        NOW(),
        NOW()
    ),
    (
        'WAITING_PERIOD_DAYS_DEFAULT',
        '30',
        'INTEGER',
        'فترة الانتظار الافتراضية بالأيام عند إضافة مستفيد جديد لوثيقة.',
        'ELIGIBILITY',
        true,
        '30',
        'min:0,max:365',
        true,
        NOW(),
        NOW()
    ),
    (
        'ELIGIBILITY_GRACE_PERIOD_DAYS',
        '7',
        'INTEGER',
        'فترة السماح بالأيام بعد انتهاء صلاحية الوثيقة قبل رفض المطالبات تلقائياً.',
        'ELIGIBILITY',
        true,
        '7',
        'min:0,max:30',
        true,
        NOW(),
        NOW()
    )
ON CONFLICT (setting_key) DO NOTHING;
