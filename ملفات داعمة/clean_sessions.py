import psycopg2
import os


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

def clean_spring_session():
    try:
        conn = psycopg2.connect(db_dsn())
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
