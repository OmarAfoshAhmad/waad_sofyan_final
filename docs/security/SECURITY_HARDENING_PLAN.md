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
| S-02 | CRITICAL | `PreAuthPortalController` بلا `@PreAuthorize` ولا `FeatureGuard` ويكتب فعلياً | **FIXED** |
| S-03 | HIGH | `providerId`/`memberId` من جسم الطلب ⟶ IDOR/BOLA | **FIXED** (providerId) |
| S-04 | HIGH | لا `PreAuthAccessScopeResolver` مقابل نظيره في المستفيدين | **FIXED** |
| S-05 | HIGH | مساران متوازيان للمصادقة (Session + JWT) | **FIXED** |
| S-06 | MEDIUM→LOW | فجوة «آمن بالافتراض» في الملف الأساس (الإنتاج كان محصَّناً أصلاً) | **FIXED** |
| S-07 | LOW | عامل عمل BCrypt الافتراضي (10) | **FIXED** |
| S-09 | MEDIUM | رؤوس أمان ناقصة (لا `Referrer-Policy` ولا CSP) | **FIXED** |
| S-10 | MEDIUM | الرفع يثق بـ`Content-Type` القادم من العميل، ولا سقف لحجم الملفات الطبية | **FIXED** |
| S-08 | MEDIUM | مبررات حرة عن أشخاص مُعرَّفين في الروابط (تُسجَّل في سجلات الوصول والوكيل وتاريخ المتصفح) | **FIXED** |

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

---

## المرحلة 3 — تأمين PreAuthPortalController

**الحالة:** مكتملة ✅

### القرار: Deny first لا حذف

لا مستهلك في الواجهة (`grep` = صفر)، لكن **الحذف كان سيكون خطأً**:
`PreAuthPortalIdentityFailsClosedTest` يستدعي هذا الـcontroller مباشرة، وكُتب
خصيصاً لمنعه من تلفيق هوية المستفيد والمزوّد والتصنيف. أي أن الفريق يعتبره
سطحاً حياً ويصونه. طُبّق نص قاعدة الوثيقة: **Deny it first ثم أعد بناءه**.

### ما تغيّر

| النقطة | الحارس المضاف |
|---|---|
| `POST /` | `PREAUTH_CREATE` |
| `GET /` | `PREAUTH_VIEW` |
| `GET /{id}` | `PREAUTH_VIEW` |
| `PUT /{id}/draft` | `PREAUTH_CREATE` |
| `POST /bulk` | `PREAUTH_CREATE` + `requireProviderPortal()` + `requireDirectPreauthSubmission()` |
| `POST /{id}/attachments` | `PREAUTH_CREATE` |

بوابة الميزة على `/bulk` هي نفسها التي يطبّقها `PreAuthorizationController` على
مسار الإنشاء المشروع. بدونها كان تعطيل البوابة يُخفي الشاشة بينما تستمر هذه
النقطة في قبول الكتابة — بوابة «مطفأة» في كل مكان إلا حيث تهم.

### ملاحظة على نموذج الصلاحيات

`DATA_ENTRY` **لا يملك `PREAUTH_CREATE`** في قوالب `V191`، بينما الحارس القديم
`hasAnyRole('SUPER_ADMIN','PROVIDER_STAFF','DATA_ENTRY')` على الـcontroller
المشروع يمنحه ذلك. النموذج الجديد أضيق — وهو تشديد مقصود من مسار الترحيل
المتوازي، لا سهو.

### انحدار حقيقي ظهر وعولج بلا إضعاف

الحزمة الكاملة أظهرت **5 إخفاقات** في `PreAuthPortalIdentityFailsClosedTest`:

```
Expecting throwable message: "Access Denied"  to contain: "المستفيد"
```

السبب: الاختبار يستدعي الـbean مباشرة، فصار أمن الميثود يعترضه قبل بلوغ
تحقق الهوية. **نيّة الاختبار سليمة تماماً** — العلاج كان منحه سياق مصادقة، لا
تخفيف الحارس (وهو ما تحظره الوثيقة صراحةً).

اختير `SUPER_ADMIN` تحديداً: يملك الكتالوج كاملاً، و`FeatureGuard.isStaff()`
يقصّر عليه، فتبقى تأكيداته عن **الهوية الملفَّقة** ولا تتحول إلى اختبار ثانٍ
للتصريح أو لحالة أعلام الميزات.

