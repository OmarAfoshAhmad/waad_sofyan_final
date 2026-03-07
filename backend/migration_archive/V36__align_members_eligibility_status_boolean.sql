-- V36: Align members.eligibility_status type with Member entity (Boolean)

ALTER TABLE members
    ALTER COLUMN eligibility_status TYPE BOOLEAN
    USING (
        CASE
            WHEN eligibility_status IS NULL THEN NULL
            WHEN lower(trim(eligibility_status::text)) IN ('true', 't', '1', 'yes', 'y', 'eligible', 'active') THEN TRUE
            WHEN lower(trim(eligibility_status::text)) IN ('false', 'f', '0', 'no', 'n', 'ineligible', 'inactive') THEN FALSE
            ELSE FALSE
        END
    );

UPDATE members
SET eligibility_status = TRUE
WHERE eligibility_status IS NULL;

ALTER TABLE members
    ALTER COLUMN eligibility_status SET NOT NULL;

ALTER TABLE members
    ALTER COLUMN eligibility_status SET DEFAULT TRUE;
