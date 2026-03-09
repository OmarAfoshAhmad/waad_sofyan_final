import sqlite3
import os

# Try to find the database file. Usually it's in the current dir or backend/
db_file = 'backend/tba_waad.db' # Adjust based on project knowledge
if not os.path.exists(db_file):
    db_file = 'tba_waad.db'

if os.path.exists(db_file):
    conn = sqlite3.connect(db_file)
    try:
        query = "SELECT id, code, name, name_ar FROM medical_categories WHERE parent_id IS NULL AND active = 1"
        df = pd.read_sql_query(query, conn)
        print("Root Medical Categories:")
        print(df)
    except Exception as e:
        print(f"Error querying DB: {e}")
    finally:
        conn.close()
else:
    print(f"Database file {db_file} not found.")
