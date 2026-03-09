import psycopg2

def count_duplicates():
    conn = psycopg2.connect(
        dbname="tba_waad_system",
        user="postgres",
        password="12345",
        host="localhost",
        port="5432"
    )
    cur = conn.cursor()
    cur.execute("""
        SELECT medical_category_id, count(*) 
        FROM benefit_policy_rules 
        WHERE benefit_policy_id = 1
        GROUP BY medical_category_id
        HAVING count(*) > 1
    """)
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return rows

if __name__ == "__main__":
    dupes = count_duplicates()
    if dupes:
        print(f"Found {len(dupes)} duplicates:")
        for d in dupes:
            print(f"Category ID {d[0]}: {d[1]} times")
    else:
        print("No duplicates found.")
