# P1.4 — تصميم `UnifiedLimitResolver`: النطاق، ثم المدخل، ثم الهيكل

**الحالة:** P1.4.0 VERIFIED · P1.4.1 VERIFIED · P1.4.2 VERIFIED · P1.4.3 VERIFIED — هيكل معزول، صفر ربط حي.
**يعتمد على:** `P1_UNIFIED_LIMIT_BASELINE.md` (P1.1)، `P1_UNIFIED_LIMIT_DECISION_CONTRACT.md` (P1.3، **لم يتغيّر** — هذا المستند يُفصّل شكل الاستدعاء، لا يعدّل معنى الناتج).

---

## P1.4.0 — Scope Amendment (بعد الجرد الحي)

```text
Canonical limit-decision scope يشمل الآن أربعة مصادر، لا ثلاثة:
1. BenefitBucketLimitService        (times/days/amount + DivisibleLimitSplitter)
2. ApplicableLimitResolver          (مشي السلسلة + BUCKET_POLICY_MISMATCH)
3. EffectiveLimitResolver           (اختيار المصدر بالأولوية + فحص ملكية الوثيقة)
4. ApplicableCountingLimitResolver  (عدّ المرات في مسار الموافقة المسبقة — مكتشف اليوم)

Deferred integration (لا يبدأ الآن):
- استبدال استدعاء PreAuthorizationDecisionBuilder فعلياً بالـResolver الموحَّد → P1.12

Tracked ledger divergence (دين تقني مُسجَّل، ليس ضمن P1.4):
- BenefitBucketLedgerService.addWithParents (نسخة خامسة من مشي السلسلة،
  مستقلة عن BucketChainWalker) → يجب توحيدها مع BucketChainWalker في P1.11
  (Snapshot = Ledger)، لا قبل ذلك.
```

**الفصل الملزم:** P1.4 يبني **القدرة** (الـResolver يفهم كل الأنواع الأربعة
ويُنتج نفس قرار المبلغ والمرات الذي يحتاجه كل من المطالبة والموافقة المسبقة).
P1.12 يُبدّل **المستدعي الفعلي** للموافقة المسبقة. الاثنان منفصلان عمداً — بناء
القدرة ناقصة (بتجاهل احتياج الموافقة المسبقة) يعني تعديل عقد P1.3 لاحقاً،
وهو ما نمنعه بهذا الفصل.

---

## P1.4.1 — عقد المدخل: `UnifiedLimitInput`

### التمييز الذي يمنع اختراع Boolean غامض

اكتشاف الجرد الحي: مطالبة عادية، ومطالبة محوَّلة من موافقة مسبقة، تقرآن
الرصيد بدالتين مختلفتين تماماً في الكود اليوم (`LimitBalanceReader.read` مقابل
`readForPreauthorizedClaim`) — ليس فرقاً في قيمة معامل، بل في **الصيغة
الحسابية نفسها**:

```text
NORMAL:               reservableAvailable = actualRemaining - reserved
PREAUTHORIZED_CLAIM:   availableForThisClaim = min(actualRemaining, reservableAvailable + ownActiveReservation)
```

(المصدر: `LimitBalanceReader.java:408-409` — الحجز الخاص بنفس الموافقة يُعاد
إضافته ثم يُحدّ بالرصيد الفعلي، لا يُترك بلا سقف.)

لموافقة مسبقة نفسها (لا مطالبة بعد)، لا يوجد "حجز خاص" لأنها هي نفسها من
سيُنشئ الحجز — فهذا وضع ثالث منفصل، لا يمكن دمجه مع الاثنين أعلاه.

```java
public enum ReservationEvaluationMode {
    /** مطالبة عادية، لا صلة لها بأي موافقة مسبقة سارية. */
    NORMAL,
    /** مطالبة تُحوَّل من موافقة مسبقة — حجزها الخاص لا يُحسب ضدها. */
    PREAUTHORIZED_CLAIM,
    /** إنشاء/تقييم موافقة مسبقة نفسها، قبل أي مطالبة. */
    PREAUTH_RESERVATION
}
```

**لماذا `enum` لا `boolean fromPreAuth`:** لأن الوضعين "مطالبة محوَّلة" و"إنشاء
موافقة مسبقة" يحتاجان بيانات مختلفة (الأول يحتاج `preAuthorizationId` +
`memberPolicyAssignmentId` موجودَين مسبقاً؛ الثاني لا يحتاج أياً منهما لأنه هو
من سينشئهما). `boolean` واحد لا يستطيع حمل هذا الفرق دون حقل ثالث ضمني يُفسَّر
حسب السياق — وهو بالضبط الغموض الممنوع في القاعدة 5 من P1.3.

