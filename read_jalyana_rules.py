import pandas as pd
import sys

try:
    df = pd.read_excel('قواعد_تغطية_المنطقة_الحرة_جليانة_بالسياق.xlsx')
    print("Columns:", df.columns.tolist())
    print("\nFirst 15 rows:")
    print(df.head(15).to_string())
    
except Exception as e:
    print(f"Error: {e}")
