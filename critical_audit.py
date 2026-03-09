import pandas as pd
import sys
sys.stdout.reconfigure(encoding='utf-8')
output_file = r"d:\tba_waad_system-main\tba_waad_system-main\Dar_Shifa_Import_Ready.xlsx"
try:
    df = pd.read_excel(output_file)
    # Check services that might be misplaced
    print("Potential IP misclassified as OP:")
    # Look for OP services with keywords like surgery, operation, theater, or high prices
    ip_keywords = ['جراحة', 'عملية', 'تخدير', 'غرفة', 'إقامة', 'عناية', 'رعاية']
    mis_op = df[(df['category / التصنيف'] == 'خارج المستشفى (OP)') & 
                (df['service_name / اسم الخدمة ★'].str.contains('|'.join(ip_keywords), case=False, na=False))]
    print(mis_op.head(15).to_markdown())
    
    print("\nHigh Price OP (Potential IP/Major Surgery?):")
    high_op = df[(df['category / التصنيف'] == 'خارج المستشفى (OP)') & (df['unit_price / السعر'] > 500)]
    print(high_op.head(15).to_markdown())
    
    print("\nCategorization Distribution:")
    print(df['category / التصنيف'].value_counts().to_markdown())
except Exception as e:
    print(f"Error: {e}")
