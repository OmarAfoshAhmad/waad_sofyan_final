import psycopg2
import json

def get_policy_rules(policy_id):
    conn = psycopg2.connect(
        dbname="tba_waad_system",
        user="postgres",
        password="12345",
        host="localhost",
        port="5432"
    )
    cur = conn.cursor()
    cur.execute("""
        SELECT mc.code, mc.name, bpr.coverage_percent, bpr.amount_limit 
        FROM benefit_policy_rules bpr
        JOIN medical_categories mc ON bpr.medical_category_id = mc.id
        WHERE bpr.benefit_policy_id = %s
        ORDER BY mc.id
    """, (policy_id,))
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return rows

if __name__ == "__main__":
    rules = get_policy_rules(1)
    for r in rules:
        print(f"{r[0]:<15} | {r[1]:<30} | {r[2]}% | {r[3]}")
