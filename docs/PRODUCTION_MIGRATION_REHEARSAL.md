# بروفة الهجرات قبل النشر

> **الحالة:** إلزامية متى تجاوزت الفجوة بين إصدار الإنتاج وإصدار الكود **خمس هجرات**.
>
> **السبب:** ليست احتياطاً نظرياً. كُتبت بعد نشر 2026-09-01 الذي كشف أن الإنتاج
> كان متأخراً **45 هجرة** لا تسعاً كما افترض الدليل.

## لماذا

قبل النشر يُفترض أن الفجوة صغيرة ومعروفة. الواقع خالف ذلك:

| المفترض | الواقع |
|---|---|
| الإنتاج عند V198 | **V165** |
| ثلاث هجرات ستُنفَّذ | **45** |
| هجرات بلا حرّاس | **25 هجرة تحوي 68 `RAISE EXCEPTION`** |

خمسٌ وعشرون من الهجرات مصمَّمة **لتُفشِل النشر** إن لم تطابق البيانات توقّعاتها —
وهذا سلوك صحيح ومقصود، لكنه يعني أن نشراً أعمى على فجوة كبيرة قد يتوقف في
منتصف تحويل بيانات ويترك القاعدة بين نموذجين.

الهجرات الأثقل حراسةً: `V174` (12 حارساً، دلالات الدفتر المالي)، `V173` (7)،
`V182` (6)، `V181` و`V184` (5 لكلٍّ).

## متى تجب البروفة

```text
فجوة ≤ 5 هجرات، ولا واحدة منها تكتب بيانات   → نشر مباشر بعد نسخة احتياطية
فجوة > 5 هجرات، أو أي هجرة تكتب/تحوّل بيانات → بروفة إلزامية
```

هجرة «تكتب بيانات» = تحوي `INSERT`, `UPDATE`, `DELETE` أو backfill، لا `ALTER TABLE` وحدها.

## الفحص المسبق

قبل أي شيء، اعرف الفجوة الحقيقية:

```bash
docker exec waadapp-db psql -U postgres -d tba_waad_system -tAc "
SELECT CASE
  WHEN EXISTS (SELECT 1 FROM flyway_schema_history WHERE success = false)
    THEN 'STOP — توجد هجرة فاشلة سابقة'
  ELSE 'آخر إصدار مطبق: V' || (SELECT max(version::int) FROM flyway_schema_history
                               WHERE version ~ '^[0-9]+\$')
END;"
```

قارنه بأعلى إصدار في `backend/src/main/resources/db/migration`. الفرق هو الفجوة.

**وافحص تصادم الأرقام** — هجرتان مختلفتان بنفس الرقم تمنعان Flyway من الإقلاع:

```bash
ls backend/src/main/resources/db/migration | grep -oE "^V[0-9]+__" | sort | uniq -d
```
**يجب أن يكون فارغاً.**

## البروفة

كلها على خادم الإنتاج، ولا شيء منها يعدّل `tba_waad_system`.

```bash
cd /opt/waadapp
git status --short          # فارغ، وإلا توقف
git fetch origin && git checkout main && git pull --ff-only origin main
git rev-parse --short HEAD

docker compose build backend    # الهجرات مضمَّنة في الـjar — البناء ضروري قبل البروفة
```

```bash
# نسخة احتياطية — تأكد أن حجمها غير صفري
docker exec waadapp-db pg_dump -U postgres tba_waad_system \
  | gzip > /opt/waadapp/backup_pre_deploy_$(date +%F_%H%M).sql.gz
ls -lh /opt/waadapp/backup_pre_deploy_*.sql.gz
```

