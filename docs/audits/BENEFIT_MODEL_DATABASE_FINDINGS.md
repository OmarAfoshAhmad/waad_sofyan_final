# نتائج فحص قاعدة البيانات والمشكلات المحتملة (Database Findings)

## 1. تصنيف موجود أكثر من مرة في الوثيقة والسياق نفسه (مستقل وعضو مجموعة)
**الاستعلام SQL:**
```sql
SELECT r.benefit_policy_id, r.medical_category_id, r.encounter_type, COUNT(r.id) as occurrences,
       COUNT(b.bucket_id) as linked_buckets
FROM benefit_policy_rules r
LEFT JOIN benefit_rule_buckets b ON r.id = b.rule_id
WHERE r.deleted = false
GROUP BY r.benefit_policy_id, r.medical_category_id, r.encounter_type
HAVING COUNT(r.id) > 1;
```
**النتيجة المتوقعة والملاحظة:** 
قاعدة البيانات تمنع هذا فعلياً بفضل القيد الفريد `uq_bpr_policy_category_context_active`. لكن المنظومة لا تمنع إنشاء سقف في `amount_limit` الخاص بـ `BenefitPolicyRule` ووجود ارتباط بـ `BenefitLimitBucket` لنفس القاعدة.

## 2. قاعدة مرتبطة بأكثر من وعاء (تضارب الأوعية)
**الاستعلام SQL:**
```sql
SELECT rule_id, COUNT(bucket_id)
FROM benefit_rule_buckets
GROUP BY rule_id
HAVING COUNT(bucket_id) > 1;
```
**النتيجة المتوقعة والملاحظة:** 
هذا مسموح هيكلياً (الجدول لا يمنعه). سيُظهر أي قواعد تم ربطها بوعاءين مختلفين، مما يؤدي إلى سحب المطالبات من كلا الوعاءين، أو اختيار الوعاء الخاطئ حسب ترتيب `consumption_order`.

## 3. قاعدة بلا وعاء (يتيمة)
**الاستعلام SQL:**
```sql
SELECT r.id, r.amount_limit 
FROM benefit_policy_rules r
LEFT JOIN benefit_rule_buckets b ON r.id = b.rule_id
WHERE b.bucket_id IS NULL AND r.amount_limit IS NOT NULL AND r.deleted = false;
```
**النتيجة المتوقعة والملاحظة:**
هذا سيعرض القواعد التي تم إضافة سقف لها قديماً عبر حقل `amount_limit` ولم يتم تحويلها إلى أوعية في معمارية المجموعات الجديدة. المحرك يتجاهل سقف هذه القواعد (كما رأينا في `CoverageEngineService`).

## 4. سقف داخل القاعدة مع وجود وعاء مرتبط
**الاستعلام SQL:**
```sql
SELECT r.id as rule_id, r.amount_limit as rule_limit, lb.amount_limit as bucket_limit
FROM benefit_policy_rules r
JOIN benefit_rule_buckets rb ON r.id = rb.rule_id
JOIN benefit_limit_buckets lb ON rb.bucket_id = lb.id
WHERE r.amount_limit IS NOT NULL AND r.deleted = false;
```
**النتيجة المتوقعة والملاحظة:**
هذه هي الحالة الأبرز للتضارب (كما في صورة `CAT-DME`). قاعدة بيانات تمتلك سقفين في مكانين مختلفين لنفس التصنيف المرجعي. سيقوم المحرك بتجاهل `rule_limit` تماماً وسيطبق `bucket_limit`. الواجهة الأمامية ترتبك وتعرض القيمتين.

## 5. قواعد محذوفة منطقياً وما زالت مرتبطة (Soft Deletion Conflicts)
**الاستعلام SQL:**
```sql
SELECT r.id, rb.bucket_id
FROM benefit_policy_rules r
JOIN benefit_rule_buckets rb ON r.id = rb.rule_id
WHERE r.deleted = true;
```
**النتيجة المتوقعة والملاحظة:**
سنجد روابط لا تزال قائمة لقواعد محذوفة؛ مما قد يؤثر على حسابات مجموعات المنافع إذا كان المحرك لا يفلتر القواعد المحذوفة بدقة قبل السحب من الوعاء المشترك.

## الخلاصة
الهيكلة الحالية تسمح بتخزين بيانات متضاربة لأن `amount_limit` متواجد في مكانين، ولأن الـ `Unique Constraints` تحمي من التكرار الدقيق لسجل القاعدة، لكنها لا تحمي من "التكرار الدلالي" (سقف في القاعدة وسقف في الوعاء).
