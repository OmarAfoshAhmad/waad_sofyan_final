import json
import os

# Final comprehensive mapping based on "جدول منافع المنطقة الحرة جليانة.docx"
# Updated 2026-03-09 based on user request:
# - X-ray in Outpatient (SUB-RAD) -> No specific sub-limit (NULL)
# - Inpatient Accommodation (SUB-STAY) -> Specific ceiling (1500) as an exception
DATA = [
    # Category Code, Coverage %, Amount Limit, Times Limit, Notes
    ("ROOT-OP", 75, 3000, None, "سقف الكشوفات والأخصائيين والتحاليل خارج المستشفى"),
    ("ROOT-IP", 75, None, None, "الإيواء والعلاج داخل المستشفى (بدون سقف عام)"),
    ("ROOT-DENT", 75, None, None, "خدمات الأسنان العامة"),
    ("ROOT-MAT", 75, 4000, None, "سقف الولادة الطبيعية والقيصرية"),
    
    ("SUB-CONSULT", 75, 3000, None, "كشوفات الأخصائيين والممارسين (يتبع سقف العيادات)"),
    ("SUB-LAB", 75, 3000, None, "التحاليل والمختبرات (يتبع سقف العيادات)"),
    ("SUB-RAD", 75, None, None, "الأشعة في العيادات الخارجية (بدون سقف مخصص)"),
    ("SUB-PHARMA", 75, 15000, None, "الأدوية والعلاجات الروتينية"),
    ("SUB-STAY", 75, 1500, None, "قاعدة مستقلة للإقامة والتمريض (سقف استثنائي)"),
    ("SUB-SURGERY", 75, None, None, "العمليات الجراحية والتخدير"),
    ("SUB-ICU", 75, None, None, "العناية الفائقة ICU/CCU"),
    ("SUB-PHYSIO", 75, 10000, 20, "العلاج الطبيعي (بحد أقصى 20 زيارة)"),
    ("SUB-DENT-REG", 75, None, None, "كشف - خلع - حشو - تنظيف"),
    ("SUB-DENT-COS", 50, None, None, "تركيب - تقويم - زراعة"),
    ("SUB-VISION", 75, 500, None, "النظارات الطبية (نظارة واحدة في السنة)"),
    ("SUB-MATERNITY", 75, 4000, None, "الولادة ومتابعة الحمل"),
    ("SUB-EMERGENCY", 75, None, None, "حالات الطوارئ والإسعاف"),
    ("SUB-AMBULANCE", 75, None, None, "الإسعاف المحلي"),
    ("SUB-SUPPLIES", 75, 1500, None, "الأجهزة والمعدات الطبية"),
    ("SUB-PSYCH", 75, 3000, None, "الطب النفسي (أدوية وجلسات)"),
    ("SUB-DIALYSIS", 75, None, None, "غسيل الكلى"),
    ("SUB-ONCOLOGY", 100, None, None, "علاج الأورام - تغطية كاملة"),
    ("SUB-WORK-INJ", 75, 25000, None, "إصابات العمل"),
]

POLICY_ID = 1

sql_lines = [
    "-- Refined Benefit Policy Update for Jalyana (System Overhaul)",
    f"UPDATE benefit_policies SET annual_limit = 60000.00, default_coverage_percent = 75, status = 'ACTIVE' WHERE id = {POLICY_ID};",
    f"DELETE FROM benefit_policy_rules WHERE benefit_policy_id = {POLICY_ID};",
    ""
]

for code, coverage, limit, times, notes in DATA:
    l_val = f"{limit:.2f}" if limit is not None else "NULL"
    t_val = f"{times}" if times is not None else "NULL"
    
    sql = f"""INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, times_limit, notes, active, created_at, requires_pre_approval) 
SELECT {POLICY_ID}, id, {coverage}, {l_val}, {t_val}, '{notes}', true, CURRENT_TIMESTAMP, false 
FROM medical_categories WHERE code = '{code}';"""
    sql_lines.append(sql)

with open("apply_jalyana_rules_v2.sql", "w", encoding="utf-8") as f:
    f.write("\n".join(sql_lines))

print("Generated apply_jalyana_rules_v2.sql")
