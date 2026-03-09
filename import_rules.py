import psycopg2
import json
from datetime import datetime

def import_rules():
    # Database configuration from application.yml
    db_config = {
        "dbname": "tba_waad_system",
        "user": "postgres",
        "password": "12345",
        "host": "localhost",
        "port": "5432"
    }

    policy_id = 1  # وثيقة جليانة

    # Rule definitions mapped to database category IDs
    # Format: (cat_id, coverage, amt_limit, times_limit, requires_pa, notes)
    rules_to_import = [
        # --- العمليات (Operations) ---
        (1, 75, None, None, True, "أجور الجراحين والعمليات الكبرى"),
        (16, 75, None, None, True, "غرفة العمليات والمواد الجراحية"),
        
        # --- الإيواء (Inpatient) ---
        (2, 75, None, None, False, "الإقامة والتمريض (غرفة خاصة)"),
        (15, 75, None, None, False, "العناية الفائقة ICU/CCU"),
        (11, 75, None, None, True, "عناية القلب وقسطرة القلب"),
        (9, 75, None, None, False, "الجراحة العامة - إيواء"),
        
        # --- الأمومة (Maternity) ---
        (12, 75, 4000, 1, True, "الولادة الطبيعية والقيصرية (حد أقصى 4000)"),
        
        # --- الحالات المستعصية (Chronic/Major) ---
        (13, 100, None, None, True, "علاج الأورام (تغطية كاملة)"),
        (14, 75, None, None, True, "غسيل الكلى"),
        (30, 75, 25000, None, True, "إصابات العمل"),
        
        # --- العيادات الخارجية (Outpatient) ---
        (19, 75, 3000, None, False, "كشوفات الأخصائيين والمستشارين (سقف مشترك)"),
        (25, 75, 15000, None, False, "أدوية العيادات الخارجية بوصفة طبية"),
        (31, 75, 3000, None, True, "الطب النفسي (أدوية وجلسات)"),
        
        # --- التحاليل والأشعة (Diagnostics) ---
        (20, 75, 3000, None, False, "تحاليل ومختبرات (خارجي)"),
        (7, 75, 3000, None, False, "أشعة تشخيصية (روتينية)"),
        (21, 75, 1500, None, True, "أشعة تخصصية (MRI/CT/PET)"),
        
        # --- العلاج الطبيعي (Physiotherapy) ---
        (22, 75, 10000, 20, True, "جلسات العلاج الطبيعي (بحد أقصى 20 ليلة/جلسة)"),
        
        # --- الأسنان (Dental) ---
        (5, 75, None, None, False, "أسنان وقائي (كشف، خلع، حشو، تنظيف)"),
        (6, 50, None, None, True, "أسنان تجميلي (تركيبات، تقويم، زراعة)"),
        (23, 75, None, None, False, "أسنان - طوارئ"),
        
        # --- العيون (Vision) ---
        (24, 75, 500, 1, False, "نظارات طبية (مرة واحدة سنوياً)"),
        
        # --- الطوارئ والإخلاء (Emergency/Evacuation) ---
        (27, 75, None, None, False, "الإسعاف المحلي"),
        (28, 75, None, None, True, "الإخلاء الطبي والمرافقين وتذاكر العائلة"),
        
        # --- فئات إضافية لضمان الشمولية ---
        (4, 75, 3000, None, False, "تحاليل طبية - عام"),
        (8, 75, 10000, 20, True, "علاج طبيعي - عام"),
        (3, 75, 3000, None, False, "خدمات عيادات - عام"),
        (10, 75, None, None, True, "أوعية دموية - إيواء"),
        (17, 75, None, None, True, "غرفة عمليات - أوعية"),
        (18, 75, None, None, True, "غرفة عمليات - تجميل"),
        (26, 75, 3000, None, False, "علاج ألم - عيادة"),
        (29, 75, 15000, None, False, "أدوية مزمنة"),
        # Totaling around 32-33 logic nodes from the doc
    ]

    conn = None
    try:
        conn = psycopg2.connect(**db_config)
        cur = conn.cursor()

        # 1. Clear existing rules for this policy to ensure a clean slate
        print(f"Cleaning existing rules for policy ID {policy_id}...")
        cur.execute("DELETE FROM benefit_policy_rules WHERE benefit_policy_id = %s", (policy_id,))
        
        # 2. Insert new rules
        print("Inserting 33 rules from jaliyana document...")
        query = """
            INSERT INTO benefit_policy_rules 
            (benefit_policy_id, medical_category_id, coverage_percent, amount_limit, times_limit, requires_pre_approval, notes, active, created_at, updated_at)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, NOW(), NOW())
        """
        
        for rule in rules_to_import:
            cur.execute(query, (
                policy_id, 
                rule[0], 
                rule[1], 
                rule[2], 
                rule[3], 
                rule[4], 
                rule[5], 
                True # active
            ))

        conn.commit()
        print(f"Successfully imported {len(rules_to_import)} rules into the database.")
        cur.close()

    except Exception as e:
        if conn:
            conn.rollback()
        print(f"Error during import: {e}")
    finally:
        if conn:
            conn.close()

if __name__ == "__main__":
    import_rules()
