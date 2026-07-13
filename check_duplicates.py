import pandas as pd
df = pd.read_excel('d:/tba_waad_system-main_success/tba_waad_system-main/قائمة_التصنيفات_جاهز_للاستيراد.xlsx')
dups = df[df.duplicated('الرمز', keep=False)]
print("Duplicates:")
print(dups)
