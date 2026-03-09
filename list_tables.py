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

def list_tables():
    conn = get_db_connection()
    cur = conn.cursor()
    
    cur.execute("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name;")
    tables = cur.fetchall()
    
    print("Tables in public schema:")
    for table in tables:
        print(f"- {table[0]}")
        
    cur.close()
    conn.close()

if __name__ == "__main__":
    list_tables()
