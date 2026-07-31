SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'claim_lines_applied_rule_id_fkey';
