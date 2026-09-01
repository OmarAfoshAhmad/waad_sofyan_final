# دليل Claude لدمج التحديث الحالي ونشره على الإنتاج

> ## ⚠️ أرقام هذا الملف تخص إصداره وحده — لا تنقلها
>
> نُشر هذا الإصدار فعلاً بتاريخ 2026-09-01، ثم دخلت بعده هجرات `V208`–`V210`.
> كل موضع أدناه يقول «تحقق من `V207`» صار **مضلِّلاً**: مَن يتبعه سيرى `V207`
> ناجحة فيكمل إلى الواجهة، بينما هجرات لاحقة قد تكون فشلت.
>
> **القاعدة الصحيحة:** تحقّق دائماً من أعلى إصدار موجود فعلاً في
> `backend/src/main/resources/db/migration`، لا من رقم محفوظ في دليل.
>
> نشر 2026-09-01 كشف أيضاً أن الإنتاج كان متأخراً **45 هجرة** لا تسعاً، منها 25
> تحمل حرّاساً تُفشِل النشر. لذلك صارت البروفة على نسخة إلزامية قبل أي فجوة
> كبيرة — الإجراء الدائم في
> [`PRODUCTION_MIGRATION_REHEARSAL.md`](./PRODUCTION_MIGRATION_REHEARSAL.md)،
> وهو المرجع عند أي تعارض مع ما يلي.

> **الحالة:** إجراء إلزامي لهذا الإصدار وما بعده.
>
> **الفرع المصدر:** `feature/benefit-group-compatibility`
>
> **الفرع الإنتاجي:** `main`
> **النطاق:** إدخال المطالبات، سياقات المطالبة وتصنيف قوائم الأسعار، هجرات `V199` إلى `V207`، وإصلاحا صفحة بيانات المستخدم وقائمة عقود مقدمي الخدمة.

هذا الملف يكمّل [`PRODUCTION_DEPLOY_RUNBOOK.md`](./PRODUCTION_DEPLOY_RUNBOOK.md). عند التعارض، تُطبّق القاعدة الأكثر تحفظاً، ولا تُتجاوز بوابة الإنتاج في `CLAUDE.md`.

## 1. ما الذي يدخل هذا الإصدار

- إنشاء المطالبة المباشرة ذرياً وبهوية تمنع التكرار عند إعادة المحاولة.
- حل الوثيقة والمال والسياق بتاريخ الخدمة، والتحقق من الموافقة المسبقة المؤهلة.
- سياقات مطالبة ديناميكية، مع فصل سياق المطالبة عن تصنيف المنفعة.
- مطابقة تصنيفات قوائم الأسعار مع قواعد التغطية، بما فيها الرنين والعلاج الطبيعي والأسنان.
- الصيدلة تصنيف منفعة وليست سياق مطالبة.
- الأسنان التجميلية غير مغطاة، ولا تُرحّل إلى تصنيف الأسنان المتقدم.
- إصلاح صفحة `/admin/users/{id}` التي كانت تنهار بسبب غياب استيراد `useAuth`.
- تمرير صلاحيات الجلسة إلى شريط التنقل، لكي يظهر رابط عقود مقدمي الخدمة عند وجود `CONTRACT_VIEW` ويختفي عند سحبها.
- هجرات قاعدة البيانات: `V199`، `V200`، `V201`، `V202`، `V203`، `V204`، `V205`، `V206`، `V207`.

## 2. بوابة الدمج المحلية — لا تدمج إن فشل أي بند

نفّذ من worktree نظيف للفرع المصدر:

```bash
git status --short
git fetch origin
git merge-base --is-ancestor main HEAD

cd backend
mvn test

cd ../frontend
npm test
npm run build

cd ..
git diff --check main...HEAD
```

النتيجة المطلوبة:

- `git status --short` فارغ.
- اختبار `merge-base` يرجع exit code `0`.
- اختبارات الخلفية والواجهة كلها خضراء.
- بناء Vite ناجح.
- لا أخطاء whitespace.

## 3. دمج آمن في `main`

استخدم worktree منفصلاً ونظيفاً لـ`main` كي لا تلمس أي عمل غير ملتزم في worktree آخر:

```bash
git worktree add <clean-main-worktree> main
cd <clean-main-worktree>
git status --short
git pull --ff-only origin main
git merge --ff-only feature/benefit-group-compatibility
git log --oneline --decorate main~15..main
git push origin main
```

قواعد إلزامية:

- لا تستخدم `reset --hard`، ولا تنظف ملفات مستخدم آخر.
- لا تستخدم merge قسرياً إذا لم يعد الدمج `fast-forward`؛ حدّث الفرع المصدر واختبره من جديد.
- لا تدفع إذا كانت الشجرة غير نظيفة أو تغيّر رأس `origin/main` أثناء التحقق.
- سجّل SHA النهائي المنشور، ثم تحقّق أن `git rev-parse main` يساوي `git rev-parse origin/main`.

