# P1.3 — عقد `UnifiedLimitDecision`: المعنى قبل الحقول

**الحالة:** للمراجعة (Design for Review) — لا كود Java بعد.
**يعتمد على:** `docs/finance/P1_UNIFIED_LIMIT_BASELINE.md` (P1.1)، `UnifiedLimitDecisionGoldenTest.java` (P1.2).

---

## 0. الفصل الذي يحمي من تكرار خطأ A/B

الخطأ الذي أدخلنا فيه أصلاً (نظام A يحسب times/days ثم نظام B يعيد حساب المال
من الصفر ويطغى عليه) لم يحدث لأن أحداً أراد ذلك — حدث لأن **لا حدّ واضحاً بين
"ما هو مسموح استهلاكه" و"من يدفع كم"**. فسهُل على كل طبقة أن تحسب الاثنين معاً
بمنطقها الخاص.

هذا العقد يمنع تكرار ذلك بفرض حد صريح لا يُتجاوز:

```text
UnifiedLimitDecision   — يجيب فقط: "كم وحدة/يوم/دينار مسموح استهلاكه، ولماذا؟"
        ↓ (bindingAvailableAmount فقط يعبر هذا الخط)
WaadFinancialEngine    — يجيب فقط: "من يدفع كم من هذا المسموح؟"
        ↓
ClaimLineDecision      — القرار المالي النهائي المدمج، جاهز للحفظ/العرض/الدفتر
```

**`UnifiedLimitDecision` لا يعرف نسبة التغطية. لا يعرف الخصم التعاقدي. لا يعرف
من يدفع.** و**`WaadFinancialEngine` لا يعرف كمية ولا أياماً ولا أي شيء عن
الأوعية** — يبقى كما هو تماماً (القسم 0 من ADR-008). `ClaimLineDecision` هو
الوحيد الذي يجمع مخرجات الاثنين لصورة واحدة قابلة للحفظ.

أي حقل يحتاج معرفة الاثنين معاً (كمية **و**مال) ينتمي إلى `ClaimLineDecision`،
لا إلى أيٍّ من الاثنين الآخرين. هذا هو الفحص الحاسم عند إضافة أي حقل جديد
مستقبلاً.

---

## 1. الأجزاء الأربعة

### 1.1 Input Contract — ما يدخل القرار

هذا ليس جزءاً من `UnifiedLimitDecision` نفسه (الذي هو ناتج/Output)، بل عقد
الاستدعاء الذي يُبنى منه:

```text
policyId          — أي وثيقة (لفحص ملكية الوعاء، القسم 5 قاعدة BUCKET_POLICY_MISMATCH)
benefitRuleId      — القاعدة المطبَّقة، مصدرها الوحيد CoverageDecisionService (لا يُعاد اشتقاقها هنا)
memberId
serviceDate        — يحدد الدورة/الفترة، لا تاريخ اليوم أبداً
encounterType
requestedQuantity  — من سطر المطالبة كما أُدخل، قبل أي قرار
requestedDays      — كذلك
excludeClaimId     — لإعادة حساب مطالبة قيد التعديل دون احتساب نفسها ضمن "المستهلك"
```

**لماذا منفصل عن الناتج:** لأن نفس المدخلات يجب أن تُنتج نفس القرار دائماً
(قاعدة الإغلاق §30 في مستندك: "نفس المدخلات، نفس الرصيد، تنتج قراراً واحداً").
فصل المدخل عن الناتج يجعل هذا قابلاً للاختبار المباشر: مدخل ثابت ⇒ ناتج ثابت.

### 1.2 Limit Evaluation Result — حالة كل نوع سقف على حدة، قبل أي ترجيح بينها

لكل نوع سقف (`amount`, `times`, `days`) نفس البنية الأربعية بالضبط — **هذا
تعمُّد، لا تكرار**: أي فحص أو اختبار يُكتب لنوع واحد يُطبَّق على البقية
حرفياً دون تمييز خاص:

