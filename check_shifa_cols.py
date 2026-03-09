import pandas as pd
import json

df = pd.read_excel(r'd:\tba_waad_system-main\tba_waad_system-main\قائمة اسعار خدمات دار الشفاء مصنفة.xlsx')
print(json.dumps(list(df.columns), ensure_ascii=False))
print(json.dumps(df.head(1).to_dict(orient='records'), ensure_ascii=False))
