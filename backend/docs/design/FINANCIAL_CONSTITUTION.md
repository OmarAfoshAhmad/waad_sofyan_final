# الدستور المالي لمنظومة WAAD TPA

**الإصدار:** `WAAD-FIN-1.0`
**الحالة:** مُجمَّد (Frozen). أي تغيير يتطلب إصدار نسخة جديدة، لا تعديلاً في مكانه.

هذا المستند هو **المرجع الوحيد** للقواعد المالية. عند أي تعارض بينه وبين كود أو اختبار
أو تعليق أو قرار سابق (بما في ذلك أي شيء على فرع `finance-00`) — هذا المستند هو الحاكم.

---

## 0. الأطراف الثلاثة

| الطرف | يتحمل |
|---|---|
| شركة التأمين / WAAD | `insurerFinalPayment` |
| المستفيد | `patientCoverageShare` + `patientLimitExcess` |
| المرفق الصحي | `contractualPriceExcess` + `providerContractDiscount` + `providerRejectedAmount` |

لكل دينار في المطالبة مصير واحد معروف. ممنوع وجود خانة عامة تجمع أسباباً مالية مختلفة.

---

## 1. تاريخ الخدمة هو الحاكم

كل اختيار مالي يعتمد على `serviceDate` حصراً: عقد المرفق، السعر التعاقدي، نسبة التغطية،
نسخة الوثيقة، السقوف، استثناء الشركة، استثناء المستفيد، نسبة الخصم.

**ممنوع** الاعتماد على تاريخ الإدخال أو المراجعة أو الاعتماد أو الدفع.

---

## 2. لا وجود لـ Coverage = 0%

**قرار مُحسَم — لا يحتاج نقاشاً ولا `POLICY_DECISION_REQUIRED`.**

الخدمة التي تدخل المحرك المالي هي خدمة **لها تغطية تأمينية فعلية معتمدة**.

- ممنوع إنشاء منطق خاص بـ `coveragePercent == 0`.
- ممنوع استخدام 0% لتمثيل خدمة غير مغطاة.
- ممنوع استخدام 0% لتمثيل خدمة مرفوضة.
- ممنوع استخدام 0% لتمثيل نفاد السقف.

الحالات غير المغطاة تُعالَج في قواعد التغطية/الأهلية **قبل** المحرك المالي، لا داخله.

---

## 3. أساس التسوية

```
contractualPriceExcess = max(0, requestedAmount - contractualPrice)
settlementBase         = min(requestedAmount, contractualPrice)
```

`contractualPriceExcess` مسؤولية المرفق حصراً: لا يُحمَّل للمستفيد، ولا لشركة التأمين،
**ولا يستهلك السقف**.

السعر التعاقدي هو الحد الأعلى الذي يدخل الحساب التأميني. وإذا طالب المرفق بأقل منه،
فالأساس هو المطلوب — لا يجوز دفع أو استهلاك أكثر مما طولب به فعلاً.

---

## 4. معنى السقف

> السقف هو **الحد المتاح من قيمة الخدمات التعاقدية** التي يمكن أن تدخل ضمن التغطية
> التأمينية للمستفيد.

السقف **ليس**: `maximumCompanyShare`، ولا `netCompanyPaymentLimit`، ولا سقفاً لصافي ما تدفعه
شركة التأمين.

لذلك يُقَص `settlementBase` على السقف **قبل** توزيعه بين الشركة والمستفيد:

```
limitCoveredBase    = min(settlementBase, availableLimit)
patientLimitExcess  = max(0, settlementBase - availableLimit)
limitConsumption    = limitCoveredBase
remainingLimit      = max(0, availableLimit - limitConsumption)
```

**نتائج ملزمة:**
- نسبة تحمل المستفيد **لا** تقلل استهلاك السقف.
- الخصم التعاقدي **لا** يقلل استهلاك السقف.
- المرفوض **لا** يقلل استهلاك السقف.
- السقف **لا** يعتمد على `Net Provider Amount`.

---

## 5. قاعدة عدم وجود سالب

لا يجوز لأي من القيم التالية أن تكون سالبة تحت أي ظرف:

`availableLimit` · `limitCoveredBase` · `patientLimitExcess` · `limitConsumption` ·
`remainingLimit` · `patientCoverageShare` · `insurerGrossShare` ·
`providerContractDiscount` · `providerRejectedAmount` · `insurerFinalPayment`

