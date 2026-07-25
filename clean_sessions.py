import psycopg2

def clean_spring_session():
    try:
        conn = psycopg2.connect("postgresql://postgres:W44d_Pr0d_Pg2026_xKz9mNq@localhost:5432/tba_waad_system")
        cur = conn.cursor()
        
        print("Checking SPRING_SESSION table...")
        cur.execute("SELECT COUNT(*) FROM spring_session;")
        count = cur.fetchone()[0]
        print(f"Total sessions in spring_session: {count}")
        
        print("Clearing spring_session and spring_session_attributes tables...")
        cur.execute("DELETE FROM spring_session_attributes;")
        cur.execute("DELETE FROM spring_session;")
        conn.commit()
        
        print("Successfully cleared spring_session and spring_session_attributes tables.")
        cur.close()
        conn.close()
    except Exception as e:
        print("Error clearing session tables:", e)

clean_spring_session()
