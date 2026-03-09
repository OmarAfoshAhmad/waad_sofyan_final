import json
import sqlite3
import os

# Mapping of document row keywords to Category Codes
MAPPING = [
    {"keyword": "الإيواء والعلاج غرفة خاصة", "code": "SUB-STAY", "coverage": 75, "limit": None},
    {"keyword": "الدواء والمستلزمات الطبية", "code": "SUB-PHARMA", "coverage": 75, "limit": None},
    {"keyword": "العناية الفائقة وعناية القلب", "code": "SUB-ICU", "coverage": 75, "limit": None},
    {"keyword": "رسوم الأطباء والجراحيين", "code": "SUB-SURGERY", "coverage": 75, "limit": None},
    {"keyword": "علاج الاسنان بالطوارئ", "code": "SUB-EMERGENCY", "coverage": 75, "limit": None},
    {"keyword": "الاسعاف المحلي", "code": "SUB-AMBULANCE", "coverage": 75, "limit": None},
    {"keyword": "العلاج الطبيعي", "code": "SUB-PHYSIO", "coverage": 75, "limit": 10000},
    {"keyword": "تكلفة إصابات العمل", "code": "SUB-WORK-INJ", "coverage": 75, "limit": 25000},
    {"keyword": "التصوير بالرنين المغناطيسي والتصوير المقطعي", "code": "SUB-RAD", "coverage": 75, "limit": 1500}, # Using the outpatient limit from doc for safety or multiple rules
    {"keyword": "التصوير بالأشعة وتحليل العينات", "code": "SUB-LAB", "coverage": 75, "limit": 3000}, # Linked to "رسوم الاخصائيين... والتحاليل" limit
    {"keyword": "الطب النفسي", "code": "SUB-PSYCH", "coverage": 75, "limit": 3000},
    {"keyword": "الاورام", "code": "SUB-ONCOLOGY", "coverage": 100, "limit": None},
    {"keyword": "غسيل الكلوي", "code": "SUB-DIALYSIS", "coverage": 75, "limit": None},
    {"keyword": "الولادة الطبيعية والقيصرية", "code": "SUB-MATERNITY", "coverage": 75, "limit": 4000},
    {"keyword": "الاجهزة والمعدات الطبية", "code": "SUB-SUPPLIES", "coverage": 75, "limit": 1500},
    {"keyword": "علاج الاسنان الروتيني", "code": "SUB-DENT-REG", "coverage": 75, "limit": None},
    {"keyword": "حشو -تنظيف", "code": "SUB-DENT-REG", "coverage": 75, "limit": None},
    {"keyword": "تركيب-تقويم -زراعة", "code": "SUB-DENT-COS", "coverage": 50, "limit": None},
    {"keyword": "كشوفات العيون (النظارات الطبية)", "code": "SUB-VISION", "coverage": 75, "limit": 500},
]

# Root Category Fallbacks to ensure broad coverage
ROOT_FALLBACKS = [
    {"code": "ROOT-OP", "coverage": 75, "limit": 3000}, # From "رسوم الاخصائيين... 3000"
    {"code": "ROOT-IP", "coverage": 75, "limit": None},
    {"code": "ROOT-MAT", "coverage": 75, "limit": 4000},
    {"code": "ROOT-DENT", "coverage": 75, "limit": None},
]

POLICY_ID = 1

sql_lines = [
    "-- Benefit Policy Update for Jalyana",
    f"UPDATE benefit_policies SET annual_limit = 60000.00, default_coverage_percent = 75, status = 'ACTIVE' WHERE id = {POLICY_ID};",
    f"DELETE FROM benefit_policy_rules WHERE benefit_policy_id = {POLICY_ID};",
    ""
]

# I need category IDs. I'll use a subquery to find them.
# The database might be PostgreSQL or H2 depending on environment. 
# Based on common setup, it's likely PostgreSQL (standard) but I'll write standard SQL.

for item in MAPPING:
    sql = f"""INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active, created_at, requires_pre_approval) 
SELECT {POLICY_ID}, id, {item['coverage']}, {item['limit'] if item['limit'] else 'NULL'}, true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = '{item['code']}';"""
    sql_lines.append(sql)

for item in ROOT_FALLBACKS:
    # Use OR IGNORE or similar? No, I'll just skip if already added by subcategory (but roots are different)
    sql = f"""INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, active, created_at, requires_pre_approval) 
SELECT {POLICY_ID}, id, {item['coverage']}, {item['limit'] if item['limit'] else 'NULL'}, true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = '{item['code']}'
AND NOT EXISTS (SELECT 1 FROM benefit_policy_rules bpr2 JOIN medical_categories mc2 ON bpr2.medical_category_id = mc2.id 
                WHERE bpr2.benefit_policy_id = {POLICY_ID} AND mc2.code = '{item['code']}');"""
    sql_lines.append(sql)

with open("apply_jalyana_rules.sql", "w", encoding="utf-8") as f:
    f.write("\n".join(sql_lines))

print("Generated apply_jalyana_rules.sql")
