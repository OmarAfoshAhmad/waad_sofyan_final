
import pandas as pd
import os

files = [
    r"d:\tba_waad_system-main\tba_waad_system-main\Price_List_Contract_4 (1).xlsx",
    r"d:\tba_waad_system-main\tba_waad_system-main\مصحة منارة المستقبل درنة.xlsx",
    r"d:\tba_waad_system-main\tba_waad_system-main\Price_List_Import_Manara.xlsx",
    r"d:\tba_waad_system-main\tba_waad_system-main\استيراد_خدمات_مصحة_منارة_المستقبل.xlsx"
]

for f in files:
    if os.path.exists(f):
        print(f"--- F: {f} ---")
        try:
            df = pd.read_excel(f, nrows=10)
            print(df.columns.tolist())
            print(df.head(5))
            
            # If there's a Category column, list unique values
            for col in df.columns:
                if 'تصنيف' in str(col) or 'Category' in str(col) or 'Special' in str(col):
                    full_df = pd.read_excel(f)
                    unique_vals = full_df[col].dropna().unique().tolist()
                    print(f"Unique values in {col}: {len(unique_vals)}")
                    print(unique_vals)
        except Exception as e:
            print(f"Error: {e}")
