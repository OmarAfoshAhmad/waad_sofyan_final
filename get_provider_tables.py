import psycopg2
import json

def get_tables():
    conn = psycopg2.connect(dbname="tba_waad_system", user="postgres", password="12345", host="localhost", port="5432")
    cur = conn.cursor()
    cur.execute("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_name LIKE 'provider%'")
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return [r[0] for r in rows]

if __name__ == "__main__":
    try:
        tables = get_tables()
        print(json.dumps(tables, ensure_ascii=False))
    except Exception as e:
        print(f"Error: {e}")
