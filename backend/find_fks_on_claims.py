import psycopg2

try:
    conn = psycopg2.connect("dbname=tba_waad_system user=postgres password=12345 host=localhost")
    cur = conn.cursor()
    
    # Query to find all foreign keys referencing the 'claims' table
    query = """
    SELECT
        tc.table_name, 
        kcu.column_name, 
        ccu.table_name AS referenced_table_name,
        ccu.column_name AS referenced_column_name
    FROM 
        information_schema.table_constraints AS tc 
        JOIN information_schema.key_column_usage AS kcu
          ON tc.constraint_name = kcu.constraint_name
          AND tc.table_schema = kcu.table_schema
        JOIN information_schema.constraint_column_usage AS ccu
          ON ccu.constraint_name = tc.constraint_name
          AND ccu.table_schema = tc.table_schema
    WHERE tc.constraint_type = 'FOREIGN KEY' AND ccu.table_name='claims';
    """
    
    cur.execute(query)
    rows = cur.fetchall()
    
    print("Foreign keys referencing 'claims' table:")
    for row in rows:
        print(f" - Table: {row[0]}, Column: {row[1]} -> referenced_column: {row[3]}")
    
except Exception as e:
    print("Error:", e)
finally:
    if 'conn' in locals():
        conn.close()