### الحقل الكامل

**تحديث بعد P1.4.2 (تنفيذ فعلي كشف حقلاً ناقصاً):** `countingMethod` لم يُذكر
هنا أصلاً، واتضح أنه **جزء من عقد الإدخال الفعلي**، لا تفصيل تنفيذي داخلي —
دونه يستحيل التمييز بين G2 (قابل للتجزئة) وG3/G4 (ذرّي أو محكوم بوحدات
صحيحة). القيم الفعلية الموجودة في المشروع اليوم هي `CountingMethod`
(`EACH_UNIT, EACH_LINE, PER_VISIT, PER_DAY` — `DivisibleLimitSplitter`، P2):
فقط `EACH_UNIT` قابل للتجزئة؛ الباقي ذرّي بالكامل مثل الأيام دائماً.

```java
public record UnifiedLimitInput(
    Long policyId,
    Long ruleId,
    Long memberId,
    LocalDate serviceDate,
    EncounterType encounterType,

    int requestedQuantity,
    int requestedDays,
    CountingMethod countingMethod,   // يحدد كيف يتحول قيد TIMES إلى قرار وحدات: EACH_UNIT قابل للتجزئة، الباقي ذرّي
    BigDecimal effectiveUnitPrice,   // لتحويل quantity المقبولة إلى bindingAvailableAmount (DivisibleLimitSplitter)
    BigDecimal eligibleAmount,       // effectiveUnitPrice × requestedQuantity، أو المبلغ المؤهَّل المباشر إن لم يكن العدّ بالوحدة

    Long excludeClaimId,             // null لغير المطالبات (PREAUTH_RESERVATION)

    ReservationEvaluationMode reservationMode,
    Long preAuthorizationId,             // مطلوب فقط عند PREAUTHORIZED_CLAIM
    Long memberPolicyAssignmentId        // مطلوب فقط عند PREAUTHORIZED_CLAIM
) {
    public UnifiedLimitInput {
        if (reservationMode == ReservationEvaluationMode.PREAUTHORIZED_CLAIM) {
            if (preAuthorizationId == null || memberPolicyAssignmentId == null) {
                throw new IllegalArgumentException(
                    "PREAUTHORIZED_CLAIM requires preAuthorizationId and memberPolicyAssignmentId");
            }
        }
    }
}
```

هذا **أقل شكل يخدم الحالتين الحيّتين** (المطالبة والموافقة المسبقة) دون
اختراع إطار جديد — كل حقل مطابق لمعامل فعلي موجود اليوم في أحد نقاط الاستدعاء
الثلاث المجرودة (P1.4.0)، لا حقل واحد مُتخيَّل.

### Invariant حسابي إلزامي — متى يوجد "وحدة" أصلاً (اكتشاف P1.4.2)

تنفيذ G1 فعلياً كشف أن معادلة واحدة لا تكفي لكل من (مبلغ بلا أي قيد مرات) و
(مبلغ محكوم بسعر وحدة مع قيد مرات) — الفرق **جوهري**، لا حالة حدّية لنفس
المعادلة:

```text
IF times.configured == null:
    AMOUNT limit هو قيد نقدي مستمر (continuous)، لا قيد وحدات.
    approvedQuantity لا تتغيّر بسبب سقف المبلغ وحده — العدّ نفسه غير مقيَّد
    هنا أصلاً، والرفض كله يقع على bindingAvailableAmount مباشرة.
    bindingAvailableAmount = min(eligibleAmount, amount.remaining)

IF times.configured != null:
    الكمية تُحسم أولاً وفق countingMethod (EACH_UNIT قابل للتجزئة بوحدات
    صحيحة فقط؛ غيره ذرّي)، مقارناً بين ما يسمح به سقف المرات وما يسمح به
    سقف المبلغ محوَّلاً لوحدات صحيحة (floor(amount.remaining / effectiveUnitPrice)).
    ثم bindingAvailableAmount يُشتق من approvedQuantity (DivisibleLimitSplitter).
```

**لماذا يُسجَّل كـ Invariant لا كتفصيل تنفيذي عابر:** أي Refactor مستقبلي قد
يُبسّط الكود بتطبيق `floor(amount/unitPrice)` بشكل عام على كل قرار مبلغ —
وهذا يكسر G1 تحديداً (مطالبة بلا مفهوم "وحدة" إطلاقاً، حيث المبلغ الجزئي
600 من 1000 مقبول تماماً، لا يُقرَّب لأسفل لأقرب وحدة وهمية). هذا الشرط يجب
أن يبقى صريحاً في أي تنفيذ لاحق لنفس المنطق.

