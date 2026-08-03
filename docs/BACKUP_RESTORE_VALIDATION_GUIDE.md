# دليل تجربة Backup / Restore

التاريخ: 2026-08-02  
السكربت: `scripts/test-postgres-backup-restore.ps1`

## الهدف

التأكد أن نسخة PostgreSQL الاحتياطية قابلة للاسترجاع فعلياً على قاعدة اختبار، بدون المساس بقاعدة العمل.

## التشغيل الافتراضي

من جذر المشروع:

```powershell
.\scripts\test-postgres-backup-restore.ps1 -ResetRestoreDatabase
```

السكربت يقرأ `.env` تلقائياً:

- `POSTGRES_DB`
- `POSTGRES_USER`
- `DB_PASSWORD`

إذا لم يجدها يستخدم:

- قاعدة المصدر: `tba_waad_system`
- المستخدم: `postgres`
- المضيف: `localhost`
- المنفذ: `5432`

## ماذا يفعل السكربت؟

1. يتحقق من وجود أدوات PostgreSQL:
   - `pg_dump`
   - `pg_restore`
   - `psql`
   - `createdb`
   - `dropdb`
2. ينشئ backup بصيغة custom:
   - `backups/<database>_<timestamp>.dump`
3. ينشئ قاعدة اختبار باسم:
   - `<database>_restore_test`
4. يسترجع النسخة عليها.
5. يقارن أعداد الجداول المهمة بين الأصل والاسترجاع.
6. يخرج تقرير Markdown داخل مجلد `backups`.

## حماية مهمة

السكربت لا يسترجع أبداً على قاعدة العمل.

قاعدة الاسترجاع يجب أن ينتهي اسمها بـ:

```text
_restore_test
```

إلا إذا استخدمت الخيار المتقدم:

```powershell
-AllowCustomRestoreDatabase
```

لا تستخدم هذا الخيار إلا إذا كنت متأكداً.

## تشغيل بقيم مخصصة

```powershell
.\scripts\test-postgres-backup-restore.ps1 `
  -HostName localhost `
  -Port 5432 `
  -User postgres `
  -SourceDatabase tba_waad_system `
  -RestoreDatabase tba_waad_system_restore_test `
  -ResetRestoreDatabase
```

## معيار النجاح

الاختبار ناجح إذا:

- تم إنشاء ملف `.dump` بحجم أكبر من صفر.
- تم إنشاء قاعدة الاسترجاع.
- تم restore بدون أخطاء قاتلة.
- الجداول الأساسية موجودة.
- أعداد السجلات في الجداول المهمة مطابقة.
- يظهر التقرير بنتيجة: `ناجح`.

## بعد نجاح السكربت

اختبار إضافي موصى به قبل الإنتاج:

1. شغّل backend مؤقتاً على قاعدة:
   - `tba_waad_system_restore_test`
2. تأكد من:
   - تسجيل الدخول.
   - فتح المستفيدين.
   - فتح المطالبات.
   - فتح الوثائق.
   - فتح قواعد التغطية.
   - فتح القاموس الطبي.

## إذا فشل الاختبار

لا تعتبر backup صالحة للإنتاج.

راجع:

- هل كلمة المرور صحيحة؟
- هل أدوات PostgreSQL موجودة في PATH؟
- هل قاعدة المصدر موجودة؟
- هل توجد migrations مكسورة؟
- هل توجد جداول مفقودة أو أسماء مختلفة عن المتوقع؟

