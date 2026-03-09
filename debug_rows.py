import pandas as pd
import re
import sys

source_file = r"d:\tba_waad_system-main\tba_waad_system-main\قائمة اسعار خدمات دار الشفاء مصنفة.xlsx"

try:
    df_raw = pd.read_excel(source_file, header=None)
    print(f"Read {len(df_raw)} raw rows.")
    # Show indexes 3 and 4 for rows 10-30
    subset = df_raw.iloc[10:30, [3, 4, 5]]
    print(subset.to_markdown())
except Exception as e:
    print(f"Error: {e}")