```bash
# قاعدة بروفة من نفس النسخة
docker exec waadapp-db psql -U postgres -c "DROP DATABASE IF EXISTS rehearsal;"
docker exec waadapp-db psql -U postgres -c "CREATE DATABASE rehearsal;"
gunzip -c /opt/waadapp/backup_pre_deploy_*.sql.gz \
  | docker exec -i waadapp-db psql -U postgres -d rehearsal -q

# تأكد أنها طبق الأصل: يجب أن تطبع نفس إصدار الإنتاج
docker exec waadapp-db psql -U postgres -d rehearsal -tAc \
  "SELECT max(version::int) FROM flyway_schema_history WHERE version ~ '^[0-9]+\$';"
```

```bash
# شغّل الهجرات على البروفة وحدها — حاوية مؤقتة، لا تمسّ العاملة
docker compose run --rm \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/rehearsal \
  backend 2>&1 | tee /opt/waadapp/rehearsal.log
```

اتركها حتى `Started TbaWaadApplication` أو خطأ، ثم `Ctrl+C`.

## قراءة النتيجة

```bash
grep -iE "Migration of schema|APPLICATION FAILED|FlywaySqlScriptException" /opt/waadapp/rehearsal.log

docker exec waadapp-db psql -U postgres -d rehearsal -tAc \
  "SELECT max(version::int) FROM flyway_schema_history WHERE version ~ '^[0-9]+\$' AND success;"

docker exec waadapp-db psql -U postgres -d rehearsal -c \
  "SELECT version, description FROM flyway_schema_history WHERE NOT success;"
```

| الناتج | القرار |
|---|---|
| أعلى إصدار = المتوقَّع، و`(0 rows)` | ✅ انشر |
| رقم أقل | ❌ حارس صدّ — صحّح البيانات، لا الهجرة |

**إن صدّ حارس:** رسالته تسمّي الصف أو الشرط المخالف. الإصلاح يكون في البيانات
عبر المسار الوظيفي المعتمد، **لا بتعديل الهجرة ولا بحذف صفوف
`flyway_schema_history`**.

## بعد نجاح البروفة

انشر بالترتيب المعتاد، **وتحقق من أعلى إصدار فعلي لا من رقم محفوظ في دليل قديم**:

```bash
docker compose up -d --force-recreate backend
docker compose ps backend      # انتظر healthy

docker compose logs backend --tail=1000 | grep -iE \
  "Started TbaWaadApplication|APPLICATION FAILED|FlywaySqlScriptException"

docker exec waadapp-db psql -U postgres -d tba_waad_system -tAc \
  "SELECT max(version::int) FROM flyway_schema_history WHERE version ~ '^[0-9]+\$' AND success;"

docker exec waadapp-db psql -U postgres -d tba_waad_system -c \
  "SELECT version, description FROM flyway_schema_history WHERE NOT success;"
```

ثم — **وفقط بعد نجاح ما سبق** — الواجهة:

```bash
docker compose build frontend
docker compose up -d --force-recreate frontend
docker compose ps frontend                                    # CREATED بالثواني لا بالأسابيع
curl -sk https://localhost -o /dev/null -w "%{http_code}\n"   # 200
```

> **تحقق من `CREATED` فعلاً.** في نشر 2026-09-01 ظهرت الواجهة تخدم `200` بينما
> حاويتها عمرها ثلاثة أسابيع — أي أن الخلفية الجديدة كانت تعمل مع واجهة قديمة.
> استجابة `200` وحدها لا تثبت أن النشر جرى.

## تنظيف

```bash
docker exec waadapp-db psql -U postgres -c "DROP DATABASE rehearsal;"
```

**أبقِ قاعدة البروفة حتى تنجح اختبارات الدخان** — إن ظهر سلوك غريب، مقارنة
الإنتاج بها تفصل بين خطأ الهجرة وخطأ الكود. واحتفظ بالنسخة الاحتياطية أسبوعاً
على الأقل.

## سِجل

| التاريخ | من → إلى | الهجرات | النتيجة |
|---|---|---|---|
| 2026-09-01 | V165 → V210 | 45 | البروفة نجحت، النشر طابقها، 0 فاشلة |
