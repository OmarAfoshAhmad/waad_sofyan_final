import psycopg2
import os
from datetime import datetime
from dotenv import load_dotenv

load_dotenv()

def get_db_connection():
    return psycopg2.connect(
        dbname=os.getenv('POSTGRES_DB', 'tba_waad_system'),
        user=os.getenv('POSTGRES_USER', 'postgres'),
        password=os.getenv('DB_PASSWORD', '12345'),
        host='localhost'
    )

def final_hard_reset():
    conn = get_db_connection()
    cur = conn.cursor()
    
    try:
        print("Starting Comprehensive Final Reset of Medical Categories...")
        
        # 1. Clear everything
        cur.execute("TRUNCATE TABLE medical_category_roots RESTART IDENTITY CASCADE;")
        cur.execute("TRUNCATE TABLE medical_service_categories RESTART IDENTITY CASCADE;")
        cur.execute("DELETE FROM benefit_policy_rules;") # Clear rules since they link to old IDs
        cur.execute("TRUNCATE TABLE medical_categories RESTART IDENTITY CASCADE;")
        
        # 2. Insert 8 Main Roots (The 8 Dimensions)
        roots = [
            ('ROOT-OP', 'خارج المستشفى (العيادات)', 'Outpatient (OP)'),
            ('ROOT-IP', 'داخل المستشفى (الإيواء والعمليات)', 'Inpatient (IP)'),
            ('ROOT-DENT', 'خدمات الأسنان', 'Dental Services'),
            ('ROOT-VIS', 'العيون والنظارات', 'Vision & Optical'),
            ('ROOT-MAT', 'الأمومة والولادة', 'Maternity'),
            ('ROOT-CHR', 'الأمراض المزمنة', 'Chronic Diseases'),
            ('ROOT-EMER', 'الطوارئ والإسعاف', 'Emergency & Ambulance'),
            ('ROOT-OTH', 'منافع أخرى ومستلزمات', 'Other Benefits & Supplies')
        ]
        
        root_ids = {}
        for code, name, name_en in roots:
            cur.execute("""
                INSERT INTO medical_categories (code, name, name_ar, name_en, active, deleted, created_at, updated_at)
                VALUES (%s, %s, %s, %s, true, false, %s, %s) RETURNING id;
            """, (code, name, name, name_en, datetime.now(), datetime.now()))
            root_ids[code] = cur.fetchone()[0]
            
        print(f"Inserted 8 Root Categories. IDs: {list(root_ids.values())}")
        
        # 3. Insert Sub-categories (Benefit Items from Jalyana Doc)
        # Structure: (Name, Code, List of Root Codes)
        subs = [
            ('الكشوفات والاستشارات الطبية', 'SUB-CONSULT', ['ROOT-OP', 'ROOT-IP']),
            ('التحاليل الطبية والمخبرية', 'SUB-LAB', ['ROOT-OP', 'ROOT-IP']),
            ('الأشعة والتصوير الطبي', 'SUB-RAD', ['ROOT-OP', 'ROOT-IP']),
            ('الأدوية والصيدلية', 'SUB-PHARMA', ['ROOT-OP', 'ROOT-IP', 'ROOT-CHR']),
            ('الإقامة والتمريض (غرفة خاصة)', 'SUB-STAY', ['ROOT-IP', 'ROOT-MAT']),
            ('العمليات الجراحية والتخدير', 'SUB-SURGERY', ['ROOT-IP', 'ROOT-MAT']),
            ('العناية الفائقة ICU/CCU', 'SUB-ICU', ['ROOT-IP']),
            ('العلاج الطبيعي', 'SUB-PHYSIO', ['ROOT-OP', 'ROOT-IP']),
            ('الأسنان - وقائي وعلاجي', 'SUB-DENT-REG', ['ROOT-DENT']),
            ('الأسنان - تجميلي وتركيبات', 'SUB-DENT-COS', ['ROOT-DENT']),
            ('النظارات الطبية والعدسات', 'SUB-VISION', ['ROOT-VIS']),
            ('متابعة الحمل والولادة', 'SUB-MATERNITY', ['ROOT-MAT']),
            ('خدمات الحالات الطارئة', 'SUB-EMERGENCY', ['ROOT-EMER']),
            ('الإسعاف المحلي والطبي', 'SUB-AMBULANCE', ['ROOT-EMER']),
            ('المستلزمات الطبية والأجهزة', 'SUB-SUPPLIES', ['ROOT-OTH']),
            ('الطب النفسي والجلسات', 'SUB-PSYCH', ['ROOT-OP']),
            ('غسيل الكلى', 'SUB-DIALYSIS', ['ROOT-CHR', 'ROOT-IP']),
            ('علاج الأورام', 'SUB-ONCOLOGY', ['ROOT-CHR', 'ROOT-IP']),
            ('إصابات العمل', 'SUB-WORK-INJ', ['ROOT-OTH'])
        ]
        
        print("Inserting sub-categories and linking to multiple roots...")
        for name, code, root_codes in subs:
            # Insert sub-category with the first root as parent (for legacy support)
            parent_id = root_ids[root_codes[0]]
            cur.execute("""
                INSERT INTO medical_categories (code, name, name_ar, parent_id, active, deleted, created_at, updated_at)
                VALUES (%s, %s, %s, %s, true, false, %s, %s) RETURNING id;
            """, (code, name, name, parent_id, datetime.now(), datetime.now()))
            cat_id = cur.fetchone()[0]
            
            # Link to ALL specified roots in many-to-many table
            for r_code in root_codes:
                r_id = root_ids[r_code]
                cur.execute("""
                    INSERT INTO medical_category_roots (category_id, root_id)
                    VALUES (%s, %s);
                """, (cat_id, r_id))
        
        conn.commit()
        print("Success: 8 Roots and 19 Sub-categories (Benefit Items) inserted and linked.")
        
    except Exception as e:
        conn.rollback()
        print(f"Error: {e}")
    finally:
        cur.close()
        conn.close()

if __name__ == "__main__":
    final_hard_reset()