### الاختبارات

`PreAuthPortalRequiresAuthorizationTest` — 4 اختبارات:

| الاختبار | يثبت |
|---|---|
| `anonymousCannotWriteThroughThePortal` | رفض + **عدد الصفوف لم يتغير** |
| `authenticatedWithoutPreauthPermissionCannotWrite` | المصادقة وحدها لا تكفي (`FINANCE_VIEWER`) |
| `anonymousCannotReadThroughThePortal` | القراءة محروسة أيضاً |
| `everyPortalEndpointDeclaresAPermission` | حارس لكل mapping — نقطة تُضاف لاحقاً لا ترث الانفتاح القديم |

### النتيجة

```
م2:   Tests=1034  Failures=0
م3:   Tests=1038  Failures=0     BUILD SUCCESS
```

### ما تبقّى على هذه النقطة

`providerId` و`memberId` ما زالا يأتيان من جسم الطلب لا من هوية المستدعي، فحاملُ
`PREAUTH_CREATE` يستطيع الكتابة باسم مزوّد آخر. هذه **S-03**، ولا تُصلَح دون
محلّل النطاق في **S-04**.

---

## المرحلتان 4 و5 — محلّل النطاق وإزالة IDOR

**الحالة:** مكتملتان ✅ — نُفِّذتا معاً لأن محلّلاً بلا مستهلك كود ميت.

### النموذج

`PreAuthAccessScope` — على غرار `MemberAccessScope` عمداً: هو المكوّن الذي أوقف
سلسلة S-01 عن بلوغ بيانات المستفيدين، ونموذج نطاق ثانٍ بقواعد مختلفة يعني
شيئاً ثانياً يُفكَّر فيه تحت الضغط.

| Kind | المعنى |
|---|---|
| `GLOBAL` | مدير النظام والمراجعون |
| `PROVIDERS` | موظف مقدم الخدمة — محدود بمرفقه |
| `EMPLOYERS` | مدير جهة العمل — محدود بجهته |
| `DENIED` | كل ما عدا ذلك، وكل نطاق غير محدد |

الموافقة المسبقة تقع على **محورين** في آن: المزوّد الذي قدّمها، وجهة عمل
المستفيد الذي طُلبت له. لذا `covers(providerId, employerId)`.

### قرار سياسة موثَّق: المراجعون عالميون

`isReviewer` ⟶ `GLOBAL`. مراجعة الطلبات عبر كل المزوّدين **هي العمل نفسه**، لا
تصعيد صلاحية؛ ومراجع محدود بجهة واحدة لا يستطيع تشغيل الصندوق أصلاً. والمنح
أضيق مما يبدو: نقاط المراجعة تشترط `PREAUTH_REVIEW`/`PREAUTH_APPROVE` فوقه،
**والنطاق لا يمنح عملية أبداً**.

هذا يختلف عن `MemberAccessScopeResolver` الذي يحصر المراجع بجهته — لأن ذاك يحكم
بيانات المستفيدين لا صندوق المراجعة.

### إزالة IDOR

```java
// قبل
Long providerId = Long.valueOf(payload.get("providerId").toString());

// بعد
PreAuthAccessScope scope = scopeResolver.resolve();
if (scope.isDenied()) throw new AccessDeniedException(scope.reason());
Long providerId = scope.singleProviderId().orElse(null);   // المزوّد يكتب كنفسه
// ومن له نطاق أوسع يجب أن يسمّي المزوّد صراحةً، ويُرفض أي مزوّد خارج نطاقه
```

**حساب المزوّد لا يستطيع تسمية أحد غيره — حتى لو سمّى نفسه بشكل صحيح.** قبول
معرّف مطابق كان سيُبقي النقطة تثق بالجسم، وهي عين العلّة.

### الاختبارات

`PreAuthAccessScopeResolverTest` — 12 اختباراً (مصفوفة الأدوار كاملة).
`PreAuthPortalProviderScopeTest` — 4 اختبارات تكامل بمزوّدَين حقيقيين:

