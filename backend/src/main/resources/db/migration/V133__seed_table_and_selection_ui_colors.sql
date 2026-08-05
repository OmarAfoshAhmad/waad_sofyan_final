-- Add the table and selection palette keys introduced by the appearance settings UI.
-- This is intentionally separate from V132 because V132 may already be applied in production.
INSERT INTO system_settings
    (setting_key, setting_value, value_type, description, category, is_editable,
     default_value, validation_rules, active, created_at, updated_at)
VALUES
    ('TABLE_HEADER_BG',   '#E0F2F1',                 'STRING', 'Table header background color',             'UI', true, '#E0F2F1',                 NULL, true, NOW(), NOW()),
    ('TABLE_HEADER_TEXT', '#004D50',                 'STRING', 'Table header text color',                   'UI', true, '#004D50',                 NULL, true, NOW(), NOW()),
    ('TABLE_ROW_EVEN',    'rgba(224,242,241,0.45)',  'STRING', 'Alternating table row color',               'UI', true, 'rgba(224,242,241,0.45)',  NULL, true, NOW(), NOW()),
    ('SELECTION_COLOR',   'rgba(0,131,143,0.08)',    'STRING', 'Selected row and item highlight color',     'UI', true, 'rgba(0,131,143,0.08)',    NULL, true, NOW(), NOW())
ON CONFLICT (setting_key) DO NOTHING;