```text
configured   — القيمة المُعدَّة على الوعاء (null فقط إذا لم يُعدّ سقف من هذا النوع إطلاقاً على أي وعاء في السلسلة)
committed    — المُستهلك فعلياً (مطالبات مُعتمَدة سابقاً، من نفس المصدر الذي يستخدمه LimitBalanceReader)
reserved     — المحجوز حالياً لموافقات مسبقة سارية (القاعدة 4، القسم 3 أدناه) — الحجوزات النشطة فقط، لا الملغاة/المنتهية/المُحوَّلة لاستهلاك فعلي
remaining    — configured - committed - reserved (النشط)، بالضبط، لكل نوع من الثلاثة على حدة — لا صيغة بديلة في أي مكان في النظام
```

**صيغة صريحة واحدة، لا استثناء لنوع دون آخر:**

```text
remaining[AMOUNT] = configured[AMOUNT] - committed[AMOUNT] - activeReserved[AMOUNT]
remaining[TIMES]  = configured[TIMES]  - committed[TIMES]  - activeReserved[TIMES]
remaining[DAYS]   = configured[DAYS]   - committed[DAYS]   - activeReserved[DAYS]
```

هذا يسدّ تحديداً الفجوة المؤكَّدة في P1.1: `BenefitBucketLimitService` اليوم
يطرح `reserved` فقط من السقف العام، ويتجاهله كلياً لسقوف `times`/`amount` على
مستوى وعاء التصنيف. في `UnifiedLimitDecision` لا يوجد نوع "معفى" من طرح
المحجوز.

**لماذا `configured` منفصل عن `remaining`:** لأن "لا يوجد سقف من هذا النوع"
(`configured=null`) يختلف جوهرياً عن "السقف موجود ومستنفَد" (`configured` رقم،
`remaining=0`) — وهذا بالضبط الالتباس الذي يمنعه بند "5 قواعد" أدناه.

### 1.3 Binding Decision — أيّ سقف هو الأكثر تقييداً، والقرار النهائي بالوحدات

هنا فقط تُقارَن الأنواع الثلاثة ببعضها، ويُختار الأكثر تقييداً (بالوحدات
الصحيحة، ليس بالمبلغ وحده — G4 يثبت أن سقف المبلغ قد يقيّد بوحدات أقل من سقف
المرات):

```text
approvedQuantity        — min(requestedQuantity, timesLimit.remaining, unitsAffordableByAmountLimit)
refusedQuantity         — requestedQuantity - approvedQuantity
approvedDays            — إما requestedDays كاملة أو صفر (لا تجزئة — G3)
refusedDays             — requestedDays - approvedDays
bindingConstraintType   — أيّ نوع كان السبب الفعلي للتقييد: AMOUNT | TIMES | DAYS | NONE
bindingBucketId         — الوعاء الذي فرض هذا التقييد تحديداً (للتدقيق، وللقاعدة 6 BUCKET_POLICY_MISMATCH)
bindingAvailableAmount  — القيمة النقدية المكافئة للـapprovedQuantity/approvedDays (عبر DivisibleLimitSplitter عند القسمة الجزئية) — هذا وحده يعبر إلى WaadFinancialEngine
status                  — أحد الحالات الخمس، القسم 2 أدناه
```

**الترتيب ملزم ولا يُعكَس:** `approvedQuantity`/`approvedDays` يُحسبان أولاً
بالوحدات الصحيحة (القسمة على السقف الأكثر تقييداً)، **ثم** يُشتق منهما
`bindingAvailableAmount` كترجمة نقدية لاحقة. `bindingAvailableAmount` ليس
مصدر الكمية ولا بديلاً عنها — هو نتيجة تُحسب *من* الكمية، لا العكس.
`approvedQuantity`/`refusedQuantity` يبقيان محفوظين في `UnifiedLimitDecision`
وفي كل ما يُشتق منه (`ClaimLine`, Snapshot) بصرف النظر عن أي حساب مالي لاحق
يجريه `WaadFinancialEngine` على `bindingAvailableAmount` — لا فقدان لهذه
الحقيقة عند أي خطوة تالية.