| الاختبار | يثبت |
|---|---|
| `providerCannotFileInAnotherProvidersName` | **لا صف** تحت المزوّد الأجنبي |
| `theRequestIsFiledUnderTheAuthenticatedProviderNotTheClaimedOne` | الصف يقع تحت المستدعي |
| `aProviderNeedNotSupplyItsOwnIdAtAll` | الجسم لم يعد يحمل ما تعرفه الجلسة |
| `anAccountWithNoProviderScopeIsRefusedRatherThanDefaulted` | مزوّد بلا مرفق = رفض لا عالمية |

### ملاحظة تشغيلية

أعلام `PROVIDER_PORTAL_ENABLED` و`DIRECT_PREAUTH_SUBMISSION_ENABLED` مبذورة
**معطّلة** في `V25` — افتراض إنتاجي سليم. اختبارات النطاق تفعّلها صراحةً، وإلا
ردّ `FeatureGuard` بـ503 قبل بلوغ المنطق محل الاختبار.

### النتيجة

```
م3:     Tests=1038  Failures=0
م4+5:   Tests=1054  Failures=0     BUILD SUCCESS
```

### ما تبقّى

`memberId` ما زال يأتي من الجسم. المحلّل جاهز لفحصه (`covers` يقبل محور جهة
العمل)، لكن ربطه يحتاج حلّ جهة عمل المستفيد بتاريخ الخدمة — وهو عمل المرحلة 6.

---

## المرحلة 6 — إغلاق المسار المشروع

**الحالة:** مكتملة ✅

### الاكتشاف: ثلاث فتحات fail-open في دالة واحدة

`PreAuthorizationService.validateAndEnforceProviderId` — الحارس الذي يقرر تحت أي
مزوّد تُقيَّد الموافقة — كان مفتوحاً من ثلاث جهات:

| # | الفتحة | الأثر |
|---|---|---|
| 1 | `if (currentUser == null) { log.warn(...); return; }` | مبدأ بلا هوية ⟶ يتخطى التحقق ويكمل |
| 2 | `canAccessInternalOperations` ⟶ «أي مزوّد مسموح» | يشمل `DATA_ENTRY` |
| 3 | `// Other roles: no restriction on providerId` | كل دور آخر بلا قيد — **بالصمت** |

الفتحة الثالثة أخطرها: قائمة `hasAnyRole` على الـcontroller كانت الشيء الوحيد
الذي يضيّق هذا. وهذا **فخ لمسار ترحيل RBAC الجاري**: توسيع التعليق التوضيحي كان
سيوسّع انتحال المزوّد معه بالصمت.

### ما تغيّر

الدالة أُعيدت كتابتها لتقرر بالنطاق لا بالدور:

```java
PreAuthAccessScope scope = preAuthAccessScopeResolver.resolveFor(currentUser);
if (scope.isDenied()) throw new AccessDeniedException(scope.reason());

scope.singleProviderId().ifPresent(...)   // المزوّد يكتب كنفسه دائماً
// ومن لا مزوّد وحيد له: يجب أن يسمّيه، ويُعاد فحصه مقابل نطاقه
```

**لا فرع صامت.** أي حالة لا يغطيها النطاق ترفع استثناءً.

### لماذا كان الإغلاق آمناً

`createPreAuthorization` له **مستدعٍ واحد** (الـcontroller المحروس) ولا مهام
مجدولة في الوحدة. فحص المستدعين سبق التعديل، لا بعده.

### الاختبارات

`ProviderEnforcementFailsClosedTest` — 4 اختبارات مصدرية تثبت غياب مخارج الهروب:

| الاختبار | يثبت |
|---|---|
| `aDeniedScopeStopsTheRequestRatherThanBeingLogged` | الرفض يرفع استثناءً لا سطر سجل |
| `internalRolesNoLongerNameAnyProviderFreely` | `canAccessInternalOperations` لم تعد بوابة |
| `aWiderScopeMustNameAProviderAndBeNarrowedToIt` | التسمية إلزامية ثم تُفحص |
| `aProviderIsStillForcedToFileAsItself` | المزوّد يُقيَّد بنفسه |

فحص مصدري عمداً: اختبار زمن التشغيل يُظهر أن مساراً يرفض، ولا يُظهر أن فرعاً
غير محروس لم يُضَف من جديد.

### النتيجة

```
م4+5:  Tests=1054  Failures=0
م6:    Tests=1058  Failures=0     BUILD SUCCESS
```

---

## المرحلة 9 — مسار مصادقة واحد للويب

