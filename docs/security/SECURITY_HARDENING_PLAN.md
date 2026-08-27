# خطة التحصين الأمني — منظومة وعد

**الفرع:** `security/hardening-phase-1` (worktree معزول: `C:/tmp/wt-security`)
**نقطة الانطلاق:** `5c31d9f8`
**بدء العمل:** 2026-08-27

---

## لماذا worktree منفصل

الشجرة الأصلية (`C:/tmp/wt-member-closure`) تعمل عليها جلسة موازية تنفّذ ترحيل
صلاحيات (`hasRole` ⟶ `@permissionGuard.has`). قياس أساس أول هناك فشل بـ346
`NoClassDefFoundError` لأن `mvn compile` المتوازي أعاد كتابة `target/classes`
أثناء تشغيل الاختبارات. العزل هنا يخصّ مجلد البناء، لا الملفات — إذ لا تقاطع
بين ملفات المسارين (59 ملفاً لهم، 6 لنا، تقاطع = صفر).

## توافق اللهجة الأمنية

الجلسة الموازية ترحّل التصريح إلى `@permissionGuard.has('X')`
(189 مُطبَّقة مقابل 263 متبقية بـ`hasRole`). لذلك **كل إصلاح في هذه الخطة
يُكتب باللهجة الجديدة** تفادياً لعمل مكرر في أخطر مواضع النظام.

تحقّق من سلامة الاعتماد عليها قبل البناء فوقها:

| الفحص | النتيجة |
|---|---|
| `authentication == null` | `false` — fail-closed |
| مستخدم غير موجود | `.orElse(false)` — fail-closed |
| صلاحية غير ممنوحة | `false` — fail-closed |
| `SUPER_ADMIN` | يملك الكتالوج كاملاً (`V191`) |

---

## سجل النتائج

| ID | الخطورة | الوصف | الحالة |
|----|---------|-------|--------|
| S-01 | CRITICAL | تسجيل ذاتي عام + دور افتراضي `DATA_ENTRY` ⟶ سلسلة تصعيد من الإنترنت | **FIXED** (م1 + م2) |
| S-02 | CRITICAL | `PreAuthPortalController` بلا `@PreAuthorize` ولا `FeatureGuard` ويكتب فعلياً | OPEN |
| S-03 | HIGH | `providerId`/`memberId` من جسم الطلب ⟶ IDOR/BOLA | OPEN |
| S-04 | HIGH | لا `PreAuthAccessScopeResolver` مقابل نظيره في المستفيدين | OPEN |
| S-05 | HIGH | مساران متوازيان للمصادقة (Session + JWT) | OPEN |
| S-06 | MEDIUM | كوكي الجلسة بلا `Secure` ولا `SameSite` | OPEN |
| S-07 | LOW | مراجعة عامل عمل BCrypt | OPEN |

**Statuses:** `OPEN` → `MITIGATED` → `FIXED` → `VERIFIED`
لا تُستخدم `FIXED` قبل مرور اختبار يثبتها.

---

## S-01 — تفصيل

### السلسلة المُثبَتة

```
مجهول
 └─ SecurityConfig:91   /api/v1/auth/** = permitAll
     └─ AuthController:275  POST /register  (بلا حارس)
         └─ AuthService:222  User.builder()  لا يضبط userType
             └─ User.java:52  يرث "DATA_ENTRY"
                 └─ AuthService:228  .active(true)
                     └─ AuthService:240  login()  ⟶ جلسة فورية
                         └─ RoleService:57  DATA_ENTRY ∈ internalStaff
                             └─ FeatureGuard:40  if (isStaff()) return;  ⟶ تجاوز البوابات
                                 └─ SecurityConfig:113  anyRequest().authenticated()
                                     └─ PreAuthPortalController  (0 حراس) ⟶ كتابة
```

### دليل الاستغلال (غير مدمِّر)

```
POST /api/v1/auth/register  {}   ⟶ 400 VALIDATION_ERROR   (المعالج نُفِّذ = وصول مجهول)
POST /api/v1/pre-authorizations  ⟶ 403                     (للمقارنة: محمي فعلاً)
```

