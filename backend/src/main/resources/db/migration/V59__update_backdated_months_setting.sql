-- =================================================================================
-- V59: Update Claim Backdated Months Setting
-- Description: Sets the CLAIM_BACKDATED_MONTHS setting to 15 months to meet 
--              business requirements for retroactive claim submission.
-- =================================================================================

INSERT INTO system_settings (setting_key, setting_value, description, group_name, active)
VALUES ('CLAIM_BACKDATED_MONTHS', '15', 'عدد الأشهر المسموح بها للمطالبات القديمة', 'CLAIM', true)
ON CONFLICT (setting_key) DO UPDATE
SET setting_value = EXCLUDED.setting_value,
    updated_at = NOW();
