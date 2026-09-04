# P1.1 — خط الأساس قبل توحيد قرار السقف والقرار المالي

**الحالة:** VERIFIED
**التاريخ:** 2026-09-04
**HEAD المرجعي:** `b0f26898` (`origin/main`) + `7352dfb0` (ADR-008، P0)

هذا المستند يُثبّت حالة كل مكوّن مذكور في خطة إغلاق P1، بدليل مباشر من الكود
الحالي، قبل كتابة أي `UnifiedLimitResolver`/`UnifiedLimitDecision`. لا كود
إنتاج تغيّر بهذا الالتزام.

## 1. مسارا الكتابة الحاليان على مستوى المطالبة

| المكوّن | الدور اليوم | الفجوة المؤكَّدة |
|---|---|---|
| `CoverageEngineService` (نظام A) | يحسب `times`/`days`/`amount` عبر `CoverageDecisionService` → `BenefitBucketLimitService` → `BucketChainWalker` → `DivisibleLimitSplitter` (P2) | لا يرى `RESERVED` لأوعية التصنيف، ولا يتحقق من ملكية الوعاء للوثيقة |
| `ClaimFinancialAdjudicationService` (نظام B) | يعيد الحساب من `line.getRequestedTotal()`/`contractUnitPrice()` عبر `EffectiveLimitResolver` → `ApplicableLimitResolver` → `WaadFinancialEngine`، ويكتب فوق نتيجة A | لا يحمل بُعد `times`/`days` إطلاقاً — `ApplicableLimitDefinition` لا يملك هذين الحقلين في نموذج بياناته أصلاً |
| `WaadFinancialEngine` | محرك مالي صرف، مجمَّد (`WAAD-FIN-1.0`)، مختبر بدقة | لا فجوة — تصميمه يفترض عمداً أن الكمية "ليست مسؤوليته" (javadoc صريح) |
| `LimitBalanceReader` | يقرأ `committed + reserved` عبر `EffectiveLimitResolver` فقط | المصدر الأنظف لقراءة الرصيد اليوم — لا يُستخدم من نظام A إطلاقاً |
| `BucketChainWalker` | مشي السلسلة الأبوية، موحَّد فعلياً منذ P1 السابق (نظام السياقات) | لا فجوة — مستخدَم من كلا النظامين A وB |
| `ClaimLineLimitSnapshot` (لقطة A) | يُخزّن `benefitScopeType`, `sourceType`, `consumptionOrder`, إلخ | **صفر أعمدة times/days/quantity** — مؤكَّد بالفحص المباشر لملف الكيان |
| `PreauthLineLimitSnapshot` (لقطة الموافقة المسبقة) | يحمل `timesLimit`, `committedTimesBefore`, إلخ | يحمل بُعد المرات الذي تفتقده لقطة المطالبة — عدم تناظر بين الجدولين |
| مسار الموافقة المسبقة | `PreAuthorizationDecisionBuilder` يستدعي `CoverageDecisionService` للحصول على `appliedRuleId` ثم يتجاهل `coveragePercent` الناتج، ويعتمد `line.getCoveragePercentage()` (**دائماً `null`** — لا يُكتب في أي مكان بالكود) فيسقط لنسبة الوثيقة الافتراضية | نسبة تغطية الموافقة المسبقة والمطالبة تُحسبان بمصدرين مختلفين فعلياً |

## 2. اكتشاف جديد: `approvedQuantity` يُصحَّح خطأً بصمت عند الحفظ

`ClaimLine` يحمل عمودين فعليين في قاعدة البيانات: `requested_quantity` و
`approved_quantity` (`ClaimLine.java:402-406`).

- مسار المطالبة **لا يكتب `requestedQuantity` إطلاقاً** (لا `ClaimMapper` ولا أي
  مسار آخر).
- كلا نظامَي A وB يتركان `approvedQuantity = null` **صراحة**:
  - `ClaimMapper.java:509` → `.approvedQuantity(null)`
  - `ClaimFinancialAdjudicationService.java:145` → `line.setApprovedQuantity(null); // not derivable from a monetary result`
- ثم يتدخل هوك دورة حياة الكيان نفسه:

  ```java
  // ClaimLine.java:425-439 — initializeFinancialAuditFields()، يُستدعى من @PrePersist و@PreUpdate
  if (Boolean.TRUE.equals(rejected)) {
      approvedQuantity = 0;
  } else {
      if (approvedQuantity == null)
          approvedQuantity = quantity;   // ← الكمية الكاملة المطلوبة، لا المعتمدة الفعلية
  }
  ```

**الأثر:** حتى لو أصلح P1 المال بالكامل (Company/Copay/Non-Covered صحيحة)، فإن
`approvedQuantity` المحفوظ على سطر فيه قبول جزئي (مثال: 2 من 3) سيصبح **3**
لا 2 — خطأً صامتاً لا علاقة له بأي محرك مالي، بل بهوك على مستوى الكيان.

**القاعدة المسجَّلة لـP1.3/P1.6 (بناءً على توجيه صاحب المشروع):**

```text
RULE: approvedQuantity يأتي فقط من UnifiedLimitDecision،
      بعد وجود قرار سقف فعلي — لا مصدر آخر.
```

**قرار صريح: لا يُعطَّل الهوك الآن.** يبقى كما هو حتى يوجد الكاتب البديل
(`UnifiedLimitResolver` يكتب `approvedQuantity` الصحيح قبل أن يصل السطر لدورة
الحفظ) — فقط عندها يُقرَّر إما حذف الـfallback كلياً أو تقييده بحالات Legacy
غير مالية بوضوح.

## 3. أعمدة غير موجودة بعد (لا Migration الآن)

`refusedQuantity`, `requestedDays`, `approvedDays`, `refusedDays` **غير
موجودة كأعمدة إطلاقاً** على `ClaimLine` اليوم (تحقق مباشر بالبحث في الكيان).

**قرار صريح: لا تُنشأ أي Migration الآن.** ينتظر إثبات عقد
`UnifiedLimitDecision` نهائياً في P1.3 — المخطط يتبع العقد، لا العكس.

## 4. الاختبارات الذهبية (P1.2)

ملف `UnifiedLimitDecisionGoldenTest.java` — ستة اختبارات صيغة صرفة (G1–G6)
تُثبّت الأرقام المستهدفة قبل كتابة أي Resolver. تفاصيلها في نفس الالتزام.

## 5. بوابة Physio المُحدَّثة (بعد اكتشاف الهوك)

معيار إغلاق P1 لا يُعتبر مكتملاً حتى لو كانت كل المبالغ صحيحة، إن كانت
`approvedQuantity` خاطئة:

```text
Preview:        approvedQuantity = 2
Saved ClaimLine: requestedQuantity = 3, approvedQuantity = 2
Snapshot:        requestedQuantity = 3, approvedQuantity = 2, refusedQuantity = 1
Ledger:          timesConsumed = 2
```

إذا كان المال (150/50/100) صحيحاً لكن `approvedQuantity` المحفوظة أصبحت 3
بسبب الهوك، فإن **P1 = OPEN**، لا `VERIFIED`.

## الخلاصة

```text
P1.1: VERIFIED
P1.2: VERIFIED
Working tree: clean بعد هذا الالتزام
Production behavior: unchanged
P1.3: OPEN — عقد UnifiedLimitDecision (الخطوة التالية)
```
