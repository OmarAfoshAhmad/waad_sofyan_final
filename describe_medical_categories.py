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

def describe_table():
    conn = get_db_connection()
    cur = conn.cursor()
    
    cur.execute("SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'medical_categories';")
    columns = cur.fetchall()
    
    for col in columns:
        print(f"{col[0]}: {col[1]}")
        
    cur.close()
    conn.close()

if __name__ == "__main__":
    describe_table()
