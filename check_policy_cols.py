import psycopg2

def check():
    conn = psycopg2.connect(dbname='tba_waad_system', user='postgres', password='12345', host='localhost', port='5432')
    cur = conn.cursor()
    cur.execute("SELECT * FROM benefit_policies WHERE id = 1")
    cols = [desc[0] for desc in cur.description]
    print(cols)
    print(cur.fetchone())
    cur.close()
    conn.close()

if __name__ == "__main__":
    check()
