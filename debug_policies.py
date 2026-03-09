import psycopg2

def check_all_policies():
    conn = psycopg2.connect(
        dbname="tba_waad_system",
        user="postgres",
        password="12345",
        host="localhost",
        port="5432"
    )
    cur = conn.cursor()
    cur.execute("""
        SELECT bp.id, bp.name, bp.policy_code, (SELECT count(*) FROM benefit_policy_rules WHERE benefit_policy_id = bp.id) 
        FROM benefit_policies bp
    """)
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return rows

if __name__ == "__main__":
    policies = check_all_policies()
    print(f"{'ID':<5} | {'Name':<20} | {'Code':<15} | {'Rules'}")
    print("-" * 55)
    for p in policies:
        print(f"{p[0]:<5} | {p[1]:<20} | {p[2] if p[2] else 'N/A':<15} | {p[3]}")
