-- Prevent duplicate backlog entries for the same member/provider/date/reference
-- A partial unique index (WHERE legacy_reference_number IS NOT NULL) prevents accidental duplicate imports
-- while allowing multiple entries without a reference number (e.g. manual entries)

CREATE UNIQUE INDEX IF NOT EXISTS ux_claims_backlog_unique
    ON claims (member_id, provider_id, service_date, legacy_reference_number)
    WHERE legacy_reference_number IS NOT NULL
      AND is_backlog = TRUE;
