# CLAUDE.md
# دستور الهندسة البرمجية للمشروع
# Project Engineering Constitution

> **الحالة:** إلزامي  
> **النطاق:** كل الكود الحالي والجديد، Backend وFrontend وDatabase وTests وInfrastructure ذات الصلة  
> **الأولوية:** هذا الملف يحدد قواعد التنفيذ. أي قرار معماري جديد يخالفه يحتاج موافقة صريحة قبل التنفيذ.

---

## 0. الهدف

الهدف من هذا الدستور هو إبقاء المشروع:

- منظمًا.
- آمنًا.
- سريعًا.
- واضحًا.
- قليل التعقيد.
- قليل التكرار.
- سهل الاختبار والصيانة.
- ذو مسار واحد معتمد لكل وظيفة.
- بدون حلول موازية أو Legacy غير منضبط.
- بدون ديون تقنية مخفية.
- بدون مبالغة في التجريد أو التصميم.
- بدون Requests أو Queries أو استهلاك موارد غير مبرر.

المبدأ الأعلى:

> **اختر أبسط حل صحيح، آمن، متسق، قابل للاختبار، ويعيد استخدام الموجود قبل إنشاء شيء جديد.**

ترتيب الأولويات:

```text
Correctness
→ Security
→ Consistency
→ Simplicity
→ Reuse
→ Maintainability
→ Performance by measurement
→ Extensibility only when required
```

---

# 1. قواعد ملزمة للوكيل البرمجي

## MUST

يجب على الوكيل قبل كتابة أي كود أن:

1. يبحث عن التنفيذ الحالي المرتبط بالمهمة.
2. يحدد الـ Canonical Path الحالي.
3. يحدد Source of Truth.
4. يحدد ما يمكن إعادة استخدامه.
5. يحدد التكرار أو الـ Legacy المرتبط.
6. يحدد أقل تغيير يحقق المطلوب.
7. يحدد الاختبارات التي ستثبت صحة التغيير.
8. يحدد ما الذي سيُحذف أو يستبدل إن كان هناك مسار قديم.

## MUST NOT

ممنوع على الوكيل:

- إنشاء مسار موازٍ لنفس الوظيفة.
- إنشاء `V2`, `New`, `Enhanced`, `Improved`, `LegacyReplacement` كحل دائم.
- إنشاء abstraction جديد بلا حاجة مثبتة.
- إنشاء dependency جديدة دون مبرر وموافقة عند كونها قرارًا معماريًا.
- تغيير architecture بصمت.
- تغيير schema جذريًا دون خطة migration واضحة.
- إضافة fallback صامت.
- ترك TODO بدل الحل النهائي في مسار إنتاجي.
- نسخ Business Logic إلى أكثر من مكان.
- حماية الصلاحيات في Frontend فقط.
- إخفاء خطأ أمني بتحسين واجهة فقط.
- تعطيل اختبار أو حذفه لجعل الـ build ينجح.
- إضافة Cache أو Queue أو Redis أو WebSocket لمجرد "تحسين الأداء" دون قياس.
- إضافة Request أو Query أو Polling غير مبرر.

---

# 2. قاعدة "طريقة واحدة معتمدة"

لكل مفهوم مهم في المشروع يجب أن توجد **طريقة واحدة معتمدة فقط**.

أمثلة:

- Authentication.
- Authorization.
- Member Context Resolution.
- Employer Resolution by Service Date.
- Permission Evaluation.
- Error Mapping.
- Audit Logging.
- Money Calculation.
- Date/Time Handling.
- DTO Mapping.
- Pagination.
- Workflow State Transitions.
- Sensitive Search Handling.

إذا وجد أكثر من تنفيذ لنفس المفهوم:

```text
حدد التنفيذ المعتمد
→ رحّل المستهلكين إليه
→ اختبر
→ احذف القديم
```

لا يجوز الاحتفاظ بمسارين دائمين "للاحتياط".

---

# 3. تنظيم المشروع حسب المجال

يفضل تنظيم المشروع كـ **Feature-Oriented Modular Monolith**.

مثال:

```text
member/
├── api/
├── application/
├── domain/
└── persistence/

preauthorization/
├── api/
├── application/
├── domain/
└── persistence/

claim/
provider/
employer/
policy/
```