**الحالة:** مكتملة ✅

### الإثبات قبل الحذف

الوثيقة تشترط إثبات الاستخدام الحالي أولاً. الأدلة الست:

| السؤال | الدليل |
|---|---|
| هل ترسل الواجهة `Authorization: Bearer`؟ | **لا** — صفر نتيجة |
| هل تخزّن توكناً؟ | **لا** — `sessionStorage.clear()` عند الخروج فقط |
| ماذا تستدعي فعلاً؟ | `/auth/session/login`, `/session/me`, `/session/logout` وإعادة تعيين كلمة المرور — **لا شيء غيرها** |
| هل يوجد عميل جوال؟ | **لا** — `audience: tba-waad-mobile` طموح لا واقع |
| اختبارات تعتمده؟ | واحد فقط، ويؤكد **عدم** إصداره: `$.data.token` `doesNotExist` |
| هل يتشارك مسار الجلسة كوده؟ | **لا** — `sessionLogin` يستدعي `getUserInfo` لا `login` |

### لماذا كان يجب أن يزول لا أن يُحسَّن

مسارا مصادقة متوازيان يعنيان أن أمن التطبيق = **أضعف المسارين**، وأن كل حارس
جديد يجب أن يصمد عليهما معاً.

والأهم أنه يخرق النموذج الذي يقوم عليه باقي النظام:
`PermissionAdministrationService` يستدعي `revokeAll()` بعد كل تغيير صلاحية —
**والتوكن ليس صفاً يمكن حذفه**، فحامله يحتفظ بمكانته حتى انتهاء مدته وحدها.

### ما حُذف

| العنصر | السبب |
|---|---|
| `jwtAuthenticationFilter` من السلسلة | المسار الثاني للمصادقة |
| `POST /api/v1/auth/login` | سطح ميت — كان سيمنح توكناً لا يصادق شيئاً |
| `GET /api/v1/auth/me` | سطح ميت (يتطلب Bearer) |
| `POST /api/v1/auth/refresh-token` | سطح ميت |
| `/api/v1/auth/login` من القائمة العامة واستثناءات CSRF | إعدادات ميتة |

### الاختبارات

`WebAuthenticationIsSessionOnlyTest` — 4 اختبارات:

| الاختبار | يثبت |
|---|---|
| `jwtIsNotAcceptedForWebAuthentication` | توكن ظاهرياً سليم يشتري **لا شيء** (401) |
| `theJwtOnlyEndpointsAreGone` | النقاط الثلاث اختفت |
| `sessionLoginStillWorksAndIssuesNoToken` | لم نكسر الدخول |
| `theSecurityChainRegistersNoJwtFilter` | فلتر مصادقة ثانٍ لا يعود بالإغفال |

### النتيجة

```
م6:   Tests=1058  Failures=0
م9:   Tests=1062  Failures=0     BUILD SUCCESS
```

### ما تبقّى من سباكة JWT

`JwtTokenProvider` و`JwtAuthenticationFilter` ما زالا موجودين كأصناف،
و`AuthService.login()` ما زال يولّد توكناً (يصل إليه `register` وحده). التوكن
**خامل تماماً** الآن — لا فلتر يقبله. تنظيفه النهائي مع متطلب `JWT_SECRET` في
`StartupSecurityValidator` بند مستقل، لأنه تنظيف لا إغلاق ثغرة.

---

## المرحلة 12 — تقوية كوكي الجلسة

**الحالة:** مكتملة ✅

### تصحيح للتقييم الأولي

الفحص الأول قرأ `application.yml` وحده فأبلغ أن كوكي الجلسة بلا `Secure` ولا
`SameSite`. **هذا كان ناقصاً**: `application-prod.yml` يضبط الثلاثة أصلاً:

```yaml
secure: true   |   same-site: strict   |   http-only: true
```

فالإنتاج **لم يكن مكشوفاً**. الخطورة تُخفَّض من MEDIUM إلى LOW، والفجوة
الحقيقية مختلفة عمّا وُصف.

### الفجوة الحقيقية

القيم المحصَّنة كانت في ملف البروفايل لا في الأساس. أي بروفايل غير `prod` —
و**أي نشر ينسى تفعيل `prod`** — كان يرث كوكي عارياً. الخلل في **موضع** القيمة
لا في القيمة.

### ما تغيّر

