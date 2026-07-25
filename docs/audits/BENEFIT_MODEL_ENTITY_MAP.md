# تحليل نموذج بيانات قواعد المنافع والمجموعات (Entity Map)

## نظرة عامة
تم إجراء الفحص الشامل لنموذج البيانات المرتبط بقواعد المنافع والمجموعات والأوعية، وتبين أن الهيكلة تعتمد على معمارية مفككة (Decoupled Architecture) بين **التغطية المالية (Coverage)** و**سقوف الاستهلاك (Limits/Buckets)**.

## الكيانات (Entities)

### 1. الكيان `BenefitPolicyRule` (القاعدة الأساسية)
- **وصف الكيان**: يمثل رابط التغطية بين الوثيقة (`BenefitPolicy`) والتصنيف الطبي (`MedicalCategory`).
- **وظيفة الحقول**: يحتوي على نسبة التغطية (`coveragePercent`)، والسقف الفردي المباشر (`amountLimit`)، ونوع المواجهة (`encounterType`).
- **العلاقات**:
  - `ManyToOne` مع `BenefitPolicy`.
  - `ManyToOne` مع `MedicalCategory`.
- **القيود (Database Constraints)**:
  - القيد الفريد `uq_bpr_policy_category_context_active` لمنع تكرار نفس التصنيف داخل نفس نوع المواجهة والوثيقة طالما لم يتم حذفه (`deleted = false`).
- **المشكلة المعمارية**: 
  - يحتوي على `amountLimit` الخاص به بمعزل عن الأوعية الخارجية، مما يؤسس لمشكلة "سقف داخل القاعدة مع وجود وعاء".
  - لا يحتوي على أي حقل مباشر يشير إلى "مجموعة" باستثناء الربط عبر جداول وسيطة.

### 2. الكيان `BenefitGroup` (المجموعة)
- **وصف الكيان**: يمثل "مجموعة منافع" وظيفية لغرض التجميع في الشاشة أو الإدارة، وليس لها تأثير مالي مباشر بذاتها (السقف المالي يخزن في `BenefitLimitBucket`).
- **العلاقات**:
  - `ManyToOne` مع `BenefitPolicy`.
- **الخصائص**:
  - تحتوي على وضع التجميع `aggregationMode` والذي يقبل (INDIVIDUAL, SHARED, HIERARCHICAL).

### 3. الكيان `BenefitLimitBucket` (الوعاء المالي)
- **وصف الكيان**: يخزن السقف المالي الفعلي (المالي أو العددي).
- **العلاقات**:
  - `ManyToOne` مع `BenefitGroup` (إلزامي).
- **المشكلة المعمارية**: 
  - السقف (`amountLimit`) موجود هنا، مما يعني أن المنظومة تحتوي على سقفين: واحد في القاعدة (للتصنيف المستقل) وواحد في الوعاء (للمجموعة).

### 4. الكيان `BenefitRuleBucket` (رابط القاعدة بالوعاء)
- **وصف الكيان**: جدول وسيط يربط بين `BenefitPolicyRule` و `BenefitLimitBucket`.
- **العلاقات**:
  - `ManyToOne` مع `BenefitPolicyRule`.
  - `ManyToOne` مع `BenefitLimitBucket`.
- **المشكلة المعمارية**: 
  - هذا الكيان يثبت معمارياً أن **القاعدة الواحدة يمكن أن ترتبط بأكثر من وعاء** (لا يوجد Unique Constraint يمنع تعدد الأوعية لنفس القاعدة، القيد الموجود فقط لمنع التكرار الدقيق `rule_id, bucket_id`).

## جداول الترحيل (Flyway Migrations)
- **V84__full_benefit_classification_cutover.sql**: 
  - تم فصل التغطية (Rules) عن الحدود (Buckets) بالكامل.
  - تم إبقاء حقل `amount_limit` داخل جدول `benefit_policy_rules` ولم يُحذف بالرغم من إضافة جدول `benefit_limit_buckets`، مما سمح بظهور التضارب حيث يمكن وضع سقفين مختلفين لنفس الخدمة.
  - جدول `benefit_groups` أُنشئ ككيان تنظيمي بينما الأسقف تُدار عبر `benefit_limit_buckets`.

## سياسات الحذف والمحافظة على البيانات (Orphan Removal & Cascade)
- في `BenefitPolicyRule`: 
  - الحذف المنطقي (`Soft Deletion`) مفعل عبر الحقل `deleted = false`.
- في الروابط بين `BenefitRuleBucket` و `BenefitLimitBucket`:
  - `ON DELETE RESTRICT` لبعض الجداول و `CASCADE` للوثيقة الرئيسية، مما يعني أن إزالة الوعاء مستحيل إذا كان مرتبطاً بقواعد، لكن إزالة المجموعة سيؤدي لحذف الوعاء (Cascade) وهو ما قد يسبب أيتاماً أو أخطاء.

## المخطط الهيكلي الفعلي للنموذج (Entity Map)

```text
BenefitPolicy
 ├── BenefitGroup
 │    └── BenefitLimitBucket (يحتوي على amount_limit)
 │         └── BenefitRuleBucket (علاقة ارتباط M:N)
 │              └── BenefitPolicyRule
 │
 ├── BenefitPolicyRule (يحتوي أيضاً على amount_limit و coveragePercent)
 │    └── (يمكن أن يرتبط بـ BenefitRuleBucket أو يعمل كقاعدة مستقلة)
```

## الخلاصة المتعلقة بنموذج البيانات
* المنظومة تتبع معمارية تسمح للقاعدة بأن تكون مستقلة (بسقفها الخاص) ومجموعة (بسقف الوعاء) في نفس الوقت، حيث لا يوجد قيد في قاعدة البيانات يفرض مبدأ X-OR (إما سقف هنا أو سقف هناك). 
* يمكن للمجموعة أن تحوي قاعدة واحدة، ويمكن للقاعدة أن تدخل في أكثر من مجموعة لعدم وجود قيود حصرية.
