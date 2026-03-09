import pandas as pd
import json

df = pd.read_excel(r'd:\tba_waad_system-main\tba_waad_system-main\قائمة اسعار خدمات دار الشفاء مصنفة.xlsx', header=None, nrows=10)
print(df.to_json(orient='split', force_ascii=False))
