SELECT conrelid::regclass AS table_name, conname AS foreign_key FROM pg_constraint WHERE confrelid = 'benefit_policy_rules'::regclass;
