import psycopg2
import json

def get_contracts():
    conn = psycopg2.connect(dbname="tba_waad_system", user="postgres", password="12345", host="localhost", port="5432")
    cur = conn.cursor()
    cur.execute("SELECT id, contract_number, provider_id FROM provider_contracts WHERE provider_id = 1")
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return [{"id": r[0], "contract_number": r[1], "provider_id": r[2]} for r in rows]

if __name__ == "__main__":
    try:
        contracts = get_contracts()
        print(json.dumps(contracts, ensure_ascii=False))
    except Exception as e:
        print(f"Error: {e}")
