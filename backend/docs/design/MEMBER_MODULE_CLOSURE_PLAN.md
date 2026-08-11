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
3. **قيد تفرّد على `national_number`.** حالياً فهرس فقط لا `UNIQUE`؛ قرار
   عمل مطلوب أولاً (هل يُسمح بتكرار الرقم الوطني بين مستفيدين، أم لا؟) قبل
   إضافة الهجرة — هذا `POLICY_DECISION_REQUIRED`، لا يُحسم صامتاً.
4. **حذف `civilId`.** مُعلَّم `@Deprecated` منذ فترة، ما زال في الكيان
   والبحث. يحتاج تأكيد أن `nationalNumber` غطّى كل استخدام قبل الحذف.
5. **`kinshipVerified` بلا `@Builder.Default`.** يُحفظ `NULL` عند الإنشاء
   بالـbuilder بينما بقية أعلام الكيان `false` افتراضياً — إصلاح صف واحد.

---

## 5. Phase 3 — الواجهة (مخطَّطة، لم تُنفَّذ)

- استخراج نموذج مشترك بين `UnifiedMemberCreate.jsx` (999 سطر) و
  `UnifiedMemberEdit.jsx` (850 سطر) — نفس الحقول تقريباً، صيانة مزدوجة حالياً.
- تفكيك `UnifiedMemberView.jsx` (1284 سطر / 26 `useState`) إلى مكونات فرعية
  حسب القسم (بيانات شخصية / مالية / تابعون / مستندات).

---

## 6. Phase 4 — `getServiceCoverageLimits` (مخطَّطة، لم تُنفَّذ)

مُوثَّقة صراحة داخل الكود نفسه (`MemberFinancialSummaryService.java`) كنمط
N+1 متبقٍّ عمداً غير مُصلَح في هذه الجولة: عدّاد "مرات الاستخدام" لخدمة
معيّنة ما زال يحمّل كل مطالبات العضو ويعدّ بحلقة Java. الإصلاح الصحيح
استعلام تجميعي مخصص (ربما بإعادة استخدام ما يتتبعه `CoverageEngineService`/
`BenefitBucketLimitService` لحدود المرات/الأيام أصلاً)، لا نسخة من استعلام
الأموال أعلاه — هذا مقياس عدّ لا مقياس مالي.

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

## 8. معيار "القسم مغلق بالكامل"

- [x] محور واحد للسقف عبر كل نقاط القراءة والتحقق (Phase 1)
- [x] لا تحميل كيانات غير محدود في مسار حرج (Phase 1: الأهلية العائلية)
- [x] اختبار وحدوي لكل دالة عامة جديدة، موثَّق بجملة توضح *لماذا* لا *ماذا*
- [ ] صفر كود ميت متبقٍّ (Phase 2 بند 1)
- [ ] عزل جهة العمل من مصدر واحد لا نسخة يدوية (Phase 2 بند 2)
- [ ] قرار صريح بشأن تفرّد الرقم الوطني (Phase 2 بند 3)
- [ ] لا نموذج واجهة مكرر (Phase 3)
- [ ] عدّاد "مرات الاستخدام" بلا N+1 (Phase 4)
- [ ] تأكيد عدم وجود محور سقف ثالث في التقارير (Phase 5)
