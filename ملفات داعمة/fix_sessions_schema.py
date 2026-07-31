import psycopg2

def fix_schema():
    try:
        conn = psycopg2.connect("postgresql://postgres:12345@localhost:5432/tba_waad_system")
        cur = conn.cursor()
        
        print("Checking current columns of spring_session...")
        cur.execute("""
            SELECT column_name, data_type, character_maximum_length 
            FROM information_schema.columns 
            WHERE table_name='spring_session';
        """)
        cols = cur.fetchall()
        print("spring_session columns:", cols)

        cur.execute("""
            SELECT column_name, data_type, character_maximum_length 
            FROM information_schema.columns 
            WHERE table_name='spring_session_attributes';
        """)
        cols_attr = cur.fetchall()
        print("spring_session_attributes columns:", cols_attr)

        print("Fixing schema: Altering CHAR(36) to VARCHAR(36)...")
        cur.execute("DROP TABLE IF EXISTS spring_session_attributes CASCADE;")
        cur.execute("DROP TABLE IF EXISTS spring_session CASCADE;")
        
        cur.execute("""
            CREATE TABLE spring_session (
                primary_id VARCHAR(36) NOT NULL,
                session_id VARCHAR(36) NOT NULL,
                creation_time BIGINT NOT NULL,
                last_access_time BIGINT NOT NULL,
                max_inactive_interval INT NOT NULL,
                expiry_time BIGINT NOT NULL,
                principal_name VARCHAR(100),
                CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
            );
        """)
        cur.execute("CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);")
        cur.execute("CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);")
        cur.execute("CREATE INDEX spring_session_ix3 ON spring_session (principal_name);")

        cur.execute("""
            CREATE TABLE spring_session_attributes (
                session_primary_id VARCHAR(36) NOT NULL,
                attribute_name VARCHAR(200) NOT NULL,
                attribute_bytes BYTEA NOT NULL,
                CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
                CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id) REFERENCES spring_session(primary_id) ON DELETE CASCADE
            );
        """)

        conn.commit()
        print("Schema successfully fixed to official Spring Session JDBC PostgreSQL format (VARCHAR 36 + BYTEA)!")
        cur.close()
        conn.close()
    except Exception as e:
        print("Error fixing schema:", e)

fix_schema()
