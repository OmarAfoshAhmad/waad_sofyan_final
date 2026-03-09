import psycopg2

def delete_all_claims():
    db_config = {
        "dbname": "tba_waad_system",
        "user": "postgres",
        "password": "12345",
        "host": "localhost",
        "port": "5432"
    }

    tables_to_clear = [
        "claim_attachments",
        "claim_audit_logs",
        "claim_history",
        "claim_lines",
        "claims",
        "claim_batches" # If it exists, let's check
    ]

    conn = None
    try:
        conn = psycopg2.connect(**db_config)
        cur = conn.cursor()

        # Check if claim_batches exists first
        cur.execute("SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'claim_batches')")
        if cur.fetchone()[0]:
            tables_to_clear.insert(0, "claim_batches")

        # Use TRUNCATE with CASCADE to handle dependencies efficiently
        # We start with child tables or use CASCADE on parent
        print("Truncating claim tables...")
        # Since 'claims' is the parent, truncating it with CASCADE is safest
        cur.execute("TRUNCATE TABLE claims RESTART IDENTITY CASCADE;")
        
        # Check for other standalone claim tables just in case
        for table in ["claim_batches", "claim_history", "claim_audit_logs"]:
             cur.execute(f"SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = '{table}')")
             if cur.fetchone()[0]:
                 cur.execute(f"TRUNCATE TABLE {table} RESTART IDENTITY CASCADE;")

        conn.commit()
        print("All old claims and related data have been successfully deleted.")
        cur.close()

    except Exception as e:
        if conn:
            conn.rollback()
        print(f"Error: {e}")
    finally:
        if conn:
            conn.close()

if __name__ == "__main__":
    delete_all_claims()
