import pandas as pd
df = pd.read_excel('d:/tba_waad_system-main_success/tba_waad_system-main/ملف_استيراد_المرافق_محدث_مع_مرافق_الاسنان.xlsx', nrows=5)
print("Headers:", df.columns.tolist())
