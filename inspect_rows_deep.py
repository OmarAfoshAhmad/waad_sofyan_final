import pandas as pd
import sys
sys.stdout.reconfigure(encoding='utf-8')
source_file = r"d:\tba_waad_system-main\tba_waad_system-main\قائمة اسعار خدمات دار الشفاء مصنفة.xlsx"
try:
    df_raw = pd.read_excel(source_file, header=None)
    for i in range(50):
        row = df_raw.iloc[i].tolist()
        print(f"Row {i}: {row}")
except Exception as e:
    print(f"Error: {e}")
