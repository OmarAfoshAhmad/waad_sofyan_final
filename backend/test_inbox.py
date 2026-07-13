import psycopg2

conn = psycopg2.connect(
    dbname="tba_waad_system",
    user="postgres",
    password="waad_password", # Wait, I don't know the password
    host="localhost",
    port="5432"
)
