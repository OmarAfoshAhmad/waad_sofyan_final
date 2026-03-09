import pandas as pd
import psycopg2
import json

def import_shifa_prices():
    # Load Excel - skipping header rows to find the actual data
    df = pd.read_excel(r'd:\tba_waad_system-main\tba_waad_system-main\قائمة اسعار خدمات دار الشفاء مصنفة.xlsx', header=None)
    
    # Simple logic to find header row (contains "الخدمه")
    header_idx = 0
    for i, row in df.iterrows():
        if "الخدمة" in str(row) or "الخدمه" in str(row):
            header_idx = i
            break
    
    df.columns = df.iloc[header_idx]
    df = df.iloc[header_idx+1:].reset_index(drop=True)
    df = df.dropna(subset=["الخدمه", "السعر"])
    
    # Clean price (remove symbols)
    def clean_price(p):
        try:
            return float(str(p).replace('د.ل', '').replace(',', '').strip())
        except:
            return 0.0

    # DB Connection
    db_config = {"dbname": "tba_waad_system", "user": "postgres", "password": "12345", "host": "localhost", "port": "5432"}
    conn = psycopg2.connect(**db_config)
    cur = conn.cursor()
    
    contract_id = 1 # Dar Al-Shifa
    
    # Category Map from previous DB check
    # 1: عمليات, 2: إيواء, 3: عيادات خارجية, 20: تحاليل عيادة, 7: اشعة, 21: اشعة تخصصية, 5: اسنان
    category_map = {
        "تحاليل": 20,
        "مختبر": 20,
        "أشعة": 7,
        "اشعه": 7,
        "رنين": 21,
        "مقطعية": 21,
        "إيواء": 2,
        "ايواء": 2,
        "عمليات": 1,
        "عيادة": 19,
        "كشف": 19,
        "طوارئ": 27,
        "علاج طبيعي": 22,
        "اسنان": 23,
        "نظارات": 24
    }

    import_count = 0
    for _, row in df.iterrows():
        service_name = str(row["الخدمه"]).strip()
        specialty = str(row.get("التخصص", "")).strip()
        price = clean_price(row["السعر"])
        
        # Decide category
        final_cat_id = 19 # Default to OPD Consultations
        for key, cat_id in category_map.items():
            if key in service_name or key in specialty:
                final_cat_id = cat_id
                break
        
        # Insert into provider_contract_pricing_items
        cur.execute("""
            INSERT INTO provider_contract_pricing_items 
            (contract_id, service_name, medical_category_id, contract_price, active, created_at)
            VALUES (%s, %s, %s, %s, True, NOW())
        """, (contract_id, service_name, final_cat_id, price))
        import_count += 1

    conn.commit()
    cur.close()
    conn.close()
    print(f"Imported {import_count} services to Dar Al-Shifa contract.")

if __name__ == "__main__":
    try:
        import_shifa_prices()
    except Exception as e:
        print(f"Error: {e}")
