import pandas as pd
import sys
sys.stdout.reconfigure(encoding='utf-8')
excel_path = r"d:\tba_waad_system-main\tba_waad_system-main\قائمة اسعار خدمات دار الشفاء مصنفة.xlsx"
try:
    df = pd.read_excel(excel_path)
    print(f"Total rows: {len(df)}")
    print("Columns:", df.columns.tolist())
    print(df.head(20).astype(str).to_markdown())
except Exception as e:
    print(f"Error: {e}")