ولا يفضل وضع المشروع كله في مجلدات ضخمة مثل:

```text
controllers/
services/
repositories/
dto/
entities/
```

إذا كانت بنية المشروع الحالية مختلفة، لا تُعاد هيكلتها جذريًا دون حاجة. يتم التحسين تدريجيًا ضمن المهام المرتبطة.

---

# 4. المسار القياسي من قاعدة البيانات إلى الـ Endpoint

## المسار الخارج

```text
Database
→ Persistence Entity / Projection
→ Repository
→ Domain Resolver / Policy عند الحاجة
→ Application Service / Use Case
→ Authorization + Scope Validation
→ Mapper
→ Response DTO
→ Controller
→ Endpoint
```

## المسار الداخل

```text
HTTP Request
→ Controller
→ Request DTO
→ Validation
→ Authorization / Scope
→ Application Service
→ Domain Rules
→ Repository
→ Database
```

## ممنوع

```text
Controller → Repository
Controller → Business Logic
Controller → Database
Entity → API Response مباشرة
Frontend → Database concept مباشرة
Repository → Controller DTO
Mapper → Database access
```

---

# 5. مسؤوليات الطبقات

## Controller

مسؤول عن HTTP فقط:

- استقبال Request.
- Validation الشكلي.
- استدعاء Use Case.
- إعادة Response.

لا يحتوي على:

- Business Rules.
- SQL logic.
- Authorization logic متكرر.
- حسابات مالية.
- Workflow decisions.

## Request / Response DTO

- لا تعرض Entity مباشرة.
- DTO يمثل عقد API فقط.
- لا يعتمد Frontend على تفاصيل Persistence.
- List endpoints تستخدم Summary DTO.
- Details endpoints تستخدم Details DTO.
- لا ترجع حقولًا لا تحتاجها الشاشة.

## Application Service

مسؤول عن تنسيق الـ Use Case:

- Transaction boundary.
- استدعاء Domain Rules.
- Authorization.
- Repository operations.
- Audit.

لا يعيد اختراع قواعد موجودة في Resolver/Policy معتمد.

## Domain

يحتوي الحقيقة الوظيفية:

- Business invariants.
- State transitions.
- Domain calculations.
- Policies.
- Value Objects.

## Repository

مسؤول عن الوصول للبيانات فقط.

لا يقرر:

- هل المستخدم مسموح؟
- هل العملية قانونية وظيفيًا؟
- هل الحالة يمكن انتقالها؟
- ما صلاحية المستخدم؟

---

# 6. قواعد إعادة الاستخدام وعدم التكرار

## القاعدة

> **تشابه الكود لا يعني دائمًا وجوب التجريد. تكرار قاعدة Domain يعني وجوب التوحيد.**

## Rule of Three

- مرة واحدة: اكتبها بوضوح.
- مرتين: راقب التشابه.
- ثلاث مرات: قيّم استخراج abstraction.
- استثناء: Domain Concept واضح يمكن توحيده من البداية.

## ممنوع

```text
CommonUtils
GeneralHelper
SharedHelper
GenericManager
DoEverythingService
```

إلا إذا كان الشيء تقنيًا عامًا فعلًا وله حدود واضحة.

يفضل:

```text
MemberContextResolver
CoverageCalculator
AuthorizationPolicy
AuditService
MoneyAllocator
PolicyResolver
```

---

# 7. منع الـ Overengineering

طبّق YAGNI.

ممنوع إنشاء:

- Interface وله implementation واحد فقط دون سبب معماري.
- Factory دون تعدد حقيقي.
- Strategy دون سلوكيات متعددة فعلية.
- Event Bus بلا حاجة حالية.
- CQRS بلا قياس ومبرر.
- Microservice لمجرد الفصل النظري.
- Generic abstraction لحالة واحدة.
- Framework داخلي جديد لمشكلة بسيطة.

القاعدة:

> **لا نبني للمستقبل المتخيل. نبني للمتطلبات الحالية مع قابلية صيانة معقولة.**

---

# 8. Source of Truth

لكل مفهوم مهم يجب تحديد مصدر الحقيقة.

أمثلة:

```text
Member Status
Employer at Service Date
Policy Context
Preauthorization Status
Claim Status
Permission Set
Financial Balance
```

