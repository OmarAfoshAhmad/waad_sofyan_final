INSERT INTO system_settings
    (setting_key, setting_value, value_type, description, category,
     is_editable, default_value, validation_rules, active, created_at, updated_at)
VALUES
    ('DATE_DISPLAY_FORMAT', 'dd/MM/yyyy', 'STRING',
     'Date display format used across the user interface', 'UI',
     true, 'dd/MM/yyyy', 'enum:dd/MM/yyyy|dd-MM-yyyy|yyyy-MM-dd', true, NOW(), NOW())
ON CONFLICT (setting_key) DO UPDATE
SET default_value = EXCLUDED.default_value,
    validation_rules = EXCLUDED.validation_rules,
    active = true,
    updated_at = NOW();
