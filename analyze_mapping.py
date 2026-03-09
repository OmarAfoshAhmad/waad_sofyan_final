import pandas as pd
import sys

def analyze():
    try:
        template = pd.read_excel('Price_List_Contract_4 (1).xlsx')
        print("Template Columns:", template.columns.tolist())
        print("Template First Row Sample:", template.head(1).to_dict(orient='records'))
        
        source = pd.read_excel('مصحة منارة المستقبل درنة.xlsx')
        print("\nSource Columns:", source.columns.tolist())
        print("Source First Row Sample:", source.head(1).to_dict(orient='records'))
        print("Source Unique Categories:", source['التصنيف'].unique().tolist())
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    analyze()