### 1.4 Audit/Snapshot Metadata — لماذا اتُّخذ هذا القرار تحديداً

```text
ruleId            — appliedRuleId من CoverageDecisionService (مصدر واحد، القاعدة 1 من مستند P0)
appliedBucketIds  — كل الأوعية التي شاركت في التقييم (لا الوعاء المقيِّد فقط) — لازم للقسم "gap audit" ولإعادة البناء لاحقاً
decisionReasons   — قائمة نصوص آلية القراءة (مثال: "TIMES_EXHAUSTED bucket=931 remaining=0"، "AMOUNT_BINDS bucket=932 remaining=200") — ليست رسالة للمستخدم، بل أثر تدقيق داخلي
```

---

## 2. حالات `status` الخمس — بلا `null` مطلقاً

| الحالة | تعني | متى |
|---|---|---|
| `UNLIMITED` | لا يوجد أي سقف من أي نوع على هذا الاستهلاك | لا `BenefitRuleBucket` مرتبط، أو كل الأوعية `configured=null` |
| `LIMITED` | يوجد سقف، والطلب ضمنه بالكامل | `approvedQuantity == requestedQuantity` و`approvedDays == requestedDays` |
| `PARTIAL` | يوجد سقف، وجزء فقط مقبول | `0 < approvedQuantity < requestedQuantity` (لا يحدث لـ`days` — ذرّية دائماً) |
| `EXHAUSTED` | يوجد سقف، ولا شيء متاح | `approvedQuantity == 0` و`requestedQuantity > 0` |
| `BLOCKED` | القرار رُفض بنيوياً قبل الوصول لحساب أي رصيد | `BUCKET_POLICY_MISMATCH` أو ما يعادله — لا رقم رصيد يُحسب أصلاً |

**لماذا خمس حالات لا أربع:** `BLOCKED` مختلف جوهرياً عن `EXHAUSTED` — الأول
عطب بنيوي (بيانات غير متسقة، G6)، والثاني حالة عمل عادية متوقعة (رصيد نفد،
G2 عند تكرار الطلب). خلطهما يُخفي عطباً حقيقياً في البيانات خلف رسالة "نفد
السقف" العادية.

**`BLOCKED` يوقف القرار قبل الوصول لأي حساب رصيد، لا بعده.** `BUCKET_POLICY_MISMATCH`
هو سبب رفض (`Failure Reason`) يُلقى كاستثناء من الـResolver، وليس رقم سقف
انتهى. تحديداً:

- `bindingAvailableAmount` عند `status=BLOCKED` **لا يُساوى صفراً** — لا
  يُحسب أصلاً، ولا يُبنى الكائن الناتج بمعزل عن هذا الاستثناء (فشل الاستدعاء
  بالكامل، لا قيمة حارسة تُعاد بصمت).
- `WaadFinancialEngine` **لا يُستدعى إطلاقاً** على قرار `BLOCKED` — القرار
  يتوقف عند طبقة `UnifiedLimitResolver`، فلا يصل أي رقم (صفر أو غيره) لمحرك
  المال ليُعامَل معاملة "سقف مستنفَد عادي".
- تحويل عطب ملكية إلى `bindingAvailableAmount=0` هو بالضبط النمط الممنوع:
  يُخفي خطأً بنيوياً في البيانات (وعاء مرتبط بوثيقة خطأ) خلف نتيجة مالية
  تبدو طبيعية ("نُفد السقف")، فيختفي العطب من أي تقرير تدقيق مالي لاحق.

---

## 3. القواعد الخمس — ملزمة قبل أي كود

