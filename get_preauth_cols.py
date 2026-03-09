import psycopg2
import json

def get_preauth_cols():
    conn = psycopg2.connect(dbname="tba_waad_system", user="postgres", password="12345", host="localhost", port="5432")
    cur = conn.cursor()
    cur.execute("SELECT column_name FROM information_schema.columns WHERE table_name = 'pre_authorizations'")
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return [r[0] for r in rows]

if __name__ == "__main__":
    try:
        cols = get_preauth_cols()
        print(json.dumps(cols, ensure_ascii=False))
    except Exception as e:
        print(f"Error: {e}")
