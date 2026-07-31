import subprocess
import sys

psql = r'C:\Program Files\PostgreSQL\18\bin\psql.exe'
db = 'tba_waad_system'
user = 'postgres'

def run(sql):
    result = subprocess.run([psql, '-U', user, '-d', db, '-c', sql], capture_output=True, text=True)
    return result.stdout + result.stderr

print("=== ALL RULES (policy_id=1) ===")
print(run("SELECT bpr.id, mc.id as cat_id, mc.code, mc.name, mc.parent_id, bpr.coverage_percent, bpr.amount_limit, bpr.times_limit FROM benefit_policy_rules bpr JOIN medical_categories mc ON mc.id = bpr.medical_category_id WHERE bpr.benefit_policy_id = 1 AND bpr.active = true ORDER BY bpr.id;"))

print("=== SERVICE WE-007 ===")
print(run("SELECT column_name FROM information_schema.columns WHERE table_name='benefit_policy_rules' ORDER BY ordinal_position;"))

print("=== ALL MEDICAL CATEGORIES (physio related) ===")
print(run("SELECT id, code, name, parent_id FROM medical_categories WHERE code ILIKE '%physio%' OR code ILIKE '%phys%' OR name ILIKE '%طبيعي%' ORDER BY id;"))

print("=== CATEGORIES HIERARCHY ===")
print(run("SELECT id, code, name, parent_id FROM medical_categories WHERE id IN (1, 51, 101, 201, 301, 401, 501, 601, 701) OR parent_id IN (1, 51) ORDER BY parent_id NULLS FIRST, id;"))
