INSERT INTO system_settings
    (setting_key, setting_value, value_type, description, category, is_editable,
     default_value, validation_rules, active, created_at, updated_at)
VALUES
    ('BUSINESS_TYPE', 'إدارة النفقات الطبية', 'STRING',
     'Company business/activity type shown in reports and public branding.',
     'UI', true, 'إدارة النفقات الطبية', NULL, true, NOW(), NOW())
ON CONFLICT (setting_key) DO NOTHING;
