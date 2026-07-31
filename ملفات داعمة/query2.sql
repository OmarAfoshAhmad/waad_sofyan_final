SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'benefit_rule_buckets_rule_id_fkey';
