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

def reset_categories():
    conn = get_db_connection()
    cur = conn.cursor()
    
    try:
        # 1. Archive old categories (soft delete)
        print("Archiving old categories...")
        cur.execute("""
            UPDATE medical_categories 
            SET deleted = true, 
                deleted_at = %s,
                active = false 
            WHERE deleted = false;
        """, (datetime.now(),))
        print(f"Archived {cur.rowcount} categories.")
        
        # 2. Insert the 8 New Agreed Root Categories
        new_categories = [
            ('ROOT-GENERAL', 'خدمات عامة وفتح ملف', 'General Services & File Opening'),
            ('ROOT-CONSULT', 'الكشوفات والاستشارات الطبية', 'Consultations & Medical Visits'),
            ('ROOT-SURGERY', 'العمليات والاجراءات الجراحية', 'Operations & Surgical Procedures'),
            ('ROOT-LAB', 'التحاليل والفحوصات المخبرية', 'Laboratory Tests & Analysis'),
            ('ROOT-RAD', 'التصوير الطبي والاشعة', 'Medical Imaging & X-Ray'),
            ('ROOT-STAY', 'الاقامة والتمريض', 'Inpatient Stay & Nursing'),
            ('ROOT-DRUGS', 'الصيدلية والادوية', 'Pharmacy & Drugs'),
            ('ROOT-OTHER', 'خدمات اخرى و مستلزمات', 'Other Services & Supplies')
        ]
        
        print("Inserting new agreed categories...")
        for code, name, name_en in new_categories:
            cur.execute("""
                INSERT INTO medical_categories (code, name, name_ar, name_en, active, deleted, created_at, updated_at)
                VALUES (%s, %s, %s, %s, true, false, %s, %s);
            """, (code, name, name, name_en, datetime.now(), datetime.now()))
        
        conn.commit()
        print("Successfully reset categories to the 8 agreed roots.")
        
    except Exception as e:
        conn.rollback()
        print(f"Error: {e}")
    finally:
        cur.close()
        conn.close()

if __name__ == "__main__":
    reset_categories()
