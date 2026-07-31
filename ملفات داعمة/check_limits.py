import pandas as pd
import glob
import os
import re

path = 'd:/tba_waad_system-main_success/tba_waad_system-main/Unified_Benefit_Tables_Ready_V3/*.xlsx'
files = glob.glob(path)
files = [f for f in files if not os.path.basename(f).startswith('~$')]

unique_limits = set()
unique_amounts = set()

for f in files:
    try:
        df = pd.read_excel(f)
        if 'سقف المرات' in df.columns:
            for val in df['سقف المرات'].dropna():
                if not str(val).isnumeric():
                    unique_limits.add(str(val))
        if 'سقف المبلغ (د.ل)' in df.columns:
            for val in df['سقف المبلغ (د.ل)'].dropna():
                val_str = str(val).replace(',', '')
                try:
                    float(val_str)
                except:
                    unique_amounts.add(str(val))
    except Exception as e:
        pass

print("Non-numeric سقف المرات:", unique_limits)
print("Non-numeric سقف المبلغ:", unique_amounts)
