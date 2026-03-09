import psycopg2

def inspect():
    conn = psycopg2.connect(dbname='tba_waad_system', user='postgres', password='12345', host='localhost', port='5432')
    cur = conn.cursor()
    cur.execute("SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'benefit_policy_rules'")
    cols = cur.fetchall()
    for col in cols:
        print(f"{col[0]} ({col[1]})")
    cur.close()
    conn.close()

if __name__ == "__main__":
    inspect()
