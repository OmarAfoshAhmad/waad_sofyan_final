-- Align pre_authorizations.request_date with PreAuthorization.requestDate (LocalDate)
-- Hibernate validate expects DATE, but legacy schema contains TIMESTAMP

ALTER TABLE pre_authorizations
    ALTER COLUMN request_date TYPE DATE
    USING request_date::DATE;