القيمة السالبة يمكن الاحتفاظ بها **فقط** كمؤشر تدقيقي لانحراف تاريخي، لا كرصيد أعمال
ظاهر أو قابل للاستخدام.

---

## 6. تجاوز السقف يتحمله المستفيد

أي جزء من الخدمة يتجاوز الرصيد المتبقي يتحمله **المستفيد بالكامل**. شركة التأمين لا
تتحمل منه شيئاً. والجزء الواقع داخل السقف يبقى خاضعاً لنسبة التغطية العادية.

**التعبئة جزئية، لا رفض كامل.**

---

## 7. الفصل بين نوعَي مسؤولية المستفيد

```
patientTotalResponsibility = patientCoverageShare + patientLimitExcess
```

| الحقل | المعنى | السبب |
|---|---|---|
| `patientCoverageShare` | نسبة التحمل الطبيعية على الجزء داخل السقف | شرط الوثيقة |
| `patientLimitExcess` | كامل الجزء المتجاوز للسقف | نفاد الرصيد التأميني |

ممنوع دمجهما. يجب أن يظهرا منفصلين في المطالبة والتقارير.

---

## 8. ترتيب حساب التغطية

نسبة التغطية تُطبَّق على `limitCoveredBase` **فقط**:

```
patientCoverageShare = limitCoveredBase × patientPercent
insurerGrossShare    = limitCoveredBase - patientCoverageShare
```

---

## 9. الخصم التعاقدي

```
providerContractDiscount   = insurerGrossShare × providerDiscountPercent
providerNetBeforeRejection = insurerGrossShare - providerContractDiscount
```

الخصم مسؤولية المرفق. ممنوع: تحميله للمستفيد، إضافته إلى حصة المستفيد، إعادة احتسابه
ضمن السقف، أو إعادة توزيعه بين الشركة والمستفيد.

> **قرار أعمال قطعي:** المستفيد يستفيد من السعر التعاقدي المتفق عليه للخدمة، لكنه لا
> يحصل تلقائياً على الخصم الإضافي الممنوح لشركة التأمين من حصة المرفق.

---

## 10. إلغاء `discountBeforeRejection`

**لم تعد هناك سياستان.** السياسة واحدة وثابتة:

```
insurerGrossShare → providerContractDiscount → providerNetBeforeRejection → providerRejectedAmount
```

يجب حذف الفرع المالي القديم من المحرك، وحذف اختباراته، وعدم الاحتفاظ به كـ feature flag
ولا كحقل ميت في `Input`/`Result`/DTO، وعدم تأجيل تنظيفه إلى مرحلة لاحقة.

---

## 11. المرفوض

```
insurerFinalPayment = providerNetBeforeRejection - providerRejectedAmount
0 ≤ providerRejectedAmount ≤ providerNetBeforeRejection
```

المرفوض مسؤولية المرفق حصراً. ولا يُعيد حساب: `patientCoverageShare`، ولا
`patientLimitExcess`، ولا `limitConsumption`. ولا يتحول إلى دين على المستفيد.

إن تجاوز المرفوضُ صافيَ المرفق ⇒ **Fail Closed**، لا `providerNet` سالب ولا دفعة سالبة.

---

## 12. الترتيب النهائي للمحرك (لا يتغير دون `POLICY_DECISION_REQUIRED`)

```
 1. قراءة requestedAmount
 2. جلب contractualPrice الساري في serviceDate
 3. حساب فرق السعر
 4. تحميل الفرق على المرفق
 5. تحديد settlementBase
 6. تحديد availableLimit
 7. حساب limitCoveredBase
 8. حساب patientLimitExcess
 9. تطبيق نسبة التغطية على limitCoveredBase فقط
10. حساب patientCoverageShare
11. حساب insurerGrossShare
12. تطبيق الخصم التعاقدي على insurerGrossShare
13. الوصول إلى providerNetBeforeRejection
14. طرح providerRejectedAmount
15. حساب insurerFinalPayment
16. تسجيل limitConsumption
17. تحديث الرصيد دون السماح بالقيم السالبة
18. التحقق من ثبات المعادلة قبل الاعتماد
```

---

## 13. الثابت المحاسبي

**على مستوى البند:**

