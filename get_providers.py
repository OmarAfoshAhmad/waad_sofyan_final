import psycopg2
import json

def get_providers():
    conn = psycopg2.connect(dbname="tba_waad_system", user="postgres", password="12345", host="localhost", port="5432")
    cur = conn.cursor()
    cur.execute("SELECT id, name FROM providers WHERE active = true")
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return [{"id": r[0], "name": r[1]} for r in rows]

if __name__ == "__main__":
    try:
        providers = get_providers()
        print(json.dumps(providers, ensure_ascii=False))
    except Exception as e:
        print(f"Error: {e}")
