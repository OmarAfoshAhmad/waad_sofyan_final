-- Seed the editable semantic UI palette after the existing V131 migration chain.
INSERT INTO system_settings
    (setting_key, setting_value, value_type, description, category, is_editable,
     default_value, validation_rules, active, created_at, updated_at)
VALUES
    ('PRIMARY_COLOR',   '#00838F', 'STRING', 'Primary brand color',                 'UI', true, '#00838F', NULL, true, NOW(), NOW()),
    ('SECONDARY_COLOR', '#42A5F5', 'STRING', 'Secondary action color',              'UI', true, '#42A5F5', NULL, true, NOW(), NOW()),
    ('INFO_COLOR',      '#00A2AE', 'STRING', 'Informational semantic color',         'UI', true, '#00A2AE', NULL, true, NOW(), NOW()),
    ('SUCCESS_COLOR',   '#00A854', 'STRING', 'Success and approval semantic color', 'UI', true, '#00A854', NULL, true, NOW(), NOW()),
    ('WARNING_COLOR',   '#FFBF00', 'STRING', 'Warning and review semantic color',    'UI', true, '#FFBF00', NULL, true, NOW(), NOW()),
    ('ERROR_COLOR',     '#F04134', 'STRING', 'Error and rejection semantic color',  'UI', true, '#F04134', NULL, true, NOW(), NOW())
ON CONFLICT (setting_key) DO NOTHING;
