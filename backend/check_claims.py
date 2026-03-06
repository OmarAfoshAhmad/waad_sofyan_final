import psycopg2

try:
    conn = psycopg2.connect("dbname=tba_waad_system user=postgres password=12345 host=localhost port=5432")
    cur = conn.cursor()
    cur.execute("SELECT id, status, claim_source, legacy_reference_number, service_date, is_backlog, active, entered_by, provider_id FROM claims ORDER BY id DESC LIMIT 5;")
    rows = cur.fetchall()
    print("Recent Claims:")
    for r in rows:
        print(r)
except Exception as e:
    print(e)
