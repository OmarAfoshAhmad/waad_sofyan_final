-- ============================================================
-- V28: إصلاح أعمدة مفقودة في visits
-- كيان Visit يملك الحقلين لكن V15 لم يُعرّفهما في الجدول
-- ============================================================

-- ── 1. visits: إضافة عمود complaint المفقود ──────────────────────────────────
-- يسبب خطأ 500 عند جلب المطالبات: "column v2_0.complaint does not exist"
ALTER TABLE visits ADD COLUMN IF NOT EXISTS complaint TEXT;

-- ── 2. visits: إضافة عمود network_status المفقود ─────────────────────────────
ALTER TABLE visits ADD COLUMN IF NOT EXISTS network_status VARCHAR(30) DEFAULT 'IN_NETWORK';
