-- Ensure claim_attachments.attachment_type is aligned with ClaimAttachmentType enum
-- Fixes provider submit flow where MEDICAL_REPORT upload fails on legacy check constraint

DO $$
DECLARE c RECORD;
BEGIN
    FOR c IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'claim_attachments'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) ILIKE '%attachment_type%'
    LOOP
        EXECUTE format('ALTER TABLE claim_attachments DROP CONSTRAINT IF EXISTS %I', c.conname);
    END LOOP;
END $$;

UPDATE claim_attachments
SET attachment_type = 'XRAY'
WHERE attachment_type = 'RADIOLOGY';

ALTER TABLE claim_attachments
ADD CONSTRAINT chk_claim_attachments_attachment_type
CHECK (attachment_type IN ('INVOICE', 'MEDICAL_REPORT', 'PRESCRIPTION', 'LAB_RESULT', 'XRAY', 'OTHER'));
