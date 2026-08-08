-- Phase 9 — the new provider-payment write path (draft/post/reverse, account
-- adjustment) must not activate implicitly just because its UI and read
-- endpoints exist. Seeded disabled; Phase 11 turns it on deliberately.
INSERT INTO feature_flags (flag_key, flag_name, description, enabled, created_by, created_at, updated_at)
VALUES (
    'PROVIDER_PAYMENT_POSTING_ENABLED',
    'مسار دفعات مقدم الخدمة الجديد (كتابة)',
    'يتحكم بأزرار الإنشاء/الترحيل/العكس/التسوية في نموذج ProviderPayment الجديد. القراءات (الاقتراح، المطابقة) غير مشمولة بهذا العلم. يبقى معطلاً حتى المرحلة 11.',
    false, 'SYSTEM', NOW(), NOW()
)
ON CONFLICT (flag_key) DO NOTHING;
