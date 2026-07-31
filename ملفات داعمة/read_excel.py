import pandas as pd

file_path = "d:/tba_waad_system-main_success/tba_waad_system-main/قائمة التصنيفات المعتمدة النهائي_كامل.xlsx"
try:
    df = pd.read_excel(file_path, nrows=10)
    print("Headers:", df.columns.tolist())
    print("\nFirst 3 rows:")
    print(df.head(3).to_string())
except Exception as e:
    print("Error:", e)