## 4. تحديث الإنتاج

الإصدار يغيّر Backend وFrontend وقاعدة البيانات. الترتيب المعتمد:

```text
نسخة قاعدة بيانات
→ سحب main
→ بناء backend
→ تشغيل backend والتحقق من Flyway حتى V207
→ بناء frontend
→ تشغيل frontend
→ اختبارات دخان وظيفية
```

على الخادم:

```bash
cd /opt/waadapp
git status --short
```

إذا ظهر تعديل غير متوقع فتوقف. لا تلمس شهادات `ssl/fullchain.pem` و`ssl/privkey.pem`. ثم أنشئ النسخة الاحتياطية حسب الدليل الأساسي، وتأكد أن ملفها غير صفري.

بعد النسخة الاحتياطية:

```bash
git fetch origin
git checkout main
git pull --ff-only origin main
git rev-parse HEAD

docker compose build backend
docker compose up -d --force-recreate backend
```

تحقق قبل لمس الواجهة:

```bash
docker compose ps backend
docker compose logs backend --tail=1000 | grep -v DEBUG | grep -iE \
  "Started TbaWaadApplication|APPLICATION FAILED|FlywaySqlScriptException|PSQLException"
docker exec waadapp-db psql -U postgres -d tba_waad_system -c \
  "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 12;"
```

يجب أن تظهر `V207` ناجحة، ولا يظهر `APPLICATION FAILED`. بعدها فقط:

```bash
docker compose build frontend
docker compose up -d --force-recreate frontend
docker compose ps frontend
curl -sk https://localhost -o /dev/null -w "%{http_code}\n"
```

يجب أن تكون الاستجابة `200`.

## 5. اختبارات الدخان بعد النشر

نفّذ بحساب يملك الصلاحيات المناسبة، ولا تعتمد على ظهور الصفحة الرئيسية وحده:

1. تسجيل الدخول بنجاح، ثم فتح بيانات مستخدم حقيقي؛ لا تظهر شاشة الخطأ العامة.
2. فتح «مقدمو الخدمات»؛ يظهر «عقود مقدمي الخدمات» لمن يملك `CONTRACT_VIEW`.
3. سحب `CONTRACT_VIEW` من مستخدم اختباري؛ الرابط يختفي والطلب المباشر يُرفض من الخادم.
4. فتح إدخال مطالبة والتأكد من ظهور سياقات المطالبة الفعالة من قاعدة البيانات.
5. تجربة خدمة رنين وخدمة علاج طبيعي من قائمة سعر مستوردة؛ تُربطان بتصنيف التغطية الصحيح.
6. تجربة خدمة أسنان تجميلية؛ تظهر غير مغطاة ولا تُعامل كتركيبات/زراعة/تقويم.
7. إنشاء مطالبة تجريبية واحدة، ثم إعادة نفس طلب الإنشاء؛ لا تُنشأ مطالبتان.
8. التحقق من السقف العام وسقف المنفعة قبل الحفظ وبعده، ومنع التجاوز فعلياً من الخادم.

سجّل وقت الاختبار، المستخدم، النتيجة، وSHA المنشور. احذف أو اعكس بيانات الاختبار عبر المسار الوظيفي المعتمد، لا بـSQL يدوي.

## 6. التراجع والحوادث

- **لا تعكس هجرات Flyway يدوياً ولا تحذف صفوف `flyway_schema_history`.**
- إن فشل Backend أثناء Flyway: اترك Frontend القديم، اجمع السجلات، ولا تعاود التشغيل بشكل أعمى.
- إن نجح Flyway وفشلت الواجهة: أبق Backend الجديد وشغّل صورة Frontend السابقة إن كانت متوافقة.
- التراجع عن الكود يكون بالتزام `revert` جديد ومراجَع، وليس بإعادة كتابة تاريخ `main`.
- استعادة نسخة قاعدة البيانات إجراء كارثي أخير يحتاج إيقاف الكتابة، موافقة صريحة، وخطة فقد بيانات؛ لا ينفذه Claude تلقائياً.

## 7. تقرير الإغلاق المطلوب من Claude

لا يقل التقرير النهائي عن:

```text
Source SHA:
Main SHA:
Origin/main SHA:
Backup path and size:
Backend image/build result:
Frontend image/build result:
Flyway latest version: V207 / success:
Backend health:
Frontend HTTPS status:
Smoke tests 1–8:
Unexpected changes or warnings:
```

عبارة «تم النشر» لا تُستخدم قبل اكتمال هذه الأدلة.