---

## G7 — الاختبار الذهبي السابع: مطالبة تملك حجزها الخاص

مبني حرفياً على صيغة `readForPreauthorizedClaim` الفعلية (لا تصور نظري):

```text
Limit = 20
Committed = 10
Reserved (الإجمالي عبر كل الموافقات السارية) = 6
   منها حجز هذه الموافقة بالذات (own) = 4

Normal reservableAvailable  = 20 - 10 - 6 = 4          (لو عومِلت كمطالبة عادية — خطأ)
PREAUTHORIZED_CLAIM available = min(actualRemaining, reservableAvailable + own)
                             = min(20-10, 4+4)
                             = min(10, 8)
                             = 8                        (الصحيح)
```

**لماذا `min(actualRemaining, ...)` لا مجرد الجمع:** لو حُجزت 4 فقط بينما
`committed` ارتفع لاحقاً حتى اقترب من `actualRemaining`، فإن `reservableAvailable + own`
قد يتجاوز `actualRemaining` فعلياً — والحد الأعلى يمنع هذه المطالبة من أن
"تستعيد" أكثر مما تبقّى واقعاً على الوثيقة، حتى لو كانت هي مالكة الحجز.

### قاعدة نطاق `ownActiveReservation` — لا تعني "أي حجز لنفس المستفيد"

مبنية حرفياً على معاملات `consumptionRepository.sumOwnActiveReservation`
الفعلية (`LimitBalanceReader.java:402-404`):

```text
ownActiveReservation = مجموع الحجوزات التي تحقق الأربعة شروط معاً:
  1. نفس preAuthorizationId  (هذه الموافقة المسبقة بالذات، لا أي موافقة أخرى لنفس العضو)
  2. نفس memberPolicyAssignmentId (نفس تخصيص العضو للوثيقة وقت الحجز)
  3. نفس bucketId (نفس الوعاء تحديداً، لا سقف آخر على نفس الوثيقة)
  4. نفس نافذة period (periodStart/periodEnd) لهذا السقف
  وحالتها "نشطة" فقط — لا الملغاة، ولا المعكوسة، ولا المنتهية،
  ولا التي حُوِّلت بالفعل إلى استهلاك (Status.COMMITTED)
```

**لماذا هذا التحديد الصارم:** توسيع `ownActiveReservation` ليشمل "أي حجز لنفس
المستفيد" يحوّل `PREAUTHORIZED_CLAIM` من استثناء ضيق (حرّر حجزك أنت فقط) إلى
استثناء عام (حرّر كل حجوزاتك)، فتستطيع مطالبة واحدة تجاوز رصيد موافقات مسبقة
أخرى سارية لنفس العضو لا علاقة لها بها. الشروط الأربعة معاً هي ما يمنع ذلك.

### المعنى الحسابي الصريح لكل Mode (الصيغة، لا طريقة الكتابة النهائية)

```text
NORMAL:
    available = Limit - Committed - ActiveReserved

PREAUTHORIZED_CLAIM:
    available = min(
        Limit - Committed,
        Limit - Committed - ActiveReserved + OwnActiveReservation
    )

PREAUTH_RESERVATION:
    available = Limit - Committed - ActiveReserved
```

