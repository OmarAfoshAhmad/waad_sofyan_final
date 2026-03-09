import psycopg2
import os
from dotenv import load_dotenv

load_dotenv()

def get_db_connection():
    return psycopg2.connect(
        dbname=os.getenv('POSTGRES_DB', 'tba_waad_system'),
        user=os.getenv('POSTGRES_USER', 'postgres'),
        password=os.getenv('DB_PASSWORD', '12345'),
        host='localhost'
    )

def list_categories():
    conn = get_db_connection()
    cur = conn.cursor()
    
    cur.execute("SELECT id, code, name FROM medical_categories WHERE deleted = false ORDER BY id;")
    categories = cur.fetchall()
    
    print(f"{'ID':<5} | {'Code':<25} | {'Name'}")
    print("-" * 80)
    for cat in categories:
        print(f"{cat[0]:<5} | {cat[1]:<25} | {cat[2]}")
        
    cur.close()
    conn.close()

if __name__ == "__main__":
    list_categories()
