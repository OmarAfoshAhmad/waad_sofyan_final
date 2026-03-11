$env:PGPASSWORD = "12345"
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -d tba_waad_system -c "SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint WHERE conname LIKE '%transaction%balance%';"
