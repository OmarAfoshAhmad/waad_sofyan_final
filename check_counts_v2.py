import psycopg2
import sys

def check_db():
    try:
        conn = psycopg2.connect(dbname='tba_waad_system', user='postgres', password='12345', host='localhost', port='5432')
        cur = conn.cursor()
        
        tables = ['medical_services', 'provider_services', 'provider_contract_pricing_items', 'medical_categories']
        for table in tables:
            try:
                cur.execute(f"SELECT count(*) FROM {table}")
                print(f"Table {table}: {cur.fetchone()[0]} rows")
            except:
                conn.rollback()
                print(f"Table {table} not found or error")
        
        cur.close()
        conn.close()
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    check_db()