### ما حال دون الكارثة

`MemberAccessScopeResolver:73` يرفض أي `DATA_ENTRY` بلا `employerId`:
> «لا يمكن تحديد نطاق المستخدم؛ يلزم ربطه بجهة عمل»

دفاع بالعمق نجح — ويجب تعميم نموذجه على وحدة الموافقات (S-04).

### الجذر

فصل مفقود بين **إنشاء الهوية** و**منح الامتياز**: مسار إنشاء عام يرث دوراً
داخلياً بالصمت. المبدأ الواجب: `No explicit role = no privileged account`.

---

## المرحلة 0 — الأساس

| القياس | الشجرة المشتركة | worktree معزول |
|---|---|---|
| Tests | 1027 | **1026** |
| Failures | 32 | **0** |
| Errors | 318 | **0** |
| Skipped | 4 | 4 |
| `NoClassDefFoundError` | 346 | **0** |
| النتيجة | BUILD FAILURE | **BUILD SUCCESS** |

الفارق ليس في الكود بل في `target/` مشترك بين بناءين متزامنين. كل الأصناف
المفقودة كانت أصنافاً يولّدها Lombok (`$...Builder`) — بصمة إعادة كتابة مجلد
الأصناف أثناء التشغيل.

---

## المرحلة 1 — قطع سلسلة التصعيد

**الحالة:** مكتملة ✅

### ما تغيّر

| الملف | التغيير |
|---|---|
| `security/SecurityConfig.java` | استبدال `"/api/v1/auth/**"` بقائمة صريحة من 11 نقطة عامة فعلاً |
| `security/SecurityConfig.java` | إزالة `/register` من استثناءات CSRF |
| `modules/auth/controller/AuthController.java` | `@PreAuthorize("@permissionGuard.has('USER_MANAGE')")` على `/register` |

### لماذا permissionGuard لا hasRole

الجلسة الموازية ترحّل التصريح من `hasRole` إلى `@permissionGuard.has`
(189 مُطبَّقة مقابل 263 متبقية). كتابة الإصلاح باللهجة القديمة كانت ستُنتج
عملاً مكرراً في أخطر سطر بالنظام. و`USER_MANAGE` موجودة أصلاً في
`SystemPermission`، و`SUPER_ADMIN` يملك الكتالوج كاملاً (`V191`).

### ما حُوفظ عليه عمداً

`/api/v1/auth/session/me` بقي عاماً. يرد `200` بحمولة `null` للزائر الأول،
وتستدعيه `AuthContext` عند كل تحميل صفحة للسؤال «هل توجد جلسة؟». إخراجه من
القائمة العامة كان سيكسر إقلاع التطبيق لكل زائر — تشديد أمني يهدم وظيفة.

### الاختبارات

`PublicRegistrationClosedIntegrationTest` — 4 اختبارات:

| الاختبار | يثبت |
|---|---|
| `anonymousCannotRegister` | رفض + **عدم وجود صف** في `users` |
| `anonymousWithCsrfStillCannotRegister` | رمز CSRF صالح لا يكفي بلا `USER_MANAGE` |
| `administratorWithUserManageCanStillRegister` | لم نكسر المسار المشروع |
| `publicAuthEndpointsRemainReachable` | `session/me` و`session/login` ما زالا عامَّين |

التأكيد الجوهري ليس رمز الحالة بل **غياب الصف**: رفضٌ يُبقي المستخدم مكتوباً
ليس رفضاً.

### النتيجة

```
قبل:  Tests=1026  Failures=0  Errors=0
بعد:  Tests=1030  Failures=0  Errors=0     BUILD SUCCESS
git diff --check: نظيف
```

### خطر متبقٍ

النسخة العاملة على المنفذ 8080 مأخوذة من `wt-member-closure` ولا تحمل هذا
الإصلاح. **الثغرة ما زالت مفتوحة في النسخة المشغَّلة** حتى يُدمج هذا الفرع
ويُعاد النشر.

---