لا يجوز أن تعاد نفس الحقيقة بخوارزميات مختلفة في أكثر من Service.

إذا كان هناك أكثر من حقل حالة مثل:

```text
status
reviewStatus
issuanceStatus
decisionStatus
```

يجب توثيق العلاقة بينها ومن هو المصدر النهائي للحقيقة.

---

# 9. Legacy والتوافق

الـ Legacy ليس ممنوعًا إذا كان مطلوبًا للتكامل، لكنه يجب أن يكون استثناءً موثقًا.

أي Legacy مسموح يجب أن يحتوي:

```text
Reason:
Canonical replacement:
Who still depends on it:
New code may use it? NO
Removal condition:
Target removal milestone:
Tests protecting migration:
```

ممنوع إبقاء القديم والجديد معًا بلا خطة خروج.

---

# 10. الأمن والصلاحيات

## القاعدة الأساسية

```text
Frontend visibility
+
Backend authorization
+
Scope validation
```

إخفاء العنصر في الواجهة لا يمثل حماية.

## المستخدم الذي لا يملك الصلاحية

لا يرى:

- القائمة.
- الزر.
- التبويب.
- الإجراء.
- الصفحة الوظيفية.

بدل:

```text
زر موجود → يضغط → "ليس لديك صلاحية"
```

نريد:

```text
لا يملك الصلاحية → العنصر غير موجود
```

ومع ذلك يجب أن يرفض Backend الطلب المباشر دائمًا.

## Backend MUST enforce

- Permission.
- Tenant scope.
- Employer scope.
- Provider scope.
- Resource ownership.
- Service-date context عند الحاجة.
- State transition validity.

## IDOR

يجب اختبار:

```text
User A → Resource A = allowed
User A → Resource B = denied / hidden
```

على:

```text
GET
POST
PUT
PATCH
DELETE
BULK
EXPORT
IMPORT
SEARCH
```

حسب ما هو موجود.

---

# 11. سياسة الرسائل والأخطاء

## ما يراه المستخدم

رسائل:

- عربية واضحة.
- قصيرة.
- عملية.
- غير تقنية.
- لا تكشف تفاصيل أمنية.
- تخبر المستخدم بما يحتاجه فقط.

أمثلة جيدة:

```text
رقم البطاقة مستخدم مسبقًا.
تعذر حفظ الطلب. حاول مرة أخرى.
المستفيد غير مؤهل لهذه الخدمة في التاريخ المحدد.
حدث خطأ غير متوقع. حاول مرة أخرى.
```

## ممنوع عرضه للمستخدم

```text
NullPointerException
SQLSTATE
HibernateException
DataIntegrityViolationException
ConstraintViolationException
JWT signature invalid
Stack trace
Class names
Table names
Column names
Internal endpoint details
```

## الهيكل المعتمد

```text
Technical Exception
→ Internal Logging
→ Stable Internal Error Code
→ Safe User Message
```

مثال:

```json
{
  "success": false,
  "code": "MEMBER_CARD_ALREADY_EXISTS",
  "message": "رقم البطاقة مستخدم مسبقًا.",
  "trackingId": "..."
}
```

الـ Frontend لا يعتمد على نص الرسالة لاتخاذ قرار؛ يعتمد على code ثابت.

---

# 12. سياسة الإخفاء الأمني

في الموارد الحساسة، إذا لم يكن المستخدم يملك الوصول فلا تكشف له أن المورد موجود.

بحسب سياسة الـ API يمكن استخدام:

```text
404 Not Found
```

بدل:

```text
403 + "المورد موجود لكن ليس لك"
```

عندما يكون ذلك مناسبًا لمنع Enumeration.

---

# 13. تسجيل الدخول والأخطاء الأمنية

لا تكشف:

```text
البريد موجود لكن كلمة المرور خاطئة.
الحساب غير موجود.
الحساب X مقفل بسبب Y.
```

استخدم رسالة عامة:

```text
اسم المستخدم أو كلمة المرور غير صحيحة.
```

وعند Rate Limit:

```text
تعذر تسجيل الدخول حاليًا. حاول لاحقًا.
```

التفاصيل تذهب إلى Security Logs فقط.

---

# 14. Logging وAudit

## Logging

يستخدم للتشخيص التقني.

لا يسجل بيانات حساسة دون حاجة.

