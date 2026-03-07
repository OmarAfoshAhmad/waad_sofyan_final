ALTER TABLE benefit_policies
    ALTER COLUMN policy_name DROP NOT NULL;

ALTER TABLE benefit_policies
    ALTER COLUMN effective_date DROP NOT NULL;
