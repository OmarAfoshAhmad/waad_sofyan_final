import psycopg2

def search():
    conn = psycopg2.connect(dbname='tba_waad_system', user='postgres', password='12345', host='localhost', port='5432')
    cur = conn.cursor()
    cur.execute("SELECT id, code, name FROM medical_categories WHERE name LIKE %s", ('%أشعة%',))
    rows = cur.fetchall()
    for row in rows:
        print(row)
    cur.close()
    conn.close()

if __name__ == "__main__":
    search()
