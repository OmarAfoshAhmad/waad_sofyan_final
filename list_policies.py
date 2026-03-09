import psycopg2
import json

def get_policies():
    conn = psycopg2.connect(
        dbname="tba_waad_system",
        user="postgres",
        password="12345",
        host="localhost",
        port="5432"
    )
    cur = conn.cursor()
    cur.execute("SELECT id, name FROM benefit_policies WHERE active = true")
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return rows

if __name__ == "__main__":
    try:
        policies = get_policies()
        print(json.dumps(policies, ensure_ascii=False))
    except Exception as e:
        print(f"Error: {e}")
