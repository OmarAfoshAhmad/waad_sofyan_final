# خطة إغلاق موديول المستفيدين (Member Module Closure Plan)

**الفرع:** `long-term/member-hardening/member-closure`
**التاريخ:** 2026-08-11
**الحالة:** Phase 1 منفَّذة ومُختبَرة · Phase 2-5 مخطَّطة، لم تُنفَّذ بعد

---

## 0. لماذا هذه الوثيقة

هذا الموديول يخدم قراراً طبياً/مالياً حياً (الأهلية عند نقطة الخدمة، والسقف السنوي).
أي خطأ فيه إما يُخطئ في القرار الطبي أو يُربك المستخدم بأرقام لا تُصدَّق. هذه
الوثيقة تُسجّل: ما المشكلة، لماذا حدثت، ماذا أُصلح، وماذا تبقّى — بنفس انضباط
`FINANCIAL_CONSTITUTION.md`: مصدر حقيقة واحد، توثيق لكل دالة، عدم حذف مسار
قديم دون إثبات صفر مُستدعٍ، واختبار قبل الادعاء.

---

## 1. التشخيص الذي بدأ منه العمل

تحليل عميق سابق لنافذة المستفيدين كشف:

- **P0 — محوران متناقضان للسقف السنوي.** نافذة المستفيد وبوابة اعتماد
  المطالبة كانتا تحسبان "المستخدم من السقف" بمحور `Claim.approvedAmount`
  (ما دفعته الشركة فعلياً)، بينما محرك WAAD-FIN-1.0 المعتمد يُقيِّد كل بند
  بمحور `ClaimLine.limitConsumption` (قيمة التسوية داخل السقف، قبل التغطية
  والخصم والرفض — §4 من الدستور المالي). الفرق ليس تقريبياً: في المثال
  الذهبي نفسه (`WaadFinConstitutionGoldenTest`) الفرق بين المحورين 218 على
  مطالبة واحدة، ودائماً في اتجاه المبالغة في "المتبقي".
- **P1 — أداء غير محدود في شاشة الأهلية العائلية.** `checkFamilyEligibility`
  كانت تستدعي خدمة الملخص المالي مرة لكل فرد من العائلة، وكل استدعاء يحمّل
  **كل** مطالبات ذلك الفرد التاريخية إلى الذاكرة (`findByMemberId` بلا حد)
  ثم يُجمِّعها بحلقات Java. عائلة من 6 أفراد × 200 مطالبة لكل فرد = 1200 كيان
  مُحمَّل لعرض أربعة أرقام، في شاشة تُستخدم أمام المريض في الاستقبال.
- دين تقني مُقاس: `MemberFinancialSummaryService` بلا أي اختبار قبل هذا
  العمل، فلتر عزل جهة العمل منسوخ يدوياً بين `getAllMembers`/`searchMembers`
  (التعليق نفسه يقول "COPIED")، ونموذجا الإنشاء/التعديل في الواجهة (1849
  سطراً مجتمعين) بلا مكوّن مشترك.

---

## 2. Phase 1 — تصحيح المحور + إزالة N+1 (مُنفَّذة)

### 2.1 مصدر الحقيقة الجديد

كل قراءة لـ"ما استُهلك من السقف السنوي" تمر الآن عبر نقطة واحدة:

```
BenefitPolicyCoverageService.getLimitConsumedForYear(memberId, year, excludeClaimId)   -- فرد واحد
BenefitPolicyCoverageService.getLimitConsumedForYear(memberIds, year, excludeClaimId)  -- بالجملة
```

كلاهما يعيد استخدام نفس استعلام قاعدة البيانات الذي يستخدمه المحرك المعتمد
نفسه (`LimitBalanceReader`) لمحاسبة الأوعية — **لا معادلة ثانية أُنشئت**؛ فقط
نظير بالجملة له (`sumLimitConsumptionByMembersAndPeriodExcludingClaim`) بنفس
شرط `WHERE` حرفياً، موثَّق بضرورة إبقائهما متزامنين إذا تغيّر المحور مستقبلاً.

