import pandas as pd
import sys
sys.stdout.reconfigure(encoding='utf-8')
excel_path = r"d:\tba_waad_system-main\tba_waad_system-main\Price_List_Contract_4 (1).xlsx"
try:
    df = pd.read_excel(excel_path)
    print(f"Total rows: {len(df)}")
    print(df.head(50).to_markdown())
except Exception as e:
    print(f"Error: {e}")
