$env:PGPASSWORD = '12345'
$psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'

Write-Host '=== Flyway state (V113+) ==='
& $psql -U postgres -d tba_waad_system -c 'SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 8;'

Write-Host ''
Write-Host '=== provider_accounts for provider_id=1 ==='
& $psql -U postgres -d tba_waad_system -c 'SELECT id, running_balance, total_approved, total_paid FROM provider_accounts WHERE provider_id = 1;'

Write-Host ''
Write-Host '=== account_transactions for provider_id=1 ==='
& $psql -U postgres -d tba_waad_system -c 'SELECT at.id, at.transaction_type, at.amount, at.balance_before, at.balance_after FROM account_transactions at JOIN provider_accounts pa ON pa.id = at.provider_account_id WHERE pa.provider_id = 1 ORDER BY at.created_at ASC;'

Write-Host ''
Write-Host '=== check constraint on account_transactions ==='
& $psql -U postgres -d tba_waad_system -c "SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid = 'account_transactions'::regclass AND contype='c';"
