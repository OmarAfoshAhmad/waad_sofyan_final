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

def check_linkages():
    conn = get_db_connection()
    cur = conn.cursor()
    
    cur.execute("SELECT count(*) FROM medical_services WHERE deleted = false;")
    svc_count = cur.fetchone()[0]
    
    cur.execute("SELECT count(*) FROM medical_service_categories WHERE active = true;")
    link_count = cur.fetchone()[0]
    
    print(f"Active Services: {svc_count}")
    print(f"Service-Category Links: {link_count}")
    
    cur.close()
    conn.close()

if __name__ == "__main__":
    check_linkages()
