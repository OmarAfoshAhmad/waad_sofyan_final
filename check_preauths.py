import psycopg2
import json

def check_preauths():
    conn = psycopg2.connect(dbname="tba_waad_system", user="postgres", password="12345", host="localhost", port="5432")
    cur = conn.cursor()
    cur.execute("SELECT id, auth_number FROM pre_authorizations LIMIT 10")
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return [{"id": r[0], "number": r[1]} for r in rows]

if __name__ == "__main__":
    try:
        data = check_preauths()
        print(json.dumps(data, ensure_ascii=False))
    except Exception as e:
        print(f"Error: {e}")
