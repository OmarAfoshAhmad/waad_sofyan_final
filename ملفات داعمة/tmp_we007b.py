import subprocess

psql = r'C:\Program Files\PostgreSQL\18\bin\psql.exe'
db = 'tba_waad_system'
user = 'postgres'

def run(sql):
    result = subprocess.run([psql, '-U', user, '-d', db, '-c', sql], capture_output=True, text=True)
    return result.stdout + result.stderr

print("=== WE-007 pricing items ===")
print(run("""
SELECT pi.id, pi.service_code, pi.service_name, pi.medical_category_id, 
       mc.code as cat_code, mc.name as cat_name, mc.parent_id as cat_parent,
       par.code as parent_code
FROM provider_contract_pricing_items pi 
LEFT JOIN medical_categories mc ON mc.id = pi.medical_category_id
LEFT JOIN medical_categories par ON par.id = mc.parent_id
WHERE pi.service_code='WE-007' LIMIT 10;
"""))

print("=== Rules NOW active ===")
print(run("""
SELECT bpr.id, mc.code, mc.name, mc.parent_id, bpr.coverage_percent, bpr.amount_limit, bpr.times_limit, bpr.active
FROM benefit_policy_rules bpr JOIN medical_categories mc ON mc.id = bpr.medical_category_id
WHERE bpr.benefit_policy_id = 1
ORDER BY mc.parent_id NULLS FIRST, mc.code;
"""))