`NORMAL` و`PREAUTH_RESERVATION` متطابقتان حسابياً اليوم (كلتاهما "لا حجز خاص
يُستثنى")، لكنهما تبقيان قيمتين منفصلتين في `enum` — ليس تكراراً، بل لأن معنى
"لماذا لا يوجد استثناء" مختلف: الأولى لأن المطالبة لا صلة لها بأي موافقة
أصلاً، والثانية لأن الموافقة نفسها لم تُنشئ حجزها بعد. لو تغيّر مستقبلاً حساب
أحدهما (مثال: قاعدة عمل جديدة تخص تقييم الموافقات المسبقة فقط)، فالفصل هنا هو
ما يجعل ذلك ممكناً دون المساس بالمطالبات العادية.

سيُضاف G7 كاختبار صيغة صرفة إلى نفس ملف `UnifiedLimitDecisionGoldenTest.java`
(G1-G6 موجودة، لا تُحذف) في P1.4.3.

---

## P1.4.2/P1.4.3 — النتائج التنفيذية (بعد الكتابة الفعلية)

حزمة معزولة `benefitpolicy.service.unifiedlimit` — صفر تبعية لـSpring أو
لأي مستدعٍ حي. الملفات: `ReservationEvaluationMode`, `UnifiedLimitStatus`,
`BindingConstraintType`, `UnifiedLimitInput`, `BucketLimitSnapshot`,
`UnifiedLimitDecision`, `UnifiedLimitResolver`، واختبار
`UnifiedLimitResolverTest` (8 حالات: G1-G7 + "بلا سقف إطلاقاً").

كل حالة تُثبِّت `status` و`bindingConstraintType` صراحة (لا شرطة `—`)، حتى
يُثبِت الاختبار *لماذا* اتُّخذ القرار، لا الرقم النهائي فقط:

| الاختبار | `status` | `bindingConstraintType` | `approvedQuantity`/`approvedDays` |
|---|---|---|---|
| G1 | `PARTIAL` | `AMOUNT` | الكمية كاملة (1) — الرفض كله في المال (600 من 1000) |
| G2 (Physio) | `PARTIAL` | `TIMES` | 2 من 3 |
| G3 | `EXHAUSTED` | `DAYS` | 0 من 1 يوم |
| G4 | `PARTIAL` | `AMOUNT` | 2 من 5 (لا 2.5 — القيد الأضيق بالوحدات الصحيحة) |
| G5 | `PARTIAL` | `TIMES` | 6 من 8 |
| G6 | `BLOCKED` | `NONE` | لا شيء محسوب — `bindingAvailableAmount=null` (لا صفر) |
| G7 | `LIMITED` | `NONE` | 8 من 8 (الحجز الخاص أُعيد بالكامل، محدوداً بالرصيد الفعلي) |
| بلا سقف | `UNLIMITED` | `NONE` | الطلب كاملاً |

**تحقق الانحدار:** حزمة `benefitpolicy`+`claim` كاملة، 549 اختباراً، فشل 2
— كلاهما مُثبت مسبقاً في P1.1 وغير مرتبط بهذا العمل:
`DirectClaimEntryRollbackIntegrationTest.aFailureAfterTheVisitInsertLeavesNeitherHalfOfTheCommand`
و`DirectClaimEntryRollbackIntegrationTest.concurrentRetriesCreateOneVisitAndOneClaimAndReturnTheSameClaim`
(يعتمدان على `DirectClaimEntryService.java`، ملف لم يُلمس، والاختبار يُموّه
طبقة `claimService` بالكامل فلا يمكن لهذا العمل أن يكون سببه). أي فشل ثالث
غير هذين في تشغيل مستقبلي يُعتبر انحداراً حقيقياً.

---

---

## P1.4.4 — Implementation Review Gate

مراجعة الكود الفعلي (لا التوثيق) مقابل عشرة بنود. النتيجة: **Go مشروط**، لا
Go كاملة بلا تحفظ — نقطتان حقيقيتان مسجَّلتان بدل إخفائهما.

| البند | الحكم | الدليل |
|---|---|---|
| 1. Contract fidelity | ✅ مع تصحيح توثيقي | `UnifiedLimitDecision` لا يحمل company/copay/nonCovered (تحقق مباشر). **تناقض وُجد**: P1.3 §2 وصف `BLOCKED` بأنه "يُلقى كاستثناء"؛ التنفيذ يُعيده كقيمة عادية ضمن نفس النوع — وهذا **الأصح** (متّسق مع "خمس حالات ضمن enum واحد" في نفس القسم، ومع نقاء الدالة في البند 5). التصحيح توثيقي فقط، لا كودي. |
| 2. Financial invariants | ✅ | `bindingAvailableAmount` يُشتق بعد حسم `approvedQuantity`/`approvedDays` دائماً — تحقق من ترتيب الأسطر الفعلي في `UnifiedLimitResolver.resolve`. |
| 3. Reservation semantics | ⚠️ **فجوة مسجَّلة، مؤجَّلة عمداً** | G7 يُثبت الحساب الرياضي فقط (`min(actualRemaining, reservableAvailable+own)`). `BucketLimitSnapshot.amountOwnActiveReservation`/`timesOwnActiveReservation` رقم يُمرَّر جاهزاً من الخارج — الـResolver **لا يتحقق ولا يستطيع أن يتحقق بمعزل** من الشروط الأربعة (نفس `preAuthorizationId`/`memberPolicyAssignmentId`/`bucketId`/الفترة، القسم أعلاه). هذا إثبات لا يمكن إغلاقه إلا عند بناء الطبقة الفعلية التي تستدعي `consumptionRepository.sumOwnActiveReservation` — **ينتظر P1.5+**، لا يُدّعى إغلاقه هنا. |
| 4. Divisibility semantics | ✅ (كانت فجوة، أُغلقت الآن) | كان `!divisible` (`EACH_LINE/PER_VISIT/PER_DAY` **مع** بُعد مرات موجود فعلياً) مكتوباً بلا اختبار. أُضيف **G8** يثبته: `PER_VISIT`، طلب 3 ومتبقٍ 2 → رفض كامل (`EXHAUSTED`, `approvedQuantity=0`)، لا قبول جزئي. |
| 5. Dependency purity | ✅ | لا `@Service`/`@Component`/`@Transactional`/Repository في `UnifiedLimitResolver.java` — تحقق مباشر بقراءة الملف كاملاً. دالة `static` صرفة. |
| 6. Error semantics | ✅ | `BLOCKED` قيمة إرجاع؛ `bindingAvailableAmount` يبقى `null` عند الحظر، لا `0` ولا `UNLIMITED` بديل — تحقق من `UnifiedLimitDecision.blocked()`. |
| 7. Performance characteristics | ✅ ضمن حدود المرحلة | O(n) على عدد الأوعية لكل محور، بلا استعلام. يُقيَّم كاملاً فقط بعد P1.5 (عدد استعلامات بناء `BucketLimitSnapshot`). |
| 8. Persistence readiness | جزئي، متوقَّع | الأنواع قابلة للتخزين المباشر؛ لم يُختبر لأن لا Persistence في هذا الـSkeleton — طبيعي لهذه المرحلة. |
| 9. Legacy overlap analysis | ✅ لا تداخل | `DivisibleLimitSplitter` مُستخدَم كما هو. `BucketChainWalker` **لم يُستورد بعد عمداً** — مسؤولية طبقة بناء `BucketLimitSnapshot` في P1.5، لا هذا الـResolver. |
| 10. Go/No-Go | **Go مشروط** | فجوة #4 أُغلقت الآن (G8). فجوة #3 تبقى **مفتوحة بالاسم**، مُرحَّلة لـP1.5 — ليست عيباً في المنطق، بل إثباتاً لا يمكن إنجازه بمعزل عن قاعدة بيانات حقيقية. |

**نتيجة سبعة أسئلة المخاطر المحددة مسبقاً:**

1. `remaining` يُحسب داخل الـResolver دائماً (`reduceAxis`)؛ `BucketLimitSnapshot` لا يحمله جاهزاً إطلاقاً — مصدر حقيقة واحد، لا ازدواجية.
2. `bindingAvailableAmount` مشتق بعد قرار الكمية/الأيام — مؤكَّد (البند 2 أعلاه).
3. G4 يُثبت صراحة `approvedQuantity=2` و`bindingAvailableAmount≠250.00` و`bindingConstraintType=AMOUNT`.
4. G6: `bindingAvailableAmount=null` (ليس صفراً)، `bindingConstraintType=NONE`، لا مسار بديل — مؤكَّد بالاختبار.
5. G7 يُثبت الحساب الرياضي فقط؛ **لا يُثبت** تطبيق الشروط الأربعة الفعلية على "own" — فجوة #3 أعلاه.
6. `countingMethod` يحدد فرعاً كاملاً مختلفاً في القرار — مؤكَّد بـG2/G4 (قابل للتجزئة) مقابل G8 الجديد (ذرّي رغم وجود بُعد مرات).
7. حالة `UNLIMITED` صريحة عبر `anyAxisConfigured`، تنطبق بنفس الآلية سواء كانت القائمة فارغة أو غير فارغة بأوعية بلا قيود — لا استنتاج غامض.

**تحقق الانحدار بعد G8:** `UnifiedLimitResolverTest` — 9/9 ناجحة.

---

## الخطوات التالية

```text
P1.5.1  → ربط CoverageDecisionService بـ UnifiedLimitResolver فقط (لا B، لا PreAuth)
          → مقارنة Preview قبل/بعد على G1-G7 حرفياً
P1.5.2  → مصدر واحد لقراءة الرصيد (يُغذّي BucketLimitSnapshot فعلياً من DB،
           يُثبت فجوة #3 أعلاه بالتزامن مع بناء قراءة "own" الحقيقية)
P1.12   → استبدال EffectiveLimitResolver + ApplicableCountingLimitResolver في PreAuthorizationDecisionBuilder
```

**لا تلمس `ClaimFinancialAdjudicationService` ولا `PreAuthorizationDecisionBuilder` في P1.5.1.** الربط الأول محصور بـ`CoverageDecisionService` (الأقرب لنظام A الحالي، الأقل مخاطرة) — Preview فقط، لا Save.

لا تُنقل `PreAuthorizationDecisionBuilder` ولا `ClaimFinancialAdjudicationService`
ولا `CoverageDecisionService` للمسار الجديد بعد — هذا يبقى Skeleton معزول
قابل للحذف بسهولة لو ظهر عيب في التصميم قبل أي ربط حي.
