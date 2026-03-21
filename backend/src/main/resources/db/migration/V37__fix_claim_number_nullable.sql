-- ============================================================
-- V37: جعل claim_number اختياري (يُولَّد بعد الإدراج من الـ ID)
-- claim_number was NOT NULL but the JPA entity never set it.
-- We drop NOT NULL so the first INSERT can succeed,
-- then the application updates it to 'CLM-{id}' immediately after.
-- The UNIQUE constraint is preserved.
-- ============================================================

ALTER TABLE claims ALTER COLUMN claim_number DROP NOT NULL;
