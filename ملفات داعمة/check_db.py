import psycopg2
import os
from decimal import Decimal


def db_dsn():
    database_url = os.getenv("DATABASE_URL")
    if database_url:
        return database_url

    password = os.getenv("DB_PASSWORD")
    if not password:
        raise RuntimeError("Set DATABASE_URL or DB_PASSWORD before running this script.")

    host = os.getenv("DB_HOST", "localhost")
    port = os.getenv("DB_PORT", "5432")
    name = os.getenv("DB_NAME", "tba_waad_system")
    user = os.getenv("DB_USER", "postgres")
    return f"postgresql://{user}:{password}@{host}:{port}/{name}"


def check_db():
    conn = psycopg2.connect(db_dsn())
    cur = conn.cursor()
    
    print("====== Member =====")
    cur.execute("""
        SELECT m.id, m.full_name, m.employer_id, m.benefit_policy_id
        FROM members m
        WHERE m.full_name LIKE '%ابتهال احمد%'
    """)
    member = cur.fetchone()
    print("Member:", member)
    if not member:
        return
        
    employer_id = member[2]
    
    # find policy
    cur.execute("""
        SELECT b.id, b.policy_code, b.name 
        FROM benefit_policies b 
        WHERE b.employer_id = %s AND b.active = true 
        ORDER BY b.start_date DESC LIMIT 1
    """, (employer_id,))
    policy = cur.fetchone()
    print("Policy:", policy)
    policy_id = policy[0]
    
    print("====== Policy Rules =====")
    cur.execute("""
        SELECT bp.id, mc.name as category_name, mc.code as category_code,
               bp.coverage_percent, bp.amount_limit, bp.times_limit
        FROM benefit_policy_rules bp
        JOIN medical_categories mc ON bp.medical_category_id = mc.id
        WHERE bp.benefit_policy_id = %s
    """, (policy_id,))
    for rule in cur.fetchall():
        if "طبيعي" in rule[1] or "WE" in rule[2] or "413" in rule[2] or "علاج" in rule[1]:
            print("Rule:", rule)
            
    print("====== OP CAT Policy Rules =====")
    cur.execute("""
        SELECT bp.id, mc.name as category_name, mc.code as category_code,
               bp.coverage_percent, bp.amount_limit, bp.times_limit
        FROM benefit_policy_rules bp
        JOIN medical_categories mc ON bp.medical_category_id = mc.id
        WHERE bp.benefit_policy_id = %s AND mc.code LIKE 'CAT-OP%'
    """, (policy_id,))
    for rule in cur.fetchall():
        print("Op Rule:", rule)
            
    print("====== Pricing Items =====")
    cur.execute("""
        SELECT p.id as provider_id, p.name as provider_name,
               i.id, i.service_code, i.service_name, i.contract_price
        FROM provider_contract_pricing_items i
        JOIN provider_contracts c ON i.contract_id = c.id
        JOIN providers p ON c.provider_id = p.id
        WHERE i.service_code = 'WE-413' OR i.service_name LIKE '%طبيعي%طبيب زائر%'
    """)
    for row in cur.fetchall():
        print("Pricing:", row)
        
    print("====== Pricing Validation =====")
    cur.execute("""
        SELECT SUM(refused_amount) from claim_lines cl
        JOIN claims c on cl.claim_id = c.id
        WHERE c.member_id = %s AND cl.service_code = 'WE-413'
    """, (member[0],))
    print("Total Refused WE-413:", cur.fetchone())

    cur.close()
    conn.close()

check_db()