1. **`approvedQuantity` يأتي فقط من `UnifiedLimitDecision`.** لا مصدر آخر —
   ويشمل ذلك صراحةً هوك `ClaimLine.initializeFinancialAuditFields()` (P1.1):
   يُعاد توجيهه ليقرأ من هذا القرار، لا أن يُعطَّل بلا بديل.
2. **`approvedDays` يأتي فقط من `UnifiedLimitDecision`.** نفس المبدأ، ولا
   يوجد اليوم أي هوك مكافئ يكتب فوقها (لأن العمود غير موجود بعد) — لكن القاعدة
   تُسجَّل الآن حتى لا يتكرر نفس النمط عند إضافة العمود في P1.4+.
3. **`bindingAvailableAmount` هو الرقم النقدي الوحيد الذي يصل
   `WaadFinancialEngine.Input.bindingAvailableLimit`.** لا يُعاد أي Resolver
   آخر استدعاءه بعد هذه النقطة على نفس القرار (يمنع تكرار عيب B: إعادة الحل
   من الصفر بمعزل عمّا قرره A).
4. **`reserved` يشارك في حساب `remaining` دائماً**، لكل نوع سقف على حدة —
   لا فقط للسقف العام كما يحدث اليوم في `BenefitBucketLimitService` (P1.1،
   الفجوة المؤكَّدة "لا يرى RESERVED لأوعية التصنيف").
5. **`null` لا يعني في آنٍ واحد "غير محدود" و"غير معروف" و"لا ينطبق".** كل
   معنى له تمثيله الصريح الخاص:
   - "لا يوجد سقف من هذا النوع إطلاقاً" ⇒ `configured = null` (حقل واحد فقط).
   - "السقف غير محدود" على مستوى القرار الكلي ⇒ `status = UNLIMITED` (ليس
     `null` في أي حقل رقمي).
   - "لا ينطبق لأن القرار محظور بنيوياً" ⇒ `status = BLOCKED`، وكل الحقول
     الرقمية اللاحقة (approvedQuantity وما بعدها) غير محسوبة أصلاً (تُترك
     على قيمة حارسة موثَّقة، لا `null` صامت).

---

## 3.1 Invariant إلزامي على طبقة الاستمرارية (Persistence)

هذا سطر واحد، لكنه السبب المباشر الذي أخفى فيه هوك `ClaimLine` كمية معتمدة
خاطئة بصمت طوال الوقت (P1.1):

```text
INVARIANT: Persistence lifecycle hooks (@PrePersist/@PreUpdate وما يعادلها)
MUST NOT derive approvedQuantity — ولا approvedDays مستقبلاً — بأي صيغة
افتراضية (مثل approvedQuantity = quantity) بعد أن يوجد UnifiedLimitDecision
لهذا السطر. الكيان يُخزِّن القرار، ولا يُعيد اشتقاقه أبداً.
```

**الأثر العملي على P1.4/P1.6:** `ClaimLine.initializeFinancialAuditFields()`
يُعاد كتابته ليقرأ `approvedQuantity` من `UnifiedLimitDecision` الذي وصل مع
السطر (عبر `ClaimMapper`)، لا أن يفترض `quantity` الكاملة عند غياب قيمة. لو
وصل السطر لدورة الحفظ **بلا** `UnifiedLimitDecision` مرفق (حالة لا يجب أن
تحدث بعد P1.4)، فالفشل يجب أن يكون صريحاً (استثناء)، لا قيمة افتراضية صامتة
تُعيد نفس العطب المكتشف.

---

## 4. مطابقة العقد مع الاختبارات الذهبية الستة (P1.2)

