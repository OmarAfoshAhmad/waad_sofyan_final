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

def hard_reset_categories():
    conn = get_db_connection()
    cur = conn.cursor()
    
    try:
        print("Starting hard reset of medical categories...")
        
        # 1. Clear junction tables first due to foreign keys
        print("Cleaning junction tables...")
        cur.execute("TRUNCATE TABLE medical_category_roots RESTART IDENTITY CASCADE;")
        cur.execute("TRUNCATE TABLE medical_service_categories RESTART IDENTITY CASCADE;")
        cur.execute("DELETE FROM benefit_policy_rules WHERE medical_category_id IS NOT NULL;")
        
        # 2. Truncate the categories table
        print("Truncating medical_categories table...")
        cur.execute("TRUNCATE TABLE medical_categories RESTART IDENTITY CASCADE;")
        
        # 3. Insert the 8 New Agreed Root Categories
        new_roots = [
            ('ROOT-GENERAL', 'خدمات عامة وفتح ملف', 'General Services & File Opening'),
            ('ROOT-CONSULT', 'الكشوفات والاستشارات الطبية', 'Consultations & Medical Visits'),
            ('ROOT-SURGERY', 'العمليات والاجراءات الجراحية', 'Operations & Surgical Procedures'),
            ('ROOT-LAB', 'التحاليل والفحوصات المخبرية', 'Laboratory Tests & Analysis'),
            ('ROOT-RAD', 'التصوير الطبي والاشعة', 'Medical Imaging & X-Ray'),
            ('ROOT-STAY', 'الاقامة والتمريض', 'Inpatient Stay & Nursing'),
            ('ROOT-DRUGS', 'الصيدلية والادوية', 'Pharmacy & Drugs'),
            ('ROOT-OTHER', 'خدمات اخرى و مستلزمات', 'Other Services & Supplies')
        ]
        
        root_map = {} # Code -> ID
        print("Inserting 8 main root categories...")
        for code, name, name_en in new_roots:
            cur.execute("""
                INSERT INTO medical_categories (code, name, name_ar, name_en, active, deleted, created_at, updated_at)
                VALUES (%s, %s, %s, %s, true, false, %s, %s) RETURNING id;
            """, (code, name, name, name_en, datetime.now(), datetime.now()))
            root_map[code.split('-')[1]] = cur.fetchone()[0]
        
        # 4. Insert Subcategories linked to these roots
        subs = [
            ('أجور الجراحين والعمليات', 'BEN-SURG-FEE', root_map['SURGERY']),
            ('غرفة العمليات والمواد الجراحية', 'BEN-SURG-ROOM', root_map['SURGERY']),
            ('تخدير ومتابعة جراحية', 'BEN-SURG-ANES', root_map['SURGERY']),
            ('الإقامة والتمريض (غرفة خاصة)', 'BEN-STAY-ROOM', root_map['STAY']),
            ('العناية الفائقة ICU/CCU', 'BEN-STAY-ICU', root_map['STAY']),
            ('إقامة ومتابعة طبية', 'BEN-STAY-MED', root_map['STAY']),
            ('كشوفات الأخصائيين والمستشارين', 'BEN-CONS-SPEC', root_map['CONSULT']),
            ('زيارات منزلية', 'BEN-CONS-HOME', root_map['CONSULT']),
            ('تحاليل ومختبرات روتينية', 'BEN-LAB-ROUT', root_map['LAB']),
            ('تحاليل تخصصية', 'BEN-LAB-SPEC', root_map['LAB']),
            ('أشعة تشخيصية (روتينية)', 'BEN-RAD-ROUT', root_map['RAD']),
            ('أشعة تخصصية (MRI/CT/PET)', 'BEN-RAD-SPEC', root_map['RAD']),
            ('أدوية العيادات الخارجية', 'BEN-DRUG-OP', root_map['DRUGS']),
            ('أدوية الأمراض المزمنة', 'BEN-DRUG-CHR', root_map['DRUGS']),
            ('فتح ملف وادارة خدمات', 'BEN-GEN-FILE', root_map['GENERAL']),
            ('خدمات نقل اسعاف', 'BEN-OTH-AMB', root_map['OTHER']),
            ('مستلزمات طبية', 'BEN-OTH-SUPP', root_map['OTHER']),
            ('الأسنان', 'BEN-OTH-DENT', root_map['OTHER']),
            ('العيون والنظارات', 'BEN-OTH-VIS', root_map['OTHER']),
            ('الأمومة والولادة', 'BEN-OTH-MAT', root_map['OTHER'])
        ]
        
        print("Inserting sub-categories...")
        for name, code, parent_id in subs:
            cur.execute("""
                INSERT INTO medical_categories (code, name, name_ar, parent_id, active, deleted, created_at, updated_at)
                VALUES (%s, %s, %s, %s, true, false, %s, %s);
            """, (code, name, name, parent_id, datetime.now(), datetime.now()))
            
        conn.commit()
        print("Hard reset and re-initialization complete.")
        
    except Exception as e:
        conn.rollback()
        print(f"Error during hard reset: {e}")
    finally:
        cur.close()
        conn.close()

if __name__ == "__main__":
    hard_reset_categories()
