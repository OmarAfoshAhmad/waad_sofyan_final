import pandas as pd
import sys

try:
    df = pd.read_excel('قائمة اسعار خدمات دار الشفاء مصنفة.xlsx')
    print("Columns:", df.columns.tolist())
    print("\nFirst 10 rows:")
    print(df.head(10).to_string())
    
    # Try common Arabic/English column names for classification
    for col in df.columns:
        if any(keyword in col for keyword in ['تصنيف', 'التصنيف', 'Category', 'Classification']):
            print(f"\nUnique values in {col}:")
            print(df[col].unique())
        
except Exception as e:
    print(f"Error: {e}")