| الاختبار | `status` المتوقع | `bindingConstraintType` |
|---|---|---|
| G1 (مبلغ فقط) | `LIMITED` (600 من أصل طلب أعلى) | `AMOUNT` |
| G2 (Physio، بوابة القبول) | `PARTIAL` | `TIMES` |
| G3 (أيام، صفر متبقٍ) | `EXHAUSTED` | `DAYS` |
| G4 (مبلغ+مرات معاً) | `PARTIAL` | `AMOUNT` (هو الأكثر تقييداً بالوحدات، وليس `TIMES`) |
| G5 (وعاء مشترك محجوز) | `PARTIAL` أو `LIMITED` حسب حجم الطلب | `TIMES` |
| G6 (عدم تطابق ملكية) | `BLOCKED` | — (لا يُحسب) |

هذا الجدول هو معيار قبول P1.4: أي تنفيذ فعلي لـ`UnifiedLimitResolver` يجب أن
يُنتج هذه المطابقة بالضبط قبل اعتباره VERIFIED.

---

## 5. ما هو خارج هذا العقد صراحة

- نسبة التغطية، الخصم التعاقدي، من يدفع — تنتمي لـ`ClaimLineDecision` بعد
  دمج ناتج هذا العقد مع `WaadFinancialEngine.Result`.
- آلية الـOverride (صاحب عمل/عضو) — بنية `LimitSourceProvider` الموجودة في
  نظام B اليوم غير مفعَّلة عملياً (P1.1 مصفوفة القدرات)؛ هذا العقد يترك لها
  حقل `sourceType` ضمنياً عبر `appliedBucketIds`/`decisionReasons` دون
  الالتزام بتفعيلها الآن — قرار منفصل إن احتجناه لاحقاً.
- Policy Revision — خارج نطاق P1 بالكامل (ADR-008 §8، مؤجَّل).

---

## 6. Amendment #1 (P1.5.2) — countingMethod ينتقل من مستوى القرار إلى مستوى الوعاء

**تاريخ:** أثناء مراجعة P1.5.1a، قبل أي Wiring حي.

**OLD (P1.4.1، غير موثق صراحة هنا لكن ضمنياً مفترض عبر
`UnifiedLimitInput.countingMethod`):** `countingMethod` قيمة واحدة تخص القرار/السطر
كله.

**NEW:** `countingMethod` بيانات وصفية (metadata) تخص **الوعاء (Bucket)**، لا
القرار. تُقرأ من `BucketLimitSnapshot.countingMethod()` لكل صف على حدة، وليس
من `UnifiedLimitInput` إطلاقاً (الحقل حُذف منه).

**السبب — مُثبت من الكود، وليس افتراضاً:**
- `BenefitLimitBucket.countingMethod` عمود على كيان الوعاء نفسه.
- `BenefitBucketLimitService.LimitSnapshot` يحمل `countingMethod` واحداً لكل
  وعاء مُطبَّق، مقروءاً من ذلك الوعاء تحديداً.
- المحرك الحي `CoverageEngineService.computeBucketUsage` يقرأ
  `limit.countingMethod()` **داخل حلقته على كل وعاء**، وليس مرة واحدة للسطر —
  أي أن سطراً واحداً يمر عبر أكثر من وعاء يمكن أن يرى أكثر من طريقة عدّ
  فعلياً في الإنتاج الحالي.

**الأثر على `UnifiedLimitResolver`:** قرار القابلية للتجزئة (divisible/atomic)
يُحسب الآن لكل وعاء TIMES على حدة باستخدام `countingMethod` الخاص به، ثم
تؤخذ أضيق نتيجة عبر كل الأوعية (تماماً كما تُؤخذ أضيق `remaining` أصلاً) — لا
ينتشر أسلوب وعاء إلى وعاء آخر. مُثبت بحالات CM1–CM5
(`docs/finance/P1_5_2_BUCKET_LEVEL_COUNTING_METHOD.md`).

هذا **ليس فشلاً في P1.3** — بل تنقيح (refinement) ثبت بالكود بعد اعتماد
العقد الأصلي، تماماً كما ثبتت إضافة `countingMethod` نفسها إلى `UnifiedLimitInput`
كحقل ناقص أثناء P1.4.2.
