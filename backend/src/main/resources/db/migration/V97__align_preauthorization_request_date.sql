ALTER TABLE pre_authorizations
    ALTER COLUMN request_date TYPE DATE
    USING request_date::date;

UPDATE pre_authorizations
SET request_date = CURRENT_DATE
WHERE request_date IS NULL;

ALTER TABLE pre_authorizations
    ALTER COLUMN request_date SET NOT NULL;
