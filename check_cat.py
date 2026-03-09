import psycopg2

def check_category(id):
    conn = psycopg2.connect(dbname='tba_waad_system', user='postgres', password='12345', host='localhost', port='5432')
    cur = conn.cursor()
    cur.execute("SELECT id, code, name FROM medical_categories WHERE id = %s", (id,))
    print(cur.fetchone())
    cur.close()
    conn.close()

if __name__ == "__main__":
    check_category(105)