## المرحلة 2 — إزالة الامتيازات الافتراضية

**الحالة:** مكتملة ✅

### المسح: أين كان الدور يُمنح بالصمت

ثلاثة مصادر مستقلة، لا واحد:

| # | الموضع | السلوك |
|---|---|---|
| 1 | `User.java:52` | `@Builder.Default userType = "DATA_ENTRY"` |
| 2 | `V5__users.sql:12` | `NOT NULL DEFAULT 'DATA_ENTRY'` |
| 3 | `UserService.resolveUserType` | `return "DATA_ENTRY"` كملاذ أخير |

### مسارات الإنشاء — المسح الكامل

| المسار | قبل | بعد |
|---|---|---|
| `RbacDataInitializer:103` | `SUPER_ADMIN` صريح ✓ | بلا تغيير |
| `UserService:95-98` | `toEntity` ثم `applyRoleBindings` صريح ✓ | بلا تغيير |
| `ClaimReviewService:364,695` | `ACCOUNTANT` صريح ✓ | بلا تغيير |
| `ClaimApprovalRecoveryWorker:30` | `MEDICAL_REVIEWER` صريح ✓ | بلا تغيير |
| `AuthService.register:222` | **لا يضبط شيئاً** ✗ | يتحقق ويُسند صراحةً |

لا وجود لأي `INSERT INTO users` خارج الهجرات.

### ما تغيّر

| الملف | التغيير |
|---|---|
| `modules/rbac/entity/User.java` | حذف `@Builder.Default` — الحقل بلا قيمة ابتدائية |
| `modules/rbac/service/UserService.java` | الملاذ الأخير صار `IllegalArgumentException` |
| `modules/auth/dto/RegisterRequest.java` | حقل `userType` إلزامي (`@NotBlank`) |
| `modules/auth/service/AuthService.java` | تحقق مقابل `SystemRole` ثم إسناد صريح |
| `db/migration/V193__drop_implicit_user_role_default.sql` | `ALTER COLUMN user_type DROP DEFAULT` |

### الهجرة — ما لم تفعله عمداً

- **`NOT NULL` محفوظ** — حساب بلا دور يبقى مستحيلاً.
- **لا صف واحد مسّته.** إعادة تعيين أدوار مستخدمين قائمين قرار تشغيلي يحتاج
  دليلاً لكل حساب، لا `UPDATE` جماعياً داخل هجرة. هذا نص شرط الوثيقة:
  «ممنوع تنفيذ Migration قد يمنح صلاحيات إضافية للمستخدمين الحاليين بالصمت».

### نصف قطر الانفجار

77 استدعاءً لـ`User.builder()` في الاختبارات، **3 ملفات فقط** كانت تعتمد على
الافتراضي — وكلها كائنات وهمية في الذاكرة لا تُحفظ. أُسند لها دور صريح يطابق
سياقها (`DATA_ENTRY`، `SUPER_ADMIN`، `ACCOUNTANT`).

### الاختبارات

`NoImplicitUserRoleTest` — 4 اختبارات مصدرية:

| الاختبار | يثبت |
|---|---|
| `entityDeclaresNoDefaultRole` | لا `@Builder.Default` ولا تهيئة سطرية |
| `newUserCannotInheritDataEntryRole` | لا ملاذ أخير في `resolveUserType` |
| `databaseSuppliesNoDefaultRole` | الافتراضي محذوف و`NOT NULL` باقٍ |
| `registrationRequestRequiresAnExplicitRole` | الدور جزء من الطلب |

الفحص يتجاهل التعليقات قبل المطابقة: تعليق يشرح الافتراضي المحذوف يقتبسه
بالضرورة، ومطابقة النثر بدل الكود تجعل الحارس يفشل على توثيقه هو.

### النتيجة

```
م1:   Tests=1030  Failures=0  Errors=0
م2:   Tests=1034  Failures=0  Errors=0     BUILD SUCCESS
```

V193 طُبِّقت على قاعدة PostgreSQL حقيقية داخل Testcontainers ضمن هذه الحزمة.
