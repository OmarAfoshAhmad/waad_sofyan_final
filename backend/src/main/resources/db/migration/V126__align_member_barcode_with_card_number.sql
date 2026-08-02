-- Align beneficiary scanner identity with the modern card-number identity.
-- Business rule: every beneficiary (principal or dependent) is scanned by card_number.

UPDATE members
SET barcode = card_number
WHERE card_number IS NOT NULL
  AND (barcode IS NULL OR barcode <> card_number);

ALTER TABLE members
    ADD CONSTRAINT chk_members_barcode_equals_card_number
    CHECK (barcode IS NULL OR card_number IS NULL OR barcode = card_number);
