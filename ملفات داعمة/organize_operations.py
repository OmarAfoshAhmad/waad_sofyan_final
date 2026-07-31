import pandas as pd
import numpy as np

source_file = r"d:\tba_waad_system-main_success\tba_waad_system-main\تعديل قائمة عمليات دار الشفاء.xlsx"
output_file = r"d:\tba_waad_system-main_success\tba_waad_system-main\تعديل_قائمة_عمليات_دار_الشفاء_منظم.xlsx"

print("جارٍ قراءة الملف المصدر وبحث كافة أوراق العمل (Sheets)...")
try:
    xl = pd.ExcelFile(source_file)
    all_dfs = []
    
    # تجميع البيانات من جميع الأوراق (لأن الورقة الأولى قد تكون غلافاً فقط)
    for sheet in xl.sheet_names:
        df = pd.read_excel(source_file, sheet_name=sheet, header=None)
        all_dfs.append(df)
        
    df_combined = pd.concat(all_dfs, ignore_index=True)
    
    # نحتفظ بالصفوف التي تحتوي على اسم عملية في العمود رقم 1
    df_clean = df_combined.dropna(subset=[1]).copy()
    
    # بناء الداتا فريم بالشكل النهائي المطلوب
    df_final = pd.DataFrame()
    df_final['السعر'] = df_clean[0]
    df_final['العملية'] = df_clean[1]
    df_final['التخصص'] = df_clean[2]
    
    if 3 in df_clean.columns:
        df_final[' '] = df_clean[3]
    else:
        df_final[' '] = ""
        
    # تصفية البيانات:
    # 1. إزالة أي صفوف تمثل "ترويسة" من داخل البيانات نفسها
    df_final = df_final[~df_final['العملية'].astype(str).str.contains('العملية|اسم العملية', na=False, regex=True)]
    
    # 2. إزالة الصفوف التي تحتوي فقط على اسم (مثل التاريخ أو الترويسات العشوائية) وتفتقر للسعر والتخصص معاً
    df_final = df_final.dropna(subset=['السعر', 'التخصص'], how='all')
    
    df_final = df_final.dropna(subset=['العملية'])
    
    # إنشاء أعمدة مساعدة للفرز لضمان عدم حدوث خطأ بسبب القيم الفارغة
    df_final['التخصص_للفرز'] = df_final['التخصص'].fillna('ي_غير محدد')
    df_final['النوع_للفرز'] = df_final[' '].fillna('ي_غير محدد')
    
    # الفرز حسب التصنيف الرئيسي (التخصص) ثم الفرعي (النوع)
    df_final = df_final.sort_values(by=['التخصص_للفرز', 'النوع_للفرز'])
    
    # حذف أعمدة الفرز المساعدة
    df_final = df_final.drop(columns=['التخصص_للفرز', 'النوع_للفرز'])
    
    print(f"تم استخراج وتنظيم {len(df_final)} عملية بنجاح من جميع الأوراق المتاحة.")
    
    # الحفظ في ملف إكسيل جديد
    print("جارٍ حفظ الملف المنظم...")
    with pd.ExcelWriter(output_file, engine='openpyxl') as writer:
        df_final.to_excel(writer, index=False, sheet_name='العمليات')
        
        # تنسيق عرض الأعمدة ليكون مقروءاً بوضوح
        worksheet = writer.sheets['العمليات']
        worksheet.column_dimensions['A'].width = 15
        worksheet.column_dimensions['B'].width = 60
        worksheet.column_dimensions['C'].width = 30
        worksheet.column_dimensions['D'].width = 15
        
    print("-" * 50)
    print(f"✅ تم الانتهاء بنجاح! الملف المنظم جاهز للاستيراد وموجود في المسار التالي:\n{output_file}")
    print("-" * 50)

except Exception as e:
    print(f"حدث خطأ أثناء المعالجة: {e}")