يجب تنقية:

- Password.
- Token.
- Authorization headers.
- National number.
- Sensitive identifiers.
- Medical data غير الضرورية للتشخيص.
- Request bodies الحساسة.

## Audit

يستخدم للأفعال المهمة:

```text
WHO
WHAT
WHEN
RESOURCE
RESULT
TRACKING ID
```

ولا يسجل أسرارًا أو كلمات مرور.

---

# 15. البحث الحساس

لا تضع البيانات الحساسة في URL عندما يمكن تجنب ذلك.

بدل:

```text
GET /members?nationalNumber=...
```

يفضل عند الحاجة:

```text
POST /members/search
```

مع Request Body.

ويجب تنقية Logs وAPM وTracing من القيم الحساسة.

إذا احتجنا lookup hash في قاعدة البيانات، لا نستخدم Hash بسيط لمعرفة ذات مساحة احتمالات صغيرة. يستخدم تصميم آمن مثل keyed HMAC بعد قرار معماري واضح.

---

# 16. الاختبارات

## القاعدة العليا

> **لا يوجد Business Behavior مهم بلا اختبار.**

ليس المقصود اختبار getter/setter بلا منطق.

## يجب اختبار

كل Public Method ذات منطق في:

- Application Service.
- Domain Service.
- Resolver.
- Policy.
- Calculator.
- Validator.

كل:

- Business Rule.
- Permission Rule.
- State Transition.
- Repository custom query.
- Critical Endpoint.
- Error Mapping.
- Financial calculation.
- Temporal rule.
- Security rule.

## Bug Fix

قاعدة إلزامية:

```text
NO BUG FIX WITHOUT REGRESSION TEST
```

الخطوات:

```text
Write failing regression test
→ reproduce bug
→ fix root cause
→ test passes
→ full regression
```

## Permission Tests

لكل Permission:

```text
allowed case
denied case
wrong scope case
```

## State Transition Tests

لكل transition:

```text
valid source → allowed
invalid source → denied
```

## Repository Tests

Custom queries التي تعتمد على PostgreSQL أو التواريخ أو القيود يجب اختبارها Integration Test.

مثال الفترات:

```text
[start, end)
```

يجب اختبار الحدود.

## Test Naming

يفضل:

```text
shouldRejectApprovalWhenReviewerIsOutsideProviderScope
shouldResolveEmployerUsingServiceDate
shouldNotExposeTechnicalExceptionToUser
```

ولا يفضل:

```text
testService
testMethod1
```

## ممنوع

- جعل private method public لأجل الاختبار.
- اختبار تفاصيل التنفيذ بدل السلوك.
- Mock لكل شيء في المسارات الحرجة.
- حذف test فاشل بدل إصلاح السبب.

---

# 17. هرم الاختبارات

نستخدم توازنًا:

```text
Unit Tests
→ Integration Tests
→ E2E for critical workflows
```

المسارات التالية لا تعتمد على mocks وحدها:

- Authorization.
- Tenant isolation.
- Financial calculations.
- Transactions.
- Database constraints.
- Workflow states.
- Concurrency المهمة.

---

# 18. الأداء واستهلاك الموارد

المبدأ:

```text
Do less work.
Fetch less data.
Fetch it fewer times.
Reuse what is already known.
Measure before optimizing.
```

## Requests

قبل إضافة أي Request جديد يجب إثبات الحاجة إليه.

ممنوع:

- Duplicate requests.
- Refetch شامل بعد كل mutation.
- Polling سريع بلا حاجة.
- Request عند كل keystroke.
- تحميل بيانات لا تستخدمها الشاشة.

## Frontend Server State

استخدم الآلية الموحدة الموجودة في المشروع لإدارة Server State.

يجب دعم:

- Request deduplication.
- Appropriate stale time.
- Targeted invalidation.
- Reuse cached server state.
- Controlled refetch.

لا تنشئ أكثر من آلية لإدارة نفس Server State.

## Search

يستخدم Debounce عند البحث النصي.

لا يرسل Request مع كل حرف دون حاجة.

## Pagination

إلزامية للقوائم الكبيرة.

Backend يفرض:

```text
default page size
max page size
```

ولا يثق بالFrontend.

## Response DTOs

القائمة:

```text
Summary DTO
```

