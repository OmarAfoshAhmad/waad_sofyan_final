# تتبع محرك المطالبات (Claim Engine Trace)

## مسار حساب التغطية والسقوف
يبدأ تتبع استهلاك المنافع من `CoverageEngineService`، الذي يقوم بحساب ما إذا كان يحق للمشترك الحصول على الخدمة أم لا، وبأي سقف.

### المسار المتبع في الكود:
1. **Controller:** `CoverageEngineController.bulkCheck` أو عبر معالجة مطالبة فعلية في `ClaimService`.
2. **CoverageEngineService.computeBucketUsage:** يتم استدعاؤها لتقييم سقف الاستهلاك.
3. **البحث عن السقوف (Limits Lookup):**
   - في السطر 221 من `CoverageEngineService.java` يتم تمرير `List<CoverageLimitSnapshot> bucketLimits`.
   - في السطر 236 يوجد تعليق برمجي خطير يوضح التحول المعماري: 
     ```java
     // Full bucket cutover: an unlinked rule has no usage ceiling. 
     // Never fall back to the retired amount_limit/times_limit columns on benefit_policy_rules.
     return new UsageComputation(ZERO, null, null);
     ```
   - هذا يثبت أن محرك المطالبات (Claim Engine) **يتجاهل تماماً الحقول القديمة** `amount_limit` المتواجدة في جدول `benefit_policy_rules`. المحرك يقرأ فقط من الأوعية (Buckets).

4. **الخصم المالي (Ledger Write):**
   - إذا تم الموافقة، يتم خصم القيمة عبر `benefit_bucket_consumptions` (Ledger-based). الخصم يرتبط برقم الوعاء `bucket_id` وليس `rule_id`.

## إثبات التضارب المالي (Double Deduction أو التجاهل)
بناءً على طلب السيناريو:
- إذا كان هناك تصنيف `CAT-DME` كقاعدة فردية (Rule) ولها سقف 1500 (مسجل في حقل `amount_limit` الخاص بـ `BenefitPolicyRule`).
- ونفس التصنيف موجود ضمن مجموعة `G-CAT026` بسقف 1500 (مسجل كوعاء `BenefitLimitBucket`).
- **النتيجة من المحرك:**
  - المحرك سيقرأ *فقط* الوعاء المرتبط بالمجموعة (1500) لوجود `BenefitRuleBucket` يربطهما، لأن المحرك يتجاهل `amount_limit` الخاص بالقاعدة كما هو موضح في سطر 236.
  - إذا تم إنشاء مطالبة بـ 1000 د.ل، المحرك سيخصم 1000 من سقف مجموعة `G-CAT026`. سيبدو للمستخدم أن سقف القاعدة المستقلة (1500) لم يتأثر (STATE_EXISTS_BUT_ENGINE_IGNORES_ONE_SIDE).

## الخلاصة
* **مصدر الحقيقة لنسبة التغطية (Coverage Percentage)**: الكيان `BenefitPolicyRule`.
* **مصدر الحقيقة للسقف المالي (Amount Limit)**: الكيان `BenefitLimitBucket` فقط، في حين يعتبر `BenefitPolicyRule.amountLimit` حقلاً يتيماً متقادماً (Legacy) من منظور المحرك، على الرغم من أن الشاشات وبعض أجزاء الكود قد تستمر في عرضه، مما يسبب خلطاً شديداً.
* المحرك لا يخلق الخصم المزدوج (Double Deduction) في حد ذاته إذا كانت القاعدة هي نفسها؛ ولكنه يختار وعاء المجموعة ويتجاهل سقف القاعدة الفردي.