| الملف | التغيير |
|---|---|
| `application.yml` | `secure: ${SESSION_COOKIE_SECURE:true}` · `same-site: ${SESSION_COOKIE_SAME_SITE:Strict}` · `path: /` |
| `application-dev.yml` | `secure: false` صراحةً — وهي الاسترخاء الوحيد المسموح |
| `application-prod.yml` | بلا تغيير — كان محصَّناً |

**المبدأ:** الافتراض هو الآمن، والاسترخاء يُذكر بصوت مسموع حيث ينطبق فعلاً — لا
العكس.

### لماذا Strict لا Lax

ترددت أولاً لأن طوبولوجيا النشر غير قابلة للتحديد من المستودع، و`Strict` قد
يكسر تنقّلاً عابر المواقع. ثم حسم الدليل الأمر: **الإنتاج يعمل بـ`strict`
فعلاً**، فهي مُثبَتة عملياً لا تخميناً. و`SameSite` يُقيَّم بالنطاق المسجَّل
ويتجاهل المنفذ، فمنفذا التطوير المنفصلان يبقيان same-site.

### الاختبارات

`SessionCookieHardeningTest` — 3 اختبارات:

| الاختبار | يثبت |
|---|---|
| `theDefaultProfileShipsAHardenedSessionCookie` | الأساس لا يعود لكوكي عارٍ |
| `productionRemainsAtLeastAsStrictAsTheDefault` | الإنتاج لا يتراجع |
| `developmentRelaxesOnlyTheSecureFlagAndSaysWhy` | التطوير يرخي `Secure` فقط — لا `SameSite` ولا `HttpOnly` |

الثالث هو الأهم: يمنع أن تصير «احتياجات التطوير» ثغرة ترتدي ثياب مطوّر.

### النتيجة

```
م9:    Tests=1062  Failures=0
م12:   Tests=1065  Failures=0     BUILD SUCCESS
```

---

## المرحلة 13 — لا بيانات حساسة في الروابط (S-08)

**الحالة:** مكتملة ✅

### المسح

| الفئة | النتيجة |
|---|---|
| كلمات مرور / توكنات / أسرار في `@RequestParam` | **صفر** ✓ |
| مبررات حرة (`reason`) في الرابط | **10 نقاط** عبر 4 وحدات ✗ |
| بيانات تعريف في فلاتر البحث | `nationalNumber`, `barcode`, `cardNumber` — بند مفتوح |

### لماذا `reason` تحديداً

نص حر يكتبه موظف **عن شخص مُعرَّف**: لماذا أُنهيت تغطيته، لماذا أُلغيت موافقته
المسبقة، لماذا عُلِّق عقد مزوّده. في نظام تأمين صحي هذا يذكر روتينياً حالة
مرضية أو ظرفاً شخصياً.

والرابط ليس قناة خاصة — يُكتب حرفياً في:
سجل وصول Tomcat · كل وكيل عكسي أمامه · تاريخ المتصفح · ترويسة `Referer` الصادرة.

ولا شيء من ذلك يحترم قواعد الاحتفاظ ولا ضوابط الوصول التي تحكم **جدول التدقيق
الذي يُخزَّن فيه السبب نفسه عمداً**.

### ما تغيّر

`ReasonRequest` مشترك، والمبرر انتقل إلى جسم الطلب في **10 نقاط**:

| الوحدة | النقاط |
|---|---|
| `UnifiedMemberController` | `/active`, `/reinstate`, `/status`, `/restore`, `/hard`, `/terminate` |
| `ClaimController` | `DELETE /{id}` |
| `PreAuthorizationController` | `/{id}/cancel` |
| `ProviderContractController` | `/suspend`, `/terminate` |

وتسع نقاط استدعاء في الواجهة نُقلت من `params` إلى الجسم (و`data` لطلبات
`DELETE`).

**استُثنيت عمداً:** `BenefitLedgerAdminController` — نقطة `multipart/form-data`
حيث `@RequestParam` يربط حقل نموذج في **الجسم** لا في الرابط. القيمة لا تصل
الرابط أصلاً.

**بقي في الرابط عمداً:** `status` و`active` — قيم تعدادية غير حساسة، ونقلها
لا يضيف حماية.

### الاختبارات

