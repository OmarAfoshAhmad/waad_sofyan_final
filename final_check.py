import pandas as pd
import sys
sys.stdout.reconfigure(encoding='utf-8')
output_file = r"d:\tba_waad_system-main\tba_waad_system-main\Dar_Shifa_Import_Ready.xlsx"
try:
    df = pd.read_excel(output_file)
    print(df.head(20).to_markdown())
except Exception as e:
    print(f"Error: {e}")
