import psycopg2

try:
    conn = psycopg2.connect("dbname=tba_waad_system user=postgres password=12345 host=localhost port=5432")
    cur = conn.cursor()
    cur.execute("""
        SELECT c.id, c.status, c.service_date, c.provider_id, m.employer_id, c.active
        FROM claims c
        JOIN members m ON c.member_id = m.id
        ORDER BY c.id DESC LIMIT 5;
    """)
    rows = cur.fetchall()
    print("Recent Claims with Employer:")
    for r in rows:
        print(r)
except Exception as e:
    print(e)