`NoSensitiveDataInUrlsTest` — يمسح **كل** الـcontrollers مقابل قائمة محظورة
(`reason`, `password`, `token`, `otp`, `secret`, `iban`, …)، مع استثناء
واعٍ لنقاط multipart. حارس معماري يمنع عودة النمط في نقطة جديدة.

### النتيجة

```
م12:   Tests=1065  Failures=0
م13:   Tests=1067  Failures=0     BUILD SUCCESS
```

### بند مفتوح مرتبط

فلاتر البحث ما زالت تمرر `nationalNumber` و`barcode` و`cardNumber` في الرابط
(`GET`). تحويلها إلى `POST` يكسر دلالات البحث والتخزين المؤقت، والعلاج الأنسب
تنقية سجلات الوصول أو تجزئة المعرّف — قرار تشغيلي لا برمجي بحت.

---

## المراحل 15 و17 و19–24 — المسح الختامي

**الحالة:** مكتملة ✅

### ما كان سليماً أصلاً (فحص لا تعديل)

| المرحلة | النتيجة |
|---|---|
| **19 — حقن SQL** | ✓ كل `createNativeQuery` مُمَعلَم بـ`setParameter`؛ التجميع أسطر نصية ثابتة لا مدخلات |
| **20 — Mass Assignment** | ✓ لا كيان (`Member`, `User`, `Claim`…) مكشوف في `@RequestBody`؛ DTOs محددة |
| **22 — معالجة الأخطاء** | ✓ `include-stacktrace: never` و`include-message: never`؛ والمعالج العام يسجّل الأثر خادمياً ويعيد معرّف تتبّع فقط |
| **23 — الأسرار** | ✓ لا سر ثابت في `application*.yml`؛ `StartupSecurityValidator` يمنع الإقلاع بدونها |
| **24 — Actuator** | ✓ `/actuator/**` محصور بـ`SUPER_ADMIN`، و`health` وحده عام |

نضج ملحوظ في معالجة الأخطاء: المعالج يترجم قيود Postgres إلى رسائل عمل عربية
حتى لا يسرّب أسماء القيود ومعرّفات الصفوف والمبالغ المحتجزة.

### م17 — رؤوس الأمان (S-09)

كان `frameOptions` وحده مضبوطاً. أُضيفت:

| الرأس | القيمة | لماذا |
|---|---|---|
| `Referrer-Policy` | `no-referrer` | **مكمّل مباشر لـS-08**: الروابط الباقية تحمل معرّفات مستفيدين ومرشّحات بحث؛ بدونه يرفق المتصفح العنوان كاملاً بكل طلب يغادر لأصل آخر |
| `X-Content-Type-Options` | `nosniff` | يمنع تخمين نوع المحتوى — الخطوة التي تحوّل ملفاً مرفوعاً يُعاد تقديمه إلى سكربت |
| `Strict-Transport-Security` | سنة + النطاقات الفرعية | ذو معنى على TLS حيث يعمل الإنتاج |
| `Content-Security-Policy` | `default-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'self'` | الخدمة تردّ JSON ولا تقدّم أصولاً خارجية؛ و`'self'` يغطي Swagger UI المحصور بمدير النظام |
| `Permissions-Policy` | كاميرا/ميكروفون/موقع/دفع = `()` | لا دور لها في التطبيق |

الاختبارات تفحص **الاستجابة الفعلية** لا ملف الإعداد — فما يحمي المتصفح هو
الرأس الذي يصل.

### م15 — كلفة تجزئة كلمة المرور (S-07)

الوثيقة تمنع تغيير العامل عشوائياً. القياس على آلة التطوير:

| Cost | ms/hash |
|---|---|
| 10 (السابق) | 127 |
| 11 | 225 |
| **12 (الجديد)** | **403** |
| 13 | 760 |

اختير **12**: داخل نطاق 250–500ms الذي يُبقي كسر التجزئة دون اتصال مكلفاً بلا
أن يمنح هجوم حشو بيانات الاعتماد شريحة كبيرة من خيط الطلب. و13 يضاعف كلفة
المهاجم لكنه يضاعف أيضاً ما ينفقه الخادم نيابة عنه.

قابل للضبط (`security.bcrypt-strength`) لأن القياس من حاسوب لا من خادم
الإنتاج، مع حدّ أدنى 10 يرفض الإقلاع دونه.

