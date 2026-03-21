import subprocess

psql = r'C:\Program Files\PostgreSQL\18\bin\psql.exe'
db = 'tba_waad_system'
user = 'postgres'

def run(sql):
    result = subprocess.run([psql, '-U', user, '-d', db, '-c', sql], capture_output=True, text=True)
    return result.stdout + result.stderr

print("=== Tables related to services/pricing ===")
print(run("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename ILIKE '%pricing%' OR tablename ILIKE '%service%' ORDER BY tablename;"))

print("=== Pricing items for WE-007 ===")
print(run("SELECT id, service_code, service_name, category_id, price FROM provider_contract_pricing_items WHERE service_code='WE-007' LIMIT 5;"))

print("=== Pricing items columns ===")
print(run("SELECT column_name FROM information_schema.columns WHERE table_name='provider_contract_pricing_items' ORDER BY ordinal_position;"))
