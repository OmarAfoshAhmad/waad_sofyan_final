import psycopg2

try:
    conn = psycopg2.connect("dbname=waad user=postgres password=root host=localhost")
    cur = conn.cursor()
    cur.execute("SELECT column_name FROM information_schema.columns WHERE table_name = 'claims'")
    columns = [row[0] for row in cur.fetchall()]
    print("Columns in claims table:")
    for col in columns:
        print(f" - {col}")
except Exception as e:
    print("Error:", e)
