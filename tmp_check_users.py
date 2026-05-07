import psycopg2

def list_users():
    try:
        conn = psycopg2.connect("postgresql://postgres:12345@localhost:5432/tba_waad_system")
        cur = conn.cursor()
        
        cur.execute("SELECT id, username, email, is_active, user_type, email_verified, failed_login_count, locked_until FROM users;")
        rows = cur.fetchall()
        with open("users_list_out.txt", "w", encoding="utf-8") as f:
            f.write("=== USERS ===\n")
            for row in rows:
                f.write(f"ID: {row[0]}, Username: {row[1]}, Email: {row[2]}, Active: {row[3]}, UserType: {row[4]}, EmailVerified: {row[5]}, FailedLoginCount: {row[6]}, LockedUntil: {row[7]}\n")
            
        cur.close()
        conn.close()
    except Exception as e:
        with open("users_list_out.txt", "w", encoding="utf-8") as f:
            f.write(f"Error connecting to database: {e}\n")

if __name__ == "__main__":
    list_users()