### 2.2 نقاط الاستدعاء المُصحَّحة

| الموقع | قبل | بعد |
|---|---|---|
| `BenefitPolicyCoverageService.validateAmountLimits` (بوابة اعتماد المطالبة) | `sumApprovedAmountByMemberAndYear` (approvedAmount) | `getLimitConsumedForYear` (limitConsumption) |
| `BenefitPolicyCoverageService.getRemainingCoverage` | نفسه | نفسه |
| `ClaimFinancialSnapshotService.finalizeSnapshot` | يمرّر `claim.getApprovedAmount()` إلى الفحص | يمرّر `ClaimFinancialTotals.sumLimitConsumption(claim)` — المحور نفسه الذي يقارَن به |
| `MemberFinancialSummaryDto.remainingCoverage`/`utilizationPercent` | مبنيان على `totalApproved` | مبنيان على حقل جديد `limitConsumedAmount` |
| نافذة المستفيد + الأهلية (`usedAmount` في JSON) | `getTotalApproved()` | `getLimitConsumedAmount()` |

`totalApproved` **لم يُحذف** — بقي مقياساً شرعياً مستقلاً ("كم دفعت الشركة
فعلياً")، لكنه لم يعد يُستخدم لحساب أي شيء له علاقة بالسقف. هذا يطبّق نفس
مبدأ "لا تخلط محورين" الذي طُبِّق على أبعاد السقف في `finance-03.1`.

الاستعلام القديم `sumApprovedAmountByMemberAndYear` **لم يُحذف أيضاً** رغم أن
مستدعيَيه الوحيدين حُوِّلا — تُرك موصوفاً في هذه الوثيقة كمرشح حذف بعد التأكد
أن لا مُستهلك خارجي (تقارير، Excel) ما زال يعتمده (لم يُتحقق من ذلك بعد،
انظر §4).

### 2.3 إزالة N+1 (الجملة بدل الحلقة)

`MemberFinancialSummaryService` أُعيد بناؤه بالكامل:

- **دالة واحدة** (`getFinancialSummaries(Collection<Long>)`) هي التنفيذ
  الوحيد؛ `getFinancialSummary(Long)` غلاف رقيق فوقها (عنصر واحد في قائمة)
  — لا يمكن للمسارين أن ينحرفا عن بعضهما لأن أحدهما **هو** الآخر حرفياً.
- لا يُحمَّل أي كيان `Claim` إلى الذاكرة بعد الآن لبناء الإحصاءات؛ استعلام
  تجميعي واحد (`ClaimRepository.findFinancialAggregatesByMemberIds`، نتيجة
  `GROUP BY member.id`) يُرجع العدد والمجاميع لكل الأعضاء المطلوبين معاً.
- `UnifiedMemberService.checkFamilyEligibility` يبني قائمة معرّفات العائلة
  (الأصل + كل الفروع) ويستدعي `getFinancialSummaries` **مرة واحدة**، بدل
  حلقة `for` تستدعي الخدمة لكل فرد.
- **التكلفة الثابتة الآن لأي حجم عائلة:** استعلام أعضاء + استعلام إحصاءات
  مطالبات + استعلام استهلاك سقف = 3 استعلامات، بدل `1 + 2×N`.

### 2.4 الاختبارات المُضافة/المُصحَّحة

| الملف | التغيير |
|---|---|
| `MemberFinancialSummaryServiceTest` (جديد) | 6 اختبارات: يثبت أن `limitConsumedAmount` ≠ `totalApproved` بالأرقام الذهبية نفسها (382 مقابل 600)، أن القراءة بالجملة تستدعي كل استعلام تجميعي **مرة واحدة** بصرف النظر عن عدد الأعضاء، حالة بلا مطالبات، حالة بلا وثيقة، فشل مغلق عند عضو غير موجود |
| `BenefitPolicyCoverageServiceTest` | 3 اختبارات قديمة حُوِّلت من محاكاة `sumApprovedAmountByMemberAndYear` إلى `sumLimitConsumptionByMemberAndPeriodExcludingClaim` + 3 اختبارات جديدة للدالة الفردية والجماعية |
| `ClaimFinancialSnapshotServiceTest` | بيانات الاختبار عُدِّلت لتُميِّز عمداً بين `companyShare`/`limitConsumption` (700 مقابل 648) بدل قيمة مشتركة تُخفي أي خطأ محور |

**نتيجة التشغيل الفعلي (لا ادعاء):** `mvn test` — **461/461** اختباراً
وحدوياً (خارج الاختبارات التي تحتاج Postgres عبر Testcontainers، والتي لم
تُشغَّل في هذه الجلسة لعدم توفر Docker محلياً؛ لم تُدَّعَ نتيجتها).

---

## 3. البنية بعد الإصلاح — ملخص الملفات

**جديد:**
- `claim/projection/MemberFinancialAggregateProjection.java`
- `claim/projection/MemberLimitConsumptionProjection.java`
- `member/service/MemberFinancialSummaryServiceTest.java`

**مُعدَّل:**
- `claim/repository/ClaimRepository.java` — استعلاما تجميع جديدان (فردي متوفر
  سلفاً، بالجملة جديد لكليهما)
- `benefitpolicy/service/BenefitPolicyCoverageService.java` — `getLimitConsumedForYear` (فردي + جملة)، تحويل نقطتَي الاستدعاء، حذف `calculateUsedAmountForYear` الميتة
- `claim/service/finance/ClaimFinancialTotals.java` — `sumLimitConsumption(Claim)`
- `claim/service/ClaimFinancialSnapshotService.java` — نقطة الاستدعاء + التوثيق
- `member/dto/MemberFinancialSummaryDto.java` — حقل `limitConsumedAmount` + توثيق كل حقل مالي بمحوره
- `member/service/MemberFinancialSummaryService.java` — إعادة بناء كاملة (استعلام بدل تحميل، دالة جملة واحدة)
- `member/service/UnifiedMemberService.java` — استبدال الحلقة بقراءة جملة واحدة
- `member/controller/{UnifiedMemberController,UnifiedEligibilityController}.java` — تصحيح محور `usedAmount`
- اختبارات: `BenefitPolicyCoverageServiceTest`, `ClaimFinancialSnapshotServiceTest`

**لا تغيير في العقد الظاهري:** أسماء حقول JSON للواجهة (`usedAmount`,
`remainingLimit`, `usagePercentage`) لم تتغيّر — القيمة فقط تصحّحت. الواجهة
لا تحتاج أي تعديل لهذه المرحلة (تحقّقتُ من `EligibilityCheck.jsx` مباشرة).

---

## 4. Phase 2 — تنظيف الديون المتبقية (مخطَّطة، لم تُنفَّذ)

بالترتيب المقترح:

1. **إثبات صفر مُستدعٍ لـ`sumApprovedAmountByMemberAndYear` ثم حذفها.**
   ابحث في التقارير وXlsx والواجهة أيضاً، لا الـbackend فقط.
2. **توحيد فلتر عزل جهة العمل.** استخراج الكتلة المنسوخة بين
   `getAllMembers`/`searchMembers` (المُعلَّم صراحة "COPIED from
   getAllMembers" في الكود) إلى دالة واحدة في `AuthorizationService` أو
   `UnifiedMemberService` نفسها، مع اختبار يثبت أن كلا المسارين يستدعيانها.
3. **`national_number` — القرار محسوم: حقل اختياري لا يُعتمَد عليه كمعرِّف
   فريد.** لا `UNIQUE` ولا تصميماً جديداً — بقي فهرساً عادياً كما هو، بلا أي
   هجرة. الإصلاح الوحيد المطلوب كان كودياً بحتاً: `MemberRepository` كانت
   تحمل `Optional<Member> findByNationalNumber(...)` — دالة نتيجة **مفردة**
   تفترض ضمنياً أن الرقم فريد؛ لو استُدعيت فعلياً مع رقم مكرر (المسموح به
   بحكم هذا القرار) لكانت رمت `IncorrectResultSizeDataAccessException` وقت
   التشغيل. تحقّقتُ أنها **بلا أي مُستدعٍ** في الإنتاج أو الاختبارات فحذفتها
   — لا خطر حالياً، لكنها كانت فخاً لأي مطوّر مستقبلي يستخدمها بحسن نية.
   فحصتُ أيضاً `MemberDuplicateService` (مفتاح التجميع فعلياً هو الاسم
   المُطبَّع + جهة العمل/الأصل، لا الرقم الوطني إطلاقاً) و`MemberImportRowProcessor`
   (يكتب القيمة فقط، لا يبحث بها كمفتاح) — لا افتراض خفي آخر بالتفرّد وُجد.
4. **حذف `civilId` — نطاق أوسع مما بدا، لم يُنفَّذ بعد.** مُعلَّم
   `@Deprecated` منذ فترة، لكن الفحص الفعلي وجده في **25 ملفاً**، وليس مجرد
   إعادة تسمية: `ClaimRepository`/`UnifiedSearchService` تستخدمانه فعلياً
   كحقل بحث حي في 8+ استعلامات (`m.civilId LIKE ...`, `c.member.civilId
   LIKE ...`) عبر بحث المطالبات وتقارير المزوّدين والبحث الموحّد. هذا حجم
   مقارب لـPhase 1 نفسها، ويحتاج فحص كل مسار بحث على حدة للتأكد من عدم كسر
   تجربة البحث عند التحويل إلى `nationalNumber`، لا صف واحد. مؤجَّل لجولة
   مستقلة مخصصة له وحده.
   - ملاحظة جانبية: `EligibilityCheck.civilId` **ليس** نفس الحقل — عمود
     مستقل (`member_civil_id`) منسوخ وقت فحص الأهلية، لا صلة مباشرة به.
5. **`kinshipVerified` بلا `@Builder.Default` — مُصلَحة.** كانت `= false`
   بمُهيِّئ حقل عادي يتجاهله Lombok `@Builder`؛ كل عضو يُنشأ بالـbuilder
   (النمط السائد في كل الكود) كان يُخزَّن بـ`NULL` صراحة، **يُبطل** افتراضي
   القاعدة `DEFAULT FALSE` من الهجرة V67 (لأن Hibernate يرسل NULL صراحة بدل
   حذف العمود من الإدراج). الاستعلام الوحيد المستهلك له عامل NULL كـfalse
   أصلاً فلا أثر وظيفي سابق كان ملحوظاً، لكن الإصلاح يمنع تراكم قيم NULL
   مستقبلاً ويُصحّح النية الحقيقية للحقل.

---

## 5. Phase 3 — الواجهة (جزء منها مُنفَّذ)

**مُنفَّذ:** استُخرج المنطق المكرر حرفياً بين `UnifiedMemberCreate.jsx` و
`UnifiedMemberEdit.jsx` إلى `member.shared.js` — تقييد الإدخال الرقمي،
التحقق من الرقم الوطني/الهاتف، `menuProps`، وترجمة صلة القرابة (كانت مكتوبة
يدوياً في `Edit` رغم وجود `RELATIONSHIP_AR` جاهزة). **لم يُدمَج المكوّنان في
نموذج واحد عمداً** — الفروق بينهما جوهرية (وضع التسجيل السريع في `Create`،
حالة/صلة القرابة في `Edit`)، ودمجهما كان سيصبح إعادة تصميم محفوفة بمخاطر UX،
لا إصلاح تكرار. تحقّق فعلي: `eslint` صفر أخطاء + `vite build` ناجح كامل.

**لم يُنفَّذ بعد:** تفكيك `UnifiedMemberView.jsx` (1284 سطر / 26 `useState`)
إلى مكونات فرعية حسب القسم (بيانات شخصية / مالية / تابعون / مستندات).

---

## 6. Phase 4 — `getServiceCoverageLimits` (مُنفَّذة)

استُبدلت حلقة Java (تحميل كل مطالبات العضو ثم عدّ تطابق `serviceCode` يدوياً)
باستعلام `COUNT` واحد: `ClaimRepository.countServiceUsageForMemberAndYear`.

**علّة صحة إضافية اكتُشفت أثناء الإصلاح، لا مجرد أداء:** الحلقة الأصلية كانت
تعدّ عبر `claim.getLines()` **بلا** تصفية `currentLine = true` — أي أنها كانت
تُحصي سطور المطالبة القديمة (قبل التصحيح) والجديدة معاً بعد أي دورة تصحيح
(V152)، فتُضاعف عدّ زيارة واحدة حقيقية كزيارتين. كل استعلام تجميعي آخر في
`ClaimRepository` يُصفّي بـ`currentLine = true` لنفس السبب
(`sumLimitConsumptionByMemberAndPeriodExcludingClaim` مثالاً) — الاستعلام
الجديد طبّق نفس القاعدة المُتّبعة أصلاً بدل ترك هذه الحالة استثناءً وحيداً.
بقيت بقية السلوك كما هي حرفياً: استبعاد `REJECTED` فقط (كل حالة أخرى، حتى
`DRAFT`/`SUBMITTED`، تُحسب — سلوك موروث لم يُغيَّر هنا، فقط لم يُخفَ).

اختُبرت 3 حالات جديدة: القراءة من الاستعلام لا من مطالبات محمَّلة، تجاوز
الحد وتثبيت المتبقي عند صفر لا سالب، وتخطي الاستعلام كلياً حين لا يوجد حد
مرات أصلاً.

---

## 7. Phase 5 — التناغم مع الموديولات الأخرى

- **`claim`**: هذا العمل لم يمسّ `WaadFinancialEngine` ولا
  `ClaimFinancialAdjudicationService` — فقط جعل بوابة الاعتماد (`ClaimFinancialSnapshotService`) والقراءة (نافذة المستفيد) تتفقان مع المحور الذي يفرضه المحرك أصلاً. لا تغيير سلوكي في نتيجة أي مطالبة معتمدة فعلياً؛ التغيير في **ما يُقاس** قبل الاعتماد وما يُعرض بعده.
- **`benefitpolicy`**: `getLimitConsumedForYear` يعيش في نفس الملف الذي
  يملك بالفعل `getRemainingCoverage`/`validateAmountLimits` — لا موديول
  جديد أُنشئ، تماشياً مع "لا تُوزِّع منطق السقف عبر ملفات متعددة".
- **`preauthorization`**: **لم تُمَس عمداً**. `PreAuthorizationService`
  لا يزال يمرر `approvedAmount` إلى `validateAmountLimits` (الآن يُقارَن
  بمحور صحيح، لكن الجانب الآخر من المقارنة يبقى تقريبياً لأن الموافقة
  المسبقة لا تُشغِّل محرك WAAD-FIN-1.0 بعد). هذا هو نفس الفجوة P1-3 المرصودة
  سابقاً ("الموافقة المسبقة لا تحجز سقفاً") — لم تتّسع ولم تُحل هنا، فقط لم
  تُخفَ.
- **`settlement`/`report`**: لم يُفحص بعد إن كانت أي شاشة تقارير تعتمد
  `sumApprovedAmountByMemberAndYear` أو تُعيد اشتقاق "السقف المستخدم" بمعادلة
  خاصة بها (انظر §4 بند 1) — يجب فحصه قبل إغلاق Phase 2.

---

## 8. Phase 6 — تدقيق جودة شامل: الحجم، إعادة الاستخدام، الكود الميت

طلب مستقل لاحق: قياس فعلي (لا تقديري) لعدد الأسطر ونوع الدوال ومدى تطبيق
مبدأ إعادة الاستخدام والكشف عن كود ضعيف الجودة عبر كامل الموديول (backend +
frontend، بما فيها كل ما أنجزته Phase 1-5).

**الأرقام المُتحقَّقة:** 21,229 سطراً عبر 93 ملفاً (14,391 backend main / 1,395
backend test / 5,443 frontend). 56 REST endpoint، 65 استعلام Repository في
`MemberRepository` وحدها. أكبر خمسة ملفات: `UnifiedMemberController` (1447)،
`UnifiedMemberService` (1399)، `MemberExcelTemplateService` (1374 حينها)،
`UnifiedMemberView.jsx` (1284)، `UnifiedMemberCreate.jsx` (982).

### 8.1 حذف كود ميت — `MemberImportController` (331 سطراً)

مُعلَّم صراحة `@Deprecated` ("LEGACY... use MemberExcelTemplateController")،
مُثبَّت على `/api/v1/members/legacy-import`. تحقَّقت قبل الحذف: صفر مرجع من
الواجهة الأمامية، صفر اختبار، وكل تبعية يستخدمها (`MemberExcelImportService`،
`ExcelColumnMappingService`، كيانات/مستودعات الاستيراد) **مشتركة أيضاً** مع
`MemberExcelTemplateController` الحي — لا شيء يتيتّم بالحذف. حُذف كاملاً.

### 8.2 اختبارات توصيف + علّة حقيقية مكتشفة في `importFromExcel`

`importFromExcel` (~600 سطراً، خوارزمية استيراد متعددة المراحل) كانت **بلا أي
اختبار إطلاقاً** رغم مساسها بالهوية وأرقام البطاقات — لم تُفكَّك دون شبكة أمان.
بُنيت `MemberExcelTemplateServiceTest` (4 اختبارات، مستقرة عبر تكرار التشغيل)
تغطي المسار الأساسي: صف رئيسي صالح، عمود إجباري مفقود، تكرار داخل الملف،
ورقة بيانات فارغة.

**أثناء بناء أول اختبار، انكشفت علّة حقيقية لا افتراضية:** وُجد تطبيقان
متناقضان لنفس الفحص "هل هذا الصف تابع؟":
- `isDependentRow` يتعامل مع رقم بطاقة فارغ بشكل صحيح (فارغ = رئيسي).
- لكن `parseAndCreateMember` كانت تُعيد حساب نفس الفحص بمنطق معكوس
  (`!isPrincipalCardNumber(excelCardNumber)` مباشرة، حيث
  `isPrincipalCardNumber(null)` تُعيد `false`، فتُصبح النتيجة `true` = "تابع").

**الأثر الفعلي:** أي ملف Excel بلا عمود `card_number` (الحالة الأبسط
والمسموحة رسمياً — العمود الإجباري الوحيد هو `full_name`) كان يُسقط **كل
صف رئيسي** بـ`NullPointerException` صامت (يُلتقَط ويُحتسَب "مرفوض" دون
ظهور السبب الحقيقي للمستخدم). أُصلحت باستخلاص مصدر حقيقة واحد
(`isDependentCardNumber`) يستخدمه كلا الموقعين، بدل نسختين متعارضتين.

**التحقق:** 4/4 اختبارات جديدة مستقرة (3 تكرارات تشغيل متتالية)، 56/56 اختباراً
عبر موديول الأعضاء كاملاً بعد الإصلاح، صفر انحدار.

**ما تبقّى مفتوحاً عمداً:** الاختبارات الحالية تغطي المسار الأساسي (صفوف
رئيسيين) فقط — منطق ربط التابعين متعدد المراحل (المطابقة التقريبية لجهة
العمل، استخراج رقم البطاقة الأساسي، استنتاج صلة القرابة) **غير مُختبَر بعد**
ويجب تمديد هذا الملف قبل أي محاولة لتفكيك تلك الأجزاء تحديداً. تفكيك الدالة
نفسها إلى مراحل واضحة المسؤولية (PRE-PASS / PASS-1 / PASS-2) لم يُنفَّذ في
هذه الجولة — الأولوية كانت شبكة الأمان أولاً، وهذا الآن ممكن بأمان أكبر.

---

## 9. معيار "القسم مغلق بالكامل"

- [x] محور واحد للسقف عبر كل نقاط القراءة والتحقق (Phase 1)
- [x] لا تحميل كيانات غير محدود في مسار حرج (Phase 1: الأهلية العائلية)
- [x] اختبار وحدوي لكل دالة عامة جديدة، موثَّق بجملة توضح *لماذا* لا *ماذا*
- [x] عزل جهة العمل من مصدر واحد لا نسخة يدوية (Phase 2 بند 2)
- [x] قرار صريح بشأن تفرّد الرقم الوطني (Phase 2 بند 3) — محسوم: لا تفرّد، حُذف الفخ الكودي الوحيد
- [x] `kinshipVerified` يُهيَّأ بشكل صحيح عبر الـbuilder (Phase 2 بند 5)
- [x] حذف `MemberImportController` الميت (331 سطراً، صفر مُستدعٍ) — Phase 6
- [x] اختبارات توصيف لـ`importFromExcel` + إصلاح علّة تعارض تعريف "صف تابع" المكتشَفة أثناءها — Phase 6
- [x] `civilId` — حُذف بالكامل من نطاق قسم المستفيدين (Phase 2 بند 4): 4 دوال ميتة في `MemberRepository`، توحيد `nationalNumber` كفلتر بحث وحيد في `UnifiedMemberService`/`Controller`، تصحيح خطأ `REQUIRED_FIELDS` وخطأ مقارنة بلا `cb.lower` اكتُشفا أثناء التوحيد. **لم يُمس** خارج القسم (`ClaimRepository`/`preauthorization`/تقارير المزوّدين — ~12 ملفاً، بقرار صريح مؤجَّل لجولة مستقلة بإذن منفصل)
- [x] تفكيك `importFromExcel` (~600 سطر) إلى PRE-PASS/PASS-1/PASS-2 بكائن حالة داخلي واحد (`ImportContext`) — Phase 6، بعد تمديد اختبارات التوصيف لتغطية مسارات ربط التابعين متعددة المراحل
- [x] توحيد المنطق المكرر بين نموذجَي الواجهة (Phase 3) — الدمج الكامل للمكوّنَين متروك عمداً (فروق UX جوهرية، ليس تكراراً)؛ `UnifiedMemberView.jsx` (1284 سطراً/26 `useState`) فُكِّك إلى مكونات تاب مستقلة + hook للسجل الطبي + مكوّن Chip حالة موحّد (كان مكرراً حرفياً مرتين) — علّة حقيقية اكتُشفت وأُصلحت كأثر جانبي: قائمة تغيير الحالة كانت محصورة داخل تاب واحد فلا تعمل من التاب الآخر
- [x] عدّاد "مرات الاستخدام" بلا N+1 (Phase 4) — واستُصلحت مضاعفة عدّ بعد التصحيح كأثر جانبي
- [x] تأكيد عدم وجود محور سقف ثالث في `settlement`/`report` (Phase 5) — لا يوجد؛ كلاهما يخص رصيد الدفع للمزوّد فقط. حُذفت بقية كود ميتة من الإصلاح الأصلي (`sumApprovedAmountsByMemberAndYearExcludingClaim`، صفر مُستدعٍ)

**القسم مغلق بالكامل ضمن النطاق المتفق عليه.** المتبقي خارج النطاق بقرار صريح: `civilId` في 12 ملفاً خارج قسم المستفيدين (يحتاج إذناً منفصلاً لتعديل `claim`/`preauthorization`/`provider`)، ودمج نموذجَي الإنشاء/التعديل (قرار UX، ليس ديناً تقنياً).
