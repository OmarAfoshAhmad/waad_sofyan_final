import psycopg2
import sys

def apply_sql(sql_file):
    try:
        conn = psycopg2.connect(
            dbname="tba_waad_system",
            user="postgres",
            password="12345",
            host="localhost",
            port="5432"
        )
        conn.autocommit = True
        cur = conn.cursor()
        
        with open(sql_file, "r", encoding="utf-8") as f:
            sql = f.read()
            
        print(f"Applying SQL from {sql_file}...")
        cur.execute(sql)
        print("Success!")
        
        cur.close()
        conn.close()
    except Exception as e:
        print(f"Error: {e}")
        sys.exit(1)

if __name__ == "__main__":
    if len(sys.argv) > 1:
        apply_sql(sys.argv[1])
    else:
        print("Usage: python apply_sql_script.py <sql_file>")
