import subprocess

psql = r'C:\Program Files\PostgreSQL\18\bin\psql.exe'
db = 'tba_waad_system'
user = 'postgres'

def run(sql):
    result = subprocess.run([psql, '-U', user, '-d', db, '-c', sql], capture_output=True, text=True)
    return result.stdout + result.stderr

print("=== ALL RULES (all policies) ===")
print(run("SELECT bpr.id, bpr.benefit_policy_id, mc.code, mc.name, mc.parent_id, bpr.coverage_percent, bpr.amount_limit, bpr.times_limit, bpr.active FROM benefit_policy_rules bpr JOIN medical_categories mc ON mc.id = bpr.medical_category_id ORDER BY bpr.benefit_policy_id, bpr.id;"))

print("=== WE-007 service info ===")
print(run("SELECT id, code, name, category_id FROM medical_services WHERE code='WE-007';"))

print("=== All medical services ===")
print(run("SELECT id, code, name, category_id FROM medical_services WHERE name ILIKE '%طبيعي%' OR code LIKE 'WE%' ORDER BY id;"))

print("=== BENEFIT POLICIES ===")
print(run("SELECT id, name, default_coverage_percent FROM benefit_policies ORDER BY id;"))