التفاصيل:

```text
Details DTO
```

لا نعيد Entity ضخمة لكل جدول.

---

# 19. N+1 وJPA/Hibernate

N+1 ممنوع في المسارات المهمة.

لا تعتمد على Lazy Loading غير المرئي لتجميع API Response.

يجب أن تكون Query Strategy مقصودة:

- Projection.
- Fetch join عند الحاجة.
- Batch fetching عند الحاجة.
- Dedicated query.

المسارات المهمة يجب أن يكون لها Performance Regression Test أو Query Count Assertion حيث يفيد.

---

# 20. Database Indexes

لا تضف Index عشوائيًا.

الخطوات:

```text
Observe query
→ Measure
→ EXPLAIN / EXPLAIN ANALYZE عند الحاجة
→ Add smallest justified index
→ Measure again
```

ضع في الاعتبار تكلفة Index على:

- INSERT.
- UPDATE.
- Storage.

---

# 21. Transactions

Transaction boundary تكون عادة في Application Service / Use Case.

يجب أن تكون قصيرة.

ممنوع إبقاء Transaction مفتوحة أثناء:

- Network call خارجي.
- File processing طويل.
- انتظار.
- عمليات غير DB طويلة.

---

# 22. العمليات الثقيلة

العمليات التالية قد تحتاج Background Job إذا ثبت أنها ثقيلة:

- Large imports.
- Huge exports.
- Massive recalculations.
- Heavy reports.
- Large reconciliations.

لكن لا تنشئ Queue لمهمة بسيطة.

يجب أن يكون القرار مبنيًا على القياس والحاجة.

---

# 23. Bulk Operations

Bulk يجب أن يكون محدودًا.

Backend يفرض:

```text
max batch size
max request body
timeouts
resource bounds
```

لا تحمل عشرات الآلاف من العناصر في الذاكرة بلا حاجة.

لا تستخدم loop + save لكل عنصر إذا كان هناك أسلوب Bulk أكثر أمانًا وكفاءة، لكن لا تضحي بصحة الـDomain أو Audit.

---

# 24. Connection Pool

لا ترفع Connection Pool عشوائيًا.

يجب قياس:

- Active connections.
- Waiting connections.
- Query latency.
- DB CPU.
- App CPU.
- Request concurrency.

زيادة pool ليست دائمًا تحسينًا.

---

# 25. Cache

Cache ليس علاجًا افتراضيًا.

يستخدم عندما:

- البيانات مناسبة للكاش.
- invalidation واضحة.
- القياس يثبت الحاجة.

إذا لم نستطع شرح:

```text
متى يصبح cache stale؟
كيف يتم invalidation؟
```

فلا نستخدمه.

لا نضع بيانات مالية أو Workflow state حساسة في cache بلا تصميم واضح.

---

# 26. Rate Limiting وحماية الموارد

Rate Limit ليس فقط لتسجيل الدخول.

قيّم الحاجة له في:

- Login.
- Password reset.
- Expensive search.
- Export.
- Import.
- Heavy reports.
- Bulk operations.

الحدود تعتمد على تكلفة endpoint، ولا تستخدم قيمة واحدة عشوائية لكل النظام.

---

# 27. Performance Budgets

لا تقل "الأداء جيد" بدون قياس.

نقيس عند الحاجة:

- API latency.
- p95/p99.
- DB query time.
- Query count.
- Response size.
- CPU.
- RAM.
- DB connections.
- Requests per screen.
- Duplicate requests.
- Batch memory usage.

الأهداف الرقمية تحدد بعد قياس البيئة الفعلية، لا بالتخمين.

---

# 28. Observability

يجب أن نستطيع معرفة:

```text
أي Endpoint بطيء؟
أي Query بطيئة؟
كم Queries نفذ الطلب؟
كم حجم Response؟
كم CPU/RAM؟
كم Active DB Connections؟
كم 4xx/5xx؟
```

كل Request مهم يملك trackingId مناسبًا.

لا نسجل بيانات حساسة لإكمال Observability.

---

# 29. API Conventions

يفضل نمط موحد:

```text
GET    /api/v1/members
GET    /api/v1/members/{id}
POST   /api/v1/members
PATCH  /api/v1/members/{id}
```

للـ Workflow actions:

