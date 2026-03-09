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

def insert_subcategories():
    conn = get_db_connection()
    cur = conn.cursor()
    
    # ROOT IDs
    ROOTS = {
        'GENERAL': 49,
        'CONSULT': 50,
        'SURGERY': 51,
        'LAB': 52,
        'RAD': 53,
        'STAY': 54,
        'DRUGS': 55,
        'OTHER': 56
    }
    
    # Subcategories (Name, CodePrefix, ParentID)
    SUBS = [
        # SURGERY
        ('أجور الجراحين والعمليات', 'BEN-SURG-FEE', ROOTS['SURGERY']),
        ('غرفة العمليات والمواد الجراحية', 'BEN-SURG-ROOM', ROOTS['SURGERY']),
        ('تخدير ومتابعة جراحية', 'BEN-SURG-ANES', ROOTS['SURGERY']),
        
        # STAY
        ('الإقامة والتمريض (غرفة خاصة)', 'BEN-STAY-ROOM', ROOTS['STAY']),
        ('العناية الفائقة ICU/CCU', 'BEN-STAY-ICU', ROOTS['STAY']),
        ('إقامة ومتابعة طبية', 'BEN-STAY-MED', ROOTS['STAY']),
        
        # CONSULT
        ('كشوفات الأخصائيين والمستشارين', 'BEN-CONS-SPEC', ROOTS['CONSULT']),
        ('زيارات منزلية', 'BEN-CONS-HOME', ROOTS['CONSULT']),
        
        # LAB
        ('تحاليل ومختبرات روتينية', 'BEN-LAB-ROUT', ROOTS['LAB']),
        ('تحاليل تخصصية', 'BEN-LAB-SPEC', ROOTS['LAB']),
        
        # RAD
        ('أشعة تشخيصية (روتينية)', 'BEN-RAD-ROUT', ROOTS['RAD']),
        ('أشعة تخصصية (MRI/CT/PET)', 'BEN-RAD-SPEC', ROOTS['RAD']),
        
        # DRUGS
        ('أدوية العيادات الخارجية', 'BEN-DRUG-OP', ROOTS['DRUGS']),
        ('أدوية الأمراض المزمنة', 'BEN-DRUG-CHR', ROOTS['DRUGS']),
        
        # GENERAL / OTHER
        ('فتح ملف وادارة خدمات', 'BEN-GEN-FILE', ROOTS['GENERAL']),
        ('خدمات نقل اسعاف', 'BEN-OTH-AMB', ROOTS['OTHER']),
        ('مستلزمات طبية', 'BEN-OTH-SUPP', ROOTS['OTHER']),
        ('الأسنان', 'BEN-OTH-DENT', ROOTS['OTHER']),
        ('العيون والنظارات', 'BEN-OTH-VIS', ROOTS['OTHER']),
        ('الأمومة والولادة', 'BEN-OTH-MAT', ROOTS['OTHER'])
    ]
    
    try:
        print("Inserting sub-categories (Benefit Items)...")
        for name, code, parent_id in SUBS:
            cur.execute("""
                INSERT INTO medical_categories (code, name, name_ar, parent_id, active, deleted, created_at, updated_at)
                VALUES (%s, %s, %s, %s, true, false, %s, %s);
            """, (code, name, name, parent_id, datetime.now(), datetime.now()))
        
        conn.commit()
        print(f"Successfully inserted {len(SUBS)} sub-categories.")
        
    except Exception as e:
        conn.rollback()
        print(f"Error: {e}")
    finally:
        cur.close()
        conn.close()

if __name__ == "__main__":
    insert_subcategories()
