$env:PGPASSWORD = 'postgres'
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -d tba_waad_system -c "SELECT column_name, data_type FROM information_schema.columns WHERE table_name='pre_authorization_attachments' ORDER BY ordinal_position;"