**التجزئات القائمة لا تتأثر**: BCrypt يخزّن كلفته داخل التجزئة، فكلمات المرور
المشفّرة بـ10 تظل تتحقق. تبقى عند 10 حتى يغيّرها صاحبها — وترحيلها يحتاج
إعادة تجزئة عند الدخول الناجح، وهو تحسين مستقل لا إصلاح ثغرة.

### النتيجة

```
م13:   Tests=1067  Failures=0
م17:   Tests=1075  Failures=0     BUILD SUCCESS
```

---

# بوابة الإنتاج

كل مربع ومعه الأثر الذي يثبته — لا تُعلَّم بالثقة.

## مؤتمَتة (يفرضها البناء)

| # | البند | الأثر المُثبِت |
|---|---|---|
| 1 | مجهول لا يستطيع التسجيل | `PublicRegistrationClosedIntegrationTest.anonymousCannotRegister` |
| 2 | رمز CSRF لا يكفي بلا صلاحية | `…anonymousWithCsrfStillCannotRegister` |
| 3 | المسار الإداري المشروع سليم | `…administratorWithUserManageCanStillRegister` |
| 4 | إقلاع التطبيق لم يُكسر | `…publicAuthEndpointsRemainReachable` |
| 5 | لا دور داخلي افتراضي | `NoImplicitUserRoleTest` (4) |
| 6 | بوابة المزوّد محروسة كلياً | `PreAuthPortalRequiresAuthorizationTest` (4) |
| 7 | المصادقة وحدها لا تكفي للكتابة | `…authenticatedWithoutPreauthPermissionCannotWrite` |
| 8 | نطاق غير محدد = رفض | `PreAuthAccessScopeResolverTest` (12) |
| 9 | مزوّد لا يكتب باسم آخر | `PreAuthPortalProviderScopeTest` (4) |
| 10 | لا فرع fail-open في فرض المزوّد | `ProviderEnforcementFailsClosedTest` (4) |
| 11 | JWT لا يُقبل للويب | `WebAuthenticationIsSessionOnlyTest` (4) |
| 12 | كوكي محصَّن بالافتراض | `SessionCookieHardeningTest` (3) |
| 13 | لا بيانات حساسة في الروابط | `NoSensitiveDataInUrlsTest` (2) |
| 14 | رؤوس الأمان تصل فعلاً | `SecurityHeadersTest` (5) |
| 15 | كلفة التجزئة مقاسة ولا تُبطل القائم | `PasswordHashingStrengthTest` (3) |
| 16 | الرفع يُفحص بالبايتات لا بالادعاء | `UploadContentValidationTest` (7) |

**المجموع: 61 اختبار انحدار أمني.**

## مُتحقَّقة بالفحص (لا تعديل)

| البند | الدليل |
|---|---|
| لا حقن SQL | كل `createNativeQuery` مُمَعلَم |
| لا Mass Assignment | لا كيان في `@RequestBody` |
| لا تسريب آثار الأخطاء | `include-stacktrace: never` + معرّف تتبّع |
| لا أسرار في المستودع | مسح `application*.yml` |
| Actuator محصور | `hasRole("SUPER_ADMIN")` |
| قفل الحساب قائم | `max-failed-login-attempts: 5` / 30 دقيقة |

## متبقية — تتطلب نظاماً مشغَّلاً أو قراراً تشغيلياً

| البند | لماذا لم يُغلق |
|---|---|
| **النشر** | لا شيء من هذا يحمي الإنتاج قبل الدمج وإعادة النشر |
| دورة متصفح بحسابات حقيقية | تتطلب تسجيل دخول فعلياً بكل دور |
| `memberId` من جسم `/bulk` | يحتاج حلّ جهة عمل المستفيد بتاريخ الخدمة |
| مرشّحات البحث (`nationalNumber`…) | العلاج تنقية سجلات الوصول أو تجزئة المعرّف — قرار تشغيلي |
| ترحيل تجزئات كلمات المرور القائمة | يحتاج إعادة تجزئة عند الدخول الناجح |
| تنظيف سباكة JWT الميتة | تنظيف لا ثغرة؛ فُصل عمداً عن commits أمنية |

| مصفوفة التصعيد الكاملة (م29–30) | تحتاج بيانات متعددة المستأجرين |

---

# الخلاصة

