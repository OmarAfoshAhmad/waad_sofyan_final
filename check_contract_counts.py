import psycopg2
import sys

def check_db():
    try:
        conn = psycopg2.connect(dbname='tba_waad_system', user='postgres', password='12345', host='localhost', port='5432')
        cur = conn.cursor()
        
        # List tables
        cur.execute("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")
        tables = [t[0] for t in cur.fetchall()]
        print(f"Tables: {tables}")
        
        # Check pricing items table name - probably provider_pricing_items or similar
        pricing_table = None
        for t in tables:
          if 'pricing' in t:
            pricing_table = t
            break
        
        if pricing_table:
          cur.execute(f'SELECT count(*) FROM {pricing_table}')
          count = cur.fetchone()[0]
          print(f"Pricing table {pricing_table} has {count} rows")
          
          # If it has provider_id or contract_id
          cur.execute(f"SELECT column_name FROM information_schema.columns WHERE table_name = '{pricing_table}'")
          cols = [c[0] for c in cur.fetchall()]
          print(f"Columns in {pricing_table}: {cols}")
          
          target_col = 'contract_id' if 'contract_id' in cols else ('provider_id' if 'provider_id' in cols else None)
          if target_col:
            cur.execute(f'SELECT {target_col}, count(*) FROM {pricing_table} GROUP BY {target_col} ORDER BY count DESC LIMIT 5')
            print(f"Top counts by {target_col}: {cur.fetchall()}")

        # Check medical services
        if 'medical_services' in tables:
          cur.execute('SELECT count(*) FROM medical_services')
          print(f"Total medical services: {cur.fetchone()[0]}")
        
        cur.close()
        conn.close()
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    check_db()
