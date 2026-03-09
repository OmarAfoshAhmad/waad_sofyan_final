import psycopg2
import json

def get_columns(table_name):
    conn = psycopg2.connect(dbname="tba_waad_system", user="postgres", password="12345", host="localhost", port="5432")
    cur = conn.cursor()
    cur.execute(f"SELECT column_name, data_type FROM information_schema.columns WHERE table_name = '{table_name}'")
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return [{"column": r[0], "type": r[1]} for r in rows]

if __name__ == "__main__":
    try:
        tables = ["provider_services", "provider_contract_pricing_items", "provider_contracts"]
        results = {table: get_columns(table) for table in tables}
        print(json.dumps(results, ensure_ascii=False, indent=2))
    except Exception as e:
        print(f"Error: {e}")
