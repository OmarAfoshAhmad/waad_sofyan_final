import pandas as pd
try:
    df = pd.read_excel('تعديل_قائمة_عمليات_دار_الشفاء_منظم.xlsx')
    print("Columns for second file:", df.columns)
except Exception as e:
    print(e)
