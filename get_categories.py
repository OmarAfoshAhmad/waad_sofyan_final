import psycopg2
import json

def get_categories():
    conn = psycopg2.connect(dbname="tba_waad_system", user="postgres", password="12345", host="localhost", port="5432")
    cur = conn.cursor()
    cur.execute("SELECT id, name, name_ar, code FROM medical_categories WHERE active = true")
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return [{"id": r[0], "name": r[1], "name_ar": r[2], "code": r[3]} for r in rows]

if __name__ == "__main__":
    try:
        cats = get_categories()
        with open("db_categories.json", "w", encoding="utf-8") as f:
            json.dump(cats, f, ensure_ascii=False, indent=2)
        print("Done")
    except Exception as e:
        print(f"Error: {e}")
