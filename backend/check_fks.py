import psycopg2

try:
    conn = psycopg2.connect("dbname=tba_waad_system user=postgres password=12345 host=localhost")
    cur = conn.cursor()
    
    # Query to find all foreign keys referencing 'claims' and their ON DELETE action
    query = """
    SELECT
        conname,
        r.relname AS child_table,
        confdeltype
    FROM pg_constraint c
    JOIN pg_class r ON c.conrelid = r.oid
    JOIN pg_class t ON c.confrelid = t.oid
    WHERE t.relname = 'claims' AND c.contype = 'f';
    """
    
    cur.execute(query)
    rows = cur.fetchall()
    
    # Map confdeltype: 'a' = no action, 'r' = restrict, 'c' = cascade, 'n' = set null, 'd' = set default
    action_map = {'a': 'NO ACTION', 'r': 'RESTRICT', 'c': 'CASCADE', 'n': 'SET NULL', 'd': 'SET DEFAULT'}
    
    print("FK Constraints on table 'claims':")
    for row in rows:
        action = action_map.get(row[2], row[2])
        print(f" - Constraint: {row[0]}, Table: {row[1]}, On Delete: {action}")
    
except Exception as e:
    print("Error:", e)
finally:
    if 'conn' in locals():
        conn.close()
