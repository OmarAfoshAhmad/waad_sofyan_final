import pandas as pd

def read_source():
    # Attempt to find which sheet and which row header starts
    xls = pd.ExcelFile('مصحة منارة المستقبل درنة.xlsx')
    for sheet in xls.sheet_names:
        print(f"\n--- Sheet: {sheet} ---")
        df = pd.read_excel('مصحة منارة المستقبل درنة.xlsx', sheet_name=sheet)
        print("First 10 rows:")
        print(df.head(10))

if __name__ == "__main__":
    read_source()