```
requestedAmount =
      contractualPriceExcess
    + patientCoverageShare
    + patientLimitExcess
    + providerContractDiscount
    + providerRejectedAmount
    + insurerFinalPayment
```

**على مستوى المطالبة:** كل إجمالي في `Claim` = مجموع الحقل نفسه من `ClaimLines`.
لا توجد معادلة مالية جديدة على مستوى `Claim`. الإجمالي هو مجموع البنود المقرَّبة.

---

## 14. المثال الذهبي (اختبار القبول المركزي)

**المدخلات:** `requestedAmount=1200` · `contractualPrice=1000` · `availableLimit=600` ·
`coveragePercent=80%` · `providerDiscountPercent=10%` · `providerRejectedAmount=50`

| الحقل | القيمة |
|---|---|
| `contractualPriceExcess` | 200 |
| `settlementBase` | 1,000 |
| `limitCoveredBase` | 600 |
| `patientLimitExcess` | 400 |
| `patientCoverageShare` | 120 |
| `patientTotalResponsibility` | **520** |
| `insurerGrossShare` | 480 |
| `providerContractDiscount` | 48 |
| `providerNetBeforeRejection` | 432 |
| `providerRejectedAmount` | 50 |
| `insurerFinalPayment` | **382** |
| `limitConsumption` | **600** |
| `remainingLimit` | 0 |

**التحقق:** `200 + 400 + 120 + 48 + 50 + 382 = 1,200` ✓

---

## 15. السقوف المتعددة

```
effectiveAvailable = min(available من كل وعاء منطبق)
limitCoveredBase   = min(settlementBase, effectiveAvailable)
```

يُسجَّل `limitConsumption` في **كل** وعاء منطبق بالقيمة نفسها (خدمة 300، مجموعة 300،
عام 300). لكن القيمة الطبية المستخدمة تبقى 300 وليست 900 — التقارير لا تجمع مستويات
الهرم.

---

## 16. المطالبات متعددة البنود

الاستهلاك بترتيب **حتمي**:
1. الحجوزات المرتبطة بموافقة مسبقة
2. أولوية المنفعة الصريحة إن وُجدت
3. ترتيب البنود داخل المطالبة (مثبَّت في اللقطة)
4. `lineId` كفاصل أخير

```
availableForNextLine = availableForCurrentLine - limitConsumption
```

ممنوع الاعتماد على ترتيب قاعدة البيانات العشوائي أو `Thread timing`. نفس المطالبة
تعطي نفس الناتج في كل مرة.

---

## 17. أولوية السقوف

```
MEMBER_OVERRIDE > EMPLOYER_OVERRIDE > POLICY_DEFAULT
```

**قيمة مطلقة بديلة، لا زيادة تراكمية.** (وثيقة 2,000 + شركة 3,000 + مستفيد 5,000
⇒ `effectiveLimit = 5,000`، لا 10,000.)

المحرك المالي **لا يعرف** لماذا أصبح `availableLimit` بقيمة معينة. طبقة الـ Resolution
هي التي تقرر.

---

## 18. الاستثناءات موثَّقة

`Override` ليس مجرد رقم معدَّل. يجب توثيق: المستفيد/الشركة · السقف · القيمة الأصلية ·
القيمة الجديدة · طالب التغيير · الجهة الطالبة · السبب · رقم الخطاب · نسخة الخطاب كمرفق ·
تاريخ الطلب · `effectiveFrom` · `effectiveTo` · من أدخل · من اعتمد · الحالة · Audit Trail
كامل.

ممنوع إنشاء `Override` عبر تعديل مباشر أو SQL.

---

## 19. منع تعديل الماضي

أي تغيير في: السقف · التغطية · العقد · السعر · خصم المرفق · سياسة الشركة · استثناء
المستفيد — يجب أن يكون `Versioned` و`Effective-Dated`.

ممنوع تعديل النسخة القديمة في مكانها إذا دخلت في عمليات مالية. السياسة الجديدة تؤثر من
تاريخ سريانها إلى الأمام فقط.

**تخفيض السقف تحت الاستهلاك السابق:** `remainingLimit = 0`، وليس رصيداً سالباً، ولا
حذفاً للاستهلاك القديم.

---

## 20. الموافقات المسبقة والحجوزات

`RESERVED ≠ CONSUMED/COMMITTED`

