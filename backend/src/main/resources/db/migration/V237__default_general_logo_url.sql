UPDATE system_settings
SET setting_value = '/images/waad-logo.png',
    default_value = '/images/waad-logo.png',
    description = 'Public logo URL used by the frontend shell and reports.',
    updated_at = NOW()
WHERE setting_key = 'LOGO_URL'
  AND (setting_value IS NULL OR btrim(setting_value) = '');
