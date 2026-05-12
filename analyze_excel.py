import pandas as pd
import warnings
warnings.filterwarnings('ignore')

source_file = r"d:\tba_waad_system-main_success\tba_waad_system-main\تعديل قائمة عمليات دار الشفاء.xlsx"

print("=" * 80)
print("SOURCE FILE - Reading with header=None (raw rows)")
print("=" * 80)

xl = pd.ExcelFile(source_file)
print("Sheet names:", xl.sheet_names)

for sheet in xl.sheet_names:
    print(f"\n\n{'='*60}")
    print(f"SHEET: {sheet}")
    print(f"{'='*60}")
    df = pd.read_excel(source_file, sheet_name=sheet, header=None)
    print(f"Total rows: {df.shape[0]}, Total cols: {df.shape[1]}")
    print("\n--- ALL NON-EMPTY ROWS (first 60 rows) ---")
    for i, row in df.head(60).iterrows():
        # Show rows that have at least one non-NaN value
        non_null = row.dropna()
        if len(non_null) > 0:
            print(f"Row {i}: {non_null.to_dict()}")

    print("\n--- SAMPLE FROM MIDDLE ---")
    mid = df.shape[0] // 2
    for i, row in df.iloc[mid:mid+20].iterrows():
        non_null = row.dropna()
        if len(non_null) > 0:
            print(f"Row {i}: {non_null.to_dict()}")

    print("\n--- LAST 20 ROWS ---")
    for i, row in df.tail(20).iterrows():
        non_null = row.dropna()
        if len(non_null) > 0:
            print(f"Row {i}: {non_null.to_dict()}")
