import psycopg2

def check_default():
    conn = psycopg2.connect(dbname='tba_waad_system', user='postgres', password='12345', host='localhost', port='5432')
    cur = conn.cursor()
    cur.execute("SELECT column_name, column_default, is_nullable FROM information_schema.columns WHERE table_name = 'benefit_policy_rules' AND column_name = 'created_at'")
    print(cur.fetchone())
    cur.close()
    conn.close()

if __name__ == "__main__":
    check_default()