```text
POST /api/v1/pre-authorizations/{id}/submit
POST /api/v1/pre-authorizations/{id}/start-review
POST /api/v1/pre-authorizations/{id}/request-information
POST /api/v1/pre-authorizations/{id}/approve
POST /api/v1/pre-authorizations/{id}/reject
```

ممنوع إنشاء Endpoints موازية لنفس العملية مثل:

```text
/updateStatus
/change-state
/approveRequest
```

إذا كان هناك Canonical Endpoint موجود.

---

# 30. Database Schema وMigrations

أي تغيير Schema يجب أن يمر عبر Migration معتمد.

ممنوع:

- تعديلات يدوية غير موثقة في Production.
- حذف بيانات دون خطة.
- تغيير constraint حساس بلا اختبار.
- تغيير semantics عمود قائم بصمت.

أي Migration يجب أن يكون:

- قابلًا للتفسير.
- آمنًا قدر الإمكان.
- مختبرًا.
- متوافقًا مع rollout إذا كان ذلك مطلوبًا.

---

# 31. التواريخ والأموال

## Time

استخدم سياسة واحدة:

- تحديد timezone بوضوح.
- عدم استخدام current date ضمن قواعد تاريخية إذا كان `serviceDate` هو الصحيح.
- توثيق half-open intervals عند استخدامها.

## Money

- لا تستخدم floating point للأموال.
- استخدم decimal / Money abstraction المعتمد.
- rounding policy موحدة.
- Currency واضحة.
- الحساب المالي في مكان واحد معتمد.

---

# 32. التكاملات الخارجية

يجب وضع حدود واضحة للتكاملات.

مثال:

```text
Application
→ External Gateway / Port
→ Provider Adapter
```

لا تسرّب SDK خارجي أو API خاص بمزود إلى كامل الـDomain.

Interface هنا مسموح لأنه حد خارجي حقيقي.

---

# 33. التوثيق

لا تكتب Comments تشرح ما هو واضح من الكود.

اكتب التوثيق لشرح:

- لماذا اتخذ القرار؟
- ما القاعدة غير البديهية؟
- ما سبب وجود استثناء؟
- ما حدود التوافق؟
- ما سبب اختيار strategy؟

مثال جيد:

```java
// Employer is resolved at serviceDate, not current assignment,
// because historical claims must preserve the policy context
// that applied when the service occurred.
```

---

# 34. ADR للقرارات المعمارية

القرارات المهمة توثق في:

```text
docs/architecture/decisions/
```

مثال:

```text
ADR-001-authentication-model.md
ADR-002-member-context-resolution.md
ADR-003-permission-model.md
ADR-004-preauthorization-state-machine.md
ADR-005-sensitive-search-logging.md
```

كل ADR يحتوي:

```text
Context
Decision
Alternatives
Consequences
Migration
```

---

# 35. ما يعتبر قرارًا معماريًا يحتاج موافقة

يجب التوقف وطلب موافقة قبل:

- Dependency جديدة جوهرية.
- تغيير Authentication model.
- تغيير Authorization model.
- إضافة Redis.
- إضافة Queue/Event Bus.
- إضافة WebSocket.
- Microservice extraction.
- تغيير API contract عام.
- تغيير Database schema جذري.
- إضافة Workflow state جديدة.
- إضافة Role جديد.
- تغيير Permission semantics.
- إضافة fallback.
- الإبقاء على legacy path جديد.
- إنشاء abstraction layer كبير.
- تغيير مصدر الحقيقة.

---

# 36. ما يمكن تنفيذه دون موافقة خاصة

يمكن تنفيذ ما يلي إذا كان متسقًا مع هذا الدستور:

- Bug fix محلي.
- Refactor صغير.
- إعادة استخدام implementation قائم.
- إزالة duplication مثبت.
- تحسين أسماء.
- إضافة tests.
- إضافة validation.
- تحسين query مثبت بالقياس.
- حذف dead code بعد إثبات عدم استخدامه.
- تحسين error mapping ضمن السياسة المعتمدة.

---

# 37. قاعدة Boy Scout

عند تعديل منطقة من الكود:

> اتركها أفضل قليلًا مما وجدتها، لكن لا تعيد تصميم أجزاء غير مرتبطة بالمهمة.

