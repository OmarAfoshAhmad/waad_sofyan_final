import subprocess

psql = r'C:\Program Files\PostgreSQL\18\bin\psql.exe'
db = 'tba_waad_system'
user = 'postgres'

def run(sql):
    result = subprocess.run([psql, '-U', user, '-d', db, '-c', sql], capture_output=True, text=True)
    return result.stdout + result.stderr

print("=== Pricing items columns ===")
print(run("SELECT column_name FROM information_schema.columns WHERE table_name='provider_contract_pricing_items' ORDER BY ordinal_position;"))

print("=== WE-007 pricing items ===")
print(run("SELECT id, service_code, service_name, category_id, mc.code as cat_code, mc.parent_id as cat_parent FROM provider_contract_pricing_items pi LEFT JOIN medical_categories mc ON mc.id = pi.category_id WHERE pi.service_code='WE-007' LIMIT 10;"))

print("=== All physio pricing items ===")
print(run("SELECT service_code, service_name, category_id, mc.code as cat_code FROM provider_contract_pricing_items pi LEFT JOIN medical_categories mc ON mc.id = pi.category_id WHERE service_code ILIKE '%WE%' OR service_name ILIKE '%طبيعي%' ORDER BY service_code LIMIT 20;"))
