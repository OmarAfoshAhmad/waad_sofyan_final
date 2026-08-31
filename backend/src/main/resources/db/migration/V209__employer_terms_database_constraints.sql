-- E-09 closure gate: two rules EmployerService.validateEmployerTerms already
-- enforces in Java had no matching CHECK. Java enforcing a rule the database
-- does not is exactly the mismatch the constitution names as a bug -- any
-- write that reaches this table outside EmployerService (an import path, a
-- script, a future service) passes with no rule applied at all.
--
-- NOT VALID + a separate VALIDATE, the pattern already used elsewhere in this
-- schema for adding a constraint to a live table: existing rows are not
-- re-scanned at ALTER time (no table lock spike), and VALIDATE CONSTRAINT
-- checks them afterwards without blocking concurrent writes.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'employers' AND constraint_name = 'chk_employer_contract_period'
    ) THEN
        ALTER TABLE employers
            ADD CONSTRAINT chk_employer_contract_period
            CHECK (contract_end_date IS NULL OR contract_start_date IS NULL
                   OR contract_end_date >= contract_start_date)
            NOT VALID;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'employers' AND constraint_name = 'chk_employer_max_member_limit_positive'
    ) THEN
        ALTER TABLE employers
            ADD CONSTRAINT chk_employer_max_member_limit_positive
            CHECK (max_member_limit IS NULL OR max_member_limit > 0)
            NOT VALID;
    END IF;
END $$;

ALTER TABLE employers VALIDATE CONSTRAINT chk_employer_contract_period;
ALTER TABLE employers VALIDATE CONSTRAINT chk_employer_max_member_limit_positive;
