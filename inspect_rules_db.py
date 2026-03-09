import psycopg2

def inspect_rules():
    conn = psycopg2.connect(
        dbname="tba_waad_system",
        user="postgres",
        password="12345",
        host="localhost",
        port="5432"
    )
    cur = conn.cursor()
    cur.execute("SELECT id, benefit_policy_id, medical_category_id, active FROM benefit_policy_rules WHERE benefit_policy_id = 1")
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return rows

if __name__ == "__main__":
    rules = inspect_rules()
    for r in rules:
        print(r)
