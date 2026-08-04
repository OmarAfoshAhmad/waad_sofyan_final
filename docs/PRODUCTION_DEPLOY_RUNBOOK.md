# دليل نشر التحديثات على الإنتاج (VPS)

> السيرفر: `tbawaad@waadapp-ly`, المسار: `/opt/waadapp`
> Docker Compose ثلاث خدمات: `db` (Postgres)، `backend` (Spring Boot + Flyway)، `frontend` (nginx يخدم React + يعكس `/api/` للباك-إند)

---

## 0) القاعدة الذهبية

**لا تُشغّل `docker compose up -d --build` على كل الخدمات دفعة واحدة أبداً.** أعِد بناء الخدمة التي تغيّر كودها فقط:

| تغيّر ماذا؟ | أعِد بناء |
|---|---|
| كود Java فقط (`backend/`) | `backend` فقط |
| كود React/nginx فقط (`frontend/`) | `frontend` فقط |
| `docker-compose.yml` فقط (بدون كود) | لا بناء — فقط `docker compose up -d <service>` |
| كلاهما | ابنِ كل خدمة **على حدة** بالترتيب: `backend` أولاً (وتحقق من نجاح الـ migrations) ثم `frontend` |

---

## 1) قبل أي `git pull` — تحقق من عدم وجود تعديلات محلية غير محفوظة

```bash
cd /opt/waadapp
git status --short
```

- إن ظهر شيء غير متوقع في `docker-compose.yml`، `frontend/nginx.conf`، أو أي ملف كود — **لا تحذفه بدون فهمه**. هذه غالباً كانت السبب في تعطّل السحب (`git pull`) ليلة الإصلاح الأخيرة.
- الاستثناء الوحيد الآمن دائماً: `ssl/fullchain.pem` و`ssl/privkey.pem` يظهران دائماً كـ"معدَّلين" (`M`) — هذا طبيعي ومقصود (الشهادات الحقيقية على السيرفر تختلف عن نسخة git الوهمية). **لا تُصلحهما ولا تُنفّذ `git checkout` عليهما أبداً.**

---

## 2) نسخة احتياطية من قاعدة البيانات (إلزامية قبل أي تحديث يمس الـ backend)

```bash
mkdir -p /opt/waadapp/db_backups
docker exec waadapp-db pg_dump -U postgres -d tba_waad_system -F c -f /tmp/backup.dump
docker cp waadapp-db:/tmp/backup.dump /opt/waadapp/db_backups/backup_$(date +%Y%m%d_%H%M%S).dump
ls -la /opt/waadapp/db_backups/   # تحقق أن الحجم منطقي (ليس صفراً)
```

---

## 3) السحب

```bash
git pull
git log --oneline -1   # تأكد أن الـ commit المتوقع وصل فعلاً
```

⚠️ إن ظهرت رسالة `Already up to date` لكن `git log -1` يُظهر commit قديماً — فهذا يعني أن السحب **لم يحدث فعلياً** (كان محظوراً بتعديل محلي). راجع الخطوة 1.

---

## 4) إعادة البناء — استخدم دائماً `--no-cache` عند الشك

البناء العادي:
```bash
docker compose build backend      # أو frontend
```

إن فشل البناء لسبب غامض (تبعيات، ملفات قديمة مخبأة في طبقات Docker)، أعِد المحاولة بدون أي طبقة مخبأة:
```bash
docker compose build --no-cache backend
```

**لا تنتقل للخطوة التالية قبل أن ترى بوضوح رسالة نجاح البناء** (`✔ Image ... Built` أو مكافئها). إن ظهر `failed to solve` أو `exit code: 1` — توقف وشخّص السبب أولاً.

---

## 5) إعادة التشغيل

```bash
docker compose up -d --force-recreate backend   # أو frontend
```

`--force-recreate` إلزامي — بدونه قد لا يُعيد Docker Compose إنشاء الحاوية حتى لو تغيّرت الصورة، خصوصاً إن لم يتغيّر `docker-compose.yml` نفسه.

---

## 6) التحقق — لا تفترض النجاح، تأكّد منه

### للـ backend (تحقق من Flyway تحديداً):
```bash
sleep 20
docker compose ps backend                      # يجب أن تكون healthy
docker inspect waadapp-backend --format '{{.State.StartedAt}}'   # تأكد أنه إقلاع اليوم فعلاً

docker compose logs backend --tail=1000 | grep -v DEBUG | grep -iE \
  "Started TbaWaadApplication|APPLICATION FAILED|FlywaySqlScriptException|PSQLException"

docker exec waadapp-db psql -U postgres -d tba_waad_system -c \
  "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;"
```

### للـ frontend (تحقق من HTTPS فعلياً، ليس فقط HTTP):
```bash
sleep 5
docker compose ps frontend
curl -sk https://localhost -o /dev/null -w "%{http_code}\n"   # يجب أن يطبع 200
```

إن أعطى `curl` خطأ SSL (`unexpected eof`) رغم أن المنافذ تبدو صحيحة في `docker compose ps` — تحقق من أن `frontend/nginx.conf` المبني فعلياً داخل الحاوية يحتوي `listen 443 ssl` (وليس نسخة قديمة مخبأة):
```bash
docker exec waadapp-frontend grep -c "listen 443" /etc/nginx/conf.d/default.conf
```

---

## 7) لماذا فشلت الأمور ليلة 4 أغسطس — دروس مستفادة

| المشكلة | السبب | الدرس |
|---|---|---|
| السحب فشل بصمت عدة مرات | تعديلات يدوية مباشرة على السيرفر (`sed -i` على `docker-compose.yml`) لم تُحفظ في git | لا تُعدّل ملفات على السيرفر يدوياً كحل دائم — أصلح الكود محلياً، ادفعه، ثم اسحبه |
| البناء فشل (`framer-motion`/`motion-dom`) | لا يوجد `package-lock.json` في المستودع، و`Dockerfile` استخدم `npm install` بدل `npm ci` | ✅ أُصلح دائماً — الآن يُستخدم `npm ci` بلوكفايل مثبّت |
| توقف HTTPS بالكامل | إعادة بناء frontend لأول مرة منذ 3 أسابيع كشفت أن `nginx.conf` الحالي لا يحتوي إعداد SSL إطلاقاً (هجرة معمارية غير مكتملة نحو reverse proxy خارجي معطّل) | ✅ أُصلح دائماً — SSL يُنهى الآن داخل حاوية frontend مباشرة |
| migration V84 فشلت | افترضت قاعدة بيانات فارغة تماماً لكن وُجد صف يتيم واحد في `medical_services` | كل migration جديد يجب اختباره ذهنياً ضد بيانات حقيقية موجودة، لا فقط ضد قاعدة تطوير فارغة |

**الخلاصة العملية:** كل هذه المشاكل ظهرت لأن الخدمات لم تُعَد بناؤها لمدة 3 أسابيع، فتراكمت عدة تغييرات غير مُختبَرة معاً على الإنتاج دفعة واحدة. **انشر بشكل متكرر وصغير** (كل بضعة أيام) بدل تراكم أسابيع من التغييرات — كل نشر متأخر يعني مخاطرة أكبر واكتشافاً أصعب لأي مشكلة.