الحجز يقلل `availableLimit` لكنه ليس صرفاً نهائياً. عند التحول إلى مطالبة، في **معاملة
ذرية واحدة**: تحرير الحجز + تسجيل الاستهلاك الحقيقي. ممنوع حجز 1,000 + استهلاك 1,000
لنفس الخدمة.

إلغاء الموافقة قبل التنفيذ: يحرر الحجز، يعيد الرصيد، لا يسجل استهلاكاً، ويحتفظ بسجل
الإلغاء.

---

## 21. العكس والتصحيح

```
Original Claim → Reversal → New Calculation Version → Validation → Approval
```

لا تُحذف مطالبة معتمدة ولا يُعدَّل قيد مالي معتمد مباشرة. العكس يُنشئ أثراً مرتبطاً
بالأصل ويعيد أثر السقف بطريقة قابلة للتدقيق.

---

## 22. عدم التكرار والتزامن

**مفتاح عدم التكرار:** `claimId + lineId + bucketId + calculationVersion + entryType`

**التزامن:** قراءة/استهلاك السقف داخل معاملة ذرية بقفل. رصيد 500 ومطالبتان بـ400 ⇒
الأولى تستهلك 400، والثانية `limitCoveredBase=100` و`patientLimitExcess=300`،
و`remainingLimit=0`. ممنوع أن تستهلكا 800.

---

## 23. التقريب

عملة `LYD` · منزلتان عشريتان · `BigDecimal` حصراً · `HALF_UP` · **ممنوع**
`double`/`float` في أي حساب مالي.

---

## 24. Fail Closed — الحالات التي تمنع الاعتماد

- `availableLimit < 0` أو `remainingLimit < 0` أو `limitConsumption < 0`
- `patientLimitExcess < 0` أو `insurerFinalPayment < 0`
- `providerRejectedAmount > providerNetBeforeRejection`
- فرق مالي غير مفسَّر (الثابت في §13 لا يتحقق)
- `Claim totals` لا تساوي مجموع `Claim Lines`
- نفس الاستهلاك مسجَّل مرتين
- سياستان فعالتان متعارضتان لنفس التاريخ دون أولوية واضحة
- أكثر من عقد فعال متعارض
- سعر تعاقدي غير معروف لخدمة تتطلب عقداً
- استثناء سقف بلا تاريخ سريان، أو `Override` غير محدد المصدر
- عملية مالية تعتمد على تاريخ غير `serviceDate`
- اعتماد يؤدي إلى تجاوز الرصيد بسبب `Race Condition`

---

## 25. مصدر مالي واحد

يجب ألا توجد معادلة مالية في `ClaimService` وأخرى في `ReviewService` وثالثة في
`PreAuthorizationService`. كل البوابات تعتمد نفس المحرك.

أي طبقة أخرى: **تجهّز Input → تستدعي المحرك → تستخدم Result.**

---

## 26. الوضع الإنتاجي الحالي

لا توجد مطالبات إنتاجية تاريخية تستوجب Migration مالياً أو إعادة تسوية. لذلك:
لا تُبنى `Legacy Compatibility` غير ضرورية، ولا يُحافَظ على سلوك مالي خاطئ بحجة التوافق.
لكن كل شيء جديد يجب أن يكون `Versioned` منذ اليوم الأول.

---

## المسائل المفتوحة (`POLICY_DECISION_REQUIRED`)

هذه لا تحجب المسار المالي الحرج، لكنها تحجب مراحل لاحقة محددة:

| # | المسألة | تحجب |
|---|---|---|
| 01 | السعر التعاقدي غير موجود (خدمة طارئة / مرفق غير متعاقد) | مرحلة الإصدارات |
| 03 | نفاد السقف العددي (المرات/الأيام): هل كامل `settlementBase` على المستفيد؟ | توحيد دلالة السقف |
| 04 | السقوف العائلية المشتركة | مؤجَّلة — لا تُفعَّل |
| 05 | الإشعارات الدائنة والاستردادات | مرحلة العكس |
| 06 | إدخال `effectiveFrom` بأثر رجعي بعد وجود مطالبات | مرحلة الإصدارات |
| 07 | تغيّر السقف بعد الحجز وقبل تنفيذ الخدمة | مرحلة الحجوزات |
| 08 | تعدد العملات | مؤجَّلة — `LYD` فقط في هذا الإصدار |

المسألة 02 (`Coverage = 0%`) **حُذفت** — محسومة في §2 أعلاه.
