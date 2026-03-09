import docx
import pandas as pd
import json
import os

def extract_tables_to_json(doc_path):
    doc = docx.Document(doc_path)
    all_tables = []
    
    for table in doc.tables:
        data = []
        for row in table.rows:
            row_data = [cell.text.strip() for cell in row.cells]
            data.append(row_data)
        
        if data:
            all_tables.append(data)
            
    return all_tables

if __name__ == "__main__":
    doc_path = r"d:\tba_waad_system-main\tba_waad_system-main\جدول منافع المنطقة الحرة جليانة.docx"
    output_path = r"d:\tba_waad_system-main\tba_waad_system-main\extracted_tables.json"
    
    try:
        tables = extract_tables_to_json(doc_path)
        with open(output_path, "w", encoding="utf-8") as f:
            json.dump(tables, f, ensure_ascii=False, indent=2)
        print(f"Extracted {len(tables)} tables to {output_path}")
    except Exception as e:
        print(f"Error: {e}")
