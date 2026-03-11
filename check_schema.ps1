$env:PGPASSWORD = '12345'
$psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'

Write-Host '=== account_transactions columns ==='
& $psql -U postgres -d tba_waad_system -c '\d account_transactions'

Write-Host '=== Flyway state (last 5) ==='
& $psql -U postgres -d tba_waad_system -c 'SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;'
