-- ============================================================
-- V111: Remove AI classifier / facility price-preparation feature settings
-- ============================================================
-- The "تجهيز قوائم أسعار المرافق" (Facility Price List Preparation) and
-- "إعداد API للذكاء الاصطناعي" (AI Classifier Settings) frontend windows
-- were removed in full (per explicit product decision, 2026-07-24):
--   - frontend/src/pages/settings/FacilityPricePreparationPage.jsx
--   - frontend/src/pages/settings/AIKeySettingsPage.jsx
--   - their routes ('/settings/facility-price-preparation', '/settings/ai-key')
--   - their menu entry and the embedded AI-settings tab inside SystemSettingsPage
--
-- Both were explicitly labeled experimental/trial ("نسخة تجريبية" /
-- "تجريبي" chip in the menu). The AI classifier called a third-party AI
-- endpoint (OpenRouter) directly from the browser using a key read from
-- these settings — a design that was also a client-side credential
-- exposure risk, independent of the feature's experimental status.
--
-- These 4 settings rows have no other reader anywhere in the codebase
-- (verified: getBiobertApiUrl() and the AI_CLASSIFIER_* constants were only
-- ever referenced by the deleted feature and its own initialization code).
-- The database is experimental, so the rows are dropped outright rather
-- than archived. AI_CLASSIFIER_API_KEY held an empty value; no secret is
-- lost.

DELETE FROM system_settings
WHERE setting_key IN (
    'AI_CLASSIFIER_API_KEY',
    'AI_CLASSIFIER_MODEL',
    'AI_CLASSIFIER_ENDPOINT',
    'BIOBERT_API_URL'
);
