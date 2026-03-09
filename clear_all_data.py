import psycopg2
import os
from dotenv import load_dotenv

load_dotenv()

def get_db_connection():
    return psycopg2.connect(
        dbname=os.getenv('POSTGRES_DB', 'tba_waad_system'),
        user=os.getenv('POSTGRES_USER', 'postgres'),
        password=os.getenv('DB_PASSWORD', '12345'),
        host='localhost'
    )

def clear_system_data():
    conn = get_db_connection()
    cur = conn.cursor()
    
    try:
        print("Starting comprehensive data clearing...")
        
        # 1. Clear Claims and related
        print("Clearing Claims and related data...")
        tables_to_truncate = [
            "claim_lines", "claim_attachments", "claim_audit_logs", 
            "claim_history", "claims", "settlement_batch_items", "settlement_batches"
        ]
        for table in tables_to_truncate:
            cur.execute(f"TRUNCATE TABLE {table} RESTART IDENTITY CASCADE;")

        # 2. Clear Pre-Authorizations and Visits
        print("Clearing Pre-Authorizations and Visits...")
        pa_tables = [
            "pre_authorization_attachments", "pre_authorization_audit", 
            "pre_authorizations", "preauthorization_requests", 
            "visit_attachments", "visits"
        ]
        for table in pa_tables:
            cur.execute(f"TRUNCATE TABLE {table} RESTART IDENTITY CASCADE;")

        # 3. Clear Contracts and Pricing
        print("Clearing Contracts and Pricing...")
        contract_tables = [
            "provider_contract_pricing_items", "provider_admin_documents", 
            "provider_contracts", "legacy_provider_contracts"
        ]
        for table in contract_tables:
            cur.execute(f"TRUNCATE TABLE {table} RESTART IDENTITY CASCADE;")

        # 4. Clear Services and Mappings
        print("Clearing Medical Services and Provider Services...")
        service_tables = [
            "provider_service_mappings", "provider_raw_services", 
            "provider_services", "medical_service_categories", 
            "medical_services", "provider_service_price_import_logs",
            "provider_service_price_import_log"
        ]
        for table in service_tables:
            cur.execute(f"TRUNCATE TABLE {table} RESTART IDENTITY CASCADE;")
        
        conn.commit()
        print("SUCCESS: System data cleared. Contracts, Services, and Pricing items are gone.")
        print("Ready for fresh import.")
        
    except Exception as e:
        conn.rollback()
        print(f"Error during clearing: {e}")
    finally:
        cur.close()
        conn.close()

if __name__ == "__main__":
    clear_system_data()
