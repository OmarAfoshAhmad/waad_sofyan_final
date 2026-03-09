import psycopg2

def inspect():
    conn = psycopg2.connect(
        dbname="tba_waad_system",
        user="postgres",
        password="12345",
        host="localhost",
        port="5432"
    )
    cur = conn.cursor()
    cur.execute("SELECT * FROM benefit_policy_rules WHERE benefit_policy_id = 1")
    cols = [desc[0] for desc in cur.description]
    print(cols)
    rows = cur.fetchall()
    for row in rows:
        print(row)
    cur.close()
    conn.close()

if __name__ == "__main__":
    inspect()