ممنوع تحويل كل Task إلى "إعادة بناء المشروع".

---

# 38. Workflow إلزامي لكل مهمة

قبل التنفيذ، اكتب ملخصًا داخليًا/في خطة العمل:

```text
1. Feature owner/module:
2. Canonical implementation:
3. Source of truth:
4. Existing reusable code:
5. Existing duplication/legacy:
6. Minimum change:
7. Security implications:
8. Performance implications:
9. Tests to add/update:
10. Code to remove after migration:
```

ثم:

```text
Analyze
→ Plan
→ Test cases
→ Implement
→ Refactor
→ Remove obsolete code
→ Run relevant tests
→ Run regression
→ Report evidence
```

---

# 39. Definition of Done

أي مهمة لا تعتبر مكتملة حتى تحقق المناسب منها:

```text
[ ] المتطلب صحيح وظيفيًا
[ ] لا يوجد مسار موازٍ جديد
[ ] تم استخدام الـ Canonical Path
[ ] لا يوجد Business Logic مكرر
[ ] Source of Truth واضح
[ ] Backend Authorization مطبق
[ ] Scope validation مطبق
[ ] UI يخفي العناصر غير المسموحة
[ ] الرسائل للمستخدم غير تقنية وآمنة
[ ] لا تسريب بيانات حساسة في logs
[ ] Happy path مغطى بالاختبار
[ ] Failure paths المهمة مغطاة
[ ] Permission tests موجودة عند الحاجة
[ ] Regression test موجود لأي Bug fix
[ ] Repository query الحساسة مغطاة Integration
[ ] لا N+1 جديد
[ ] لا Request مكرر أو غير مبرر
[ ] Pagination/limits مطبقة عند الحاجة
[ ] Transactions قصيرة
[ ] لا Dead code بعد migration
[ ] لا TODO إنتاجي بدل الحل
[ ] لا Test معطل لتجاوز المشكلة
[ ] Build ينجح
[ ] Relevant tests تنجح
[ ] Full regression ينجح قبل الدمج عند التغيير الحساس
[ ] التوثيق/ADR محدث عند وجود قرار معماري
```

---

# 40. Production Gate

أي تغيير أمني/مالي/صلاحيات/Workflow لا يذهب للإنتاج قبل:

```text
[ ] Code review
[ ] Unit tests
[ ] Integration tests
[ ] Authorization tests
[ ] Tenant/scope isolation tests
[ ] Critical E2E workflow
[ ] Performance sanity where relevant
[ ] Security regression
[ ] Migration verification
[ ] Build green
[ ] Deployment plan
[ ] Post-deploy smoke test
```

---

# 41. قواعد نهائية للوكيل

عند الشك، اتبع هذا الترتيب:

```text
Reuse existing canonical code
before creating new code.

Refactor existing implementation
before creating a parallel implementation.

Delete obsolete code
after migration and verification.

Prefer explicitness
over cleverness.

Prefer measured performance work
over speculative optimization.

Prefer backend enforcement
over frontend assumptions.

Prefer absence of unauthorized UI
over showing disabled/forbidden actions.

Prefer safe user messages
over technical details.

Prefer one source of truth
over synchronized duplicates.

Prefer one implementation
over compatibility forever.
```

---

# 42. إذا تعارض المطلوب مع هذا الدستور

إذا طلبت المهمة شيئًا يؤدي إلى:

- تكرار.
- مسار موازٍ.
- تغيير معماري.
- كسر أمان.
- تخزين حساس.
- تناقض Source of Truth.
- Overengineering.
- Debt جديد غير مبرر.

فلا تنفذه بصمت.

يجب:

```text
1. إيقاف التنفيذ في نقطة القرار.
2. شرح التعارض باختصار.
3. اقتراح أقل بديل يحقق المطلوب ويحافظ على الدستور.
4. انتظار الموافقة إذا كان القرار معماريًا.
```

---

# 43. المبدأ الختامي

> **الكود الجيد ليس الكود الأكثر تعقيدًا أو الأكثر "احترافية" شكليًا؛ بل الكود الذي له طريقة واحدة واضحة، مسؤوليات محددة، أمان فعلي، اختبارات تثبت السلوك، أقل قدر من العمل غير الضروري، ويمكن لمطور آخر فهمه وتعديله دون خوف.**
