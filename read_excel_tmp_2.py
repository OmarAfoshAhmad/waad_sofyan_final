import pandas as pd
import sys

try:
    # Read the file skipping first 8 rows (adjusting to headers on row 9)
    df = pd.read_excel('قائمة اسعار خدمات دار الشفاء مصنفة.xlsx', skiprows=8)
    print("Columns:", df.columns.tolist())
    print("\nFirst 10 rows after skipping:")
    print(df.head(10).to_string())
    
    # Identify classification column (likely the one containing 'إيواء')
    for col in df.columns:
        unique_vals = df[col].dropna().unique()
        if len(unique_vals) < 50: # Likely categories or specialties
            print(f"\nUnique values in {col}:")
            print(unique_vals)
        
except Exception as e:
    print(f"Error: {e}")
