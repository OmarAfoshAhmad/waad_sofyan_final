import psycopg2

def add_test():
    conn = psycopg2.connect(dbname='tba_waad_system', user='postgres', password='12345', host='localhost', port='5432')
    cur = conn.cursor()
    cur.execute("""
        INSERT INTO benefit_policies 
        (name, policy_code, employer_id, status, active, start_date, end_date, annual_limit) 
        VALUES ('TEST POLICY', 'POL-9999-999', 1, 'DRAFT', True, '2026-01-01', '2027-01-01', 5000)
    """)
    conn.commit()
    cur.close()
    conn.close()
    print("Test policy added.")

if __name__ == "__main__":
    add_test()
