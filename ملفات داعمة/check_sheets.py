import pandas as pd
xl = pd.ExcelFile('d:/tba_waad_system-main_success/tba_waad_system-main/ملف_استيراد_المرافق_محدث_مع_مرافق_الاسنان.xlsx')
print("Sheets:", xl.sheet_names)
for sheet in xl.sheet_names:
    df = pd.read_excel(xl, sheet_name=sheet, nrows=2)
    print(f"Sheet '{sheet}' headers:", df.columns.tolist())