| ID | الخطورة | الحالة |
|---|---|---|
| S-01 | 🔴 حرجة | **FIXED** |
| S-02 | 🔴 حرجة | **FIXED** |
| S-03 | 🟠 عالية | **FIXED** (المزوّد) |
| S-04 | 🟠 عالية | **FIXED** |
| S-05 | 🟠 عالية | **FIXED** |
| S-06 | 🟢 منخفضة¹ | **FIXED** |
| S-07 | 🟢 منخفضة | **FIXED** |
| S-08 | 🟡 متوسطة | **FIXED** |
| S-09 | 🟡 متوسطة | **FIXED** |

¹ خُفِّضت من MEDIUM بعد اكتشاف أن الإنتاج كان محصَّناً أصلاً.

```
الأساس:  Tests=1026  Failures=0  Errors=0
النهاية: Tests=1080  Failures=0  Errors=0
git diff --check: نظيف
```

**لا يُعتبر أي بند مغلقاً تشغيلياً قبل الدمج والنشر ودورة المتصفح.**

---

## المرحلة 18 — أمن رفع الملفات (S-10)

**الحالة:** مكتملة ✅

### ما كان سليماً

اجتياز المسار (`path traversal`) محمي بثلاث طبقات: `cleanPath` ثم إزالة `..`
و`/` ثم تحقق `startsWith(uploadPath)` على المجلد والهدف معاً. واسم الملف يُسبَق
بـUUID. و`SVG` لم يكن في قائمة السماح أصلاً.

### الفجوات الثلاث

| # | الفجوة | الأثر |
|---|---|---|
| 1 | القبول يعتمد `file.getContentType()` | ترويسة يرسلها العميل — صفحة سكربت تُرفع كـ`image/png` وتمرّ كما هي |
| 2 | لا فحص للامتداد | `invoice.png.html` يحتفظ بامتداده عبر التنقية |
| 3 | `ALLOWED_MEDICAL_TYPES` بلا سقف حجم | لا `isImageType` ولا `isDocumentType` يغطيه ⟶ **رفع DICOM بلا حد إطلاقاً** |

### ما تغيّر

**فحص البايتات لا الادعاء** — تُقرأ بداية الملف وتُطابَق التوقيع:

| النوع | التوقيع |
|---|---|
| PDF | `%PDF` |
| JPEG | `FF D8 FF` |
| PNG | `89 50 4E 47` |
| DICOM | `DICM` عند الإزاحة 128 |

الأنواع بلا توقيع مستقر (حاوية OOXML لوورد، صيغ DICOM بلا مقدّمة) تمرّ — رفض ما
لا يمكن التحقق منه كان سيرفض ملفات سريرية مشروعة، ويبقى فحص الامتداد وقواعد
التخزين ساريَين عليها.

**قائمة امتدادات محظورة** بغضّ النظر عن النوع المُعلَن: `html`, `svg`, `js`,
`php`, `jsp`, `exe`, `sh`, `jar`, `htaccess`… — لأن الامتداد هو ما سيستخدمه خادم
ويب أو وكيل أو سطح مكتب زميل لاحقاً ليقرر **ما هذا الملف**، فيجب أن يتفق مع
الادعاء.

**سقف للملفات الطبية**: `file.storage.max-size.medical` (200MB افتراضاً).
التصوير الطبي كبير مشروعاً — لكن «كبير» ليست «بلا حد».

### الاختبارات

`UploadContentValidationTest` — 7، تغطي الاتجاهين:

| الاختبار | يثبت |
|---|---|
| `scriptDisguisedAsAnImageIsRejectedOnItsBytes` | الانتحال يُكشف |
| `aDangerousExtensionIsRefusedEvenWithAConvincingContentType` | `invoice.png.html` يُرفض |
| `anSvgIsRefusedBecauseItCarriesScript` | SVG مرفوض صراحةً |
| `aGenuineImageIsStillAccepted` · `aGenuinePdfIsStillAccepted` | **لم نكسر الرفع المشروع** |
| `medicalImagingNowHasASizeCeiling` | السقف الغائب صار قائماً |
| `pathTraversalInTheFilenameCannotEscapeTheUploadDirectory` | الحماية القائمة ما زالت تعمل |

### النتيجة

```
م17:   Tests=1075  Failures=0
م18:   Tests=1082  Failures=0     BUILD SUCCESS
```
