import pandas as pd
import json

df = pd.read_excel(r'd:\tba_waad_system-main\tba_waad_system-main\قائمة اسعار خدمات دار الشفاء مصنفة.xlsx', header=None)
# Find header row
header_row = 0
for i, row in df.iterrows():
    if "الخدمة" in str(row) or "الخدمه" in str(row):
        header_row = i
        break

df.columns = df.iloc[header_row]
df = df.iloc[header_row+1:].reset_index(drop=True)
df = df.dropna(subset=["الخدمه"]) # or "الخدمة"

print(json.dumps(list(df.columns), ensure_ascii=False))
print(json.dumps(df.head(5).to_dict(orient='records'), ensure_ascii=False))
