# تحليل واجهات برمجة التطبيقات (API Map) لنظام المنافع

## نظرة عامة
يتم إدارة هيكلة المنافع عبر متحكمين رئيسيين:
1. `BenefitPolicyRuleController`: لإدارة القواعد المستقلة (نسبة التغطية والتصنيف).
2. `BenefitStructureController`: لإدارة المجموعات، الأوعية المالية، والروابط بين القواعد والأوعية.

## مسارات الـ API (Endpoints)

### 1. `BenefitPolicyRuleController`
| Method | Endpoint | العملية | Validation | المخاطر |
|--------|----------|---------|------------|---------|
| POST | `/api/v1/benefit-policies/{policyId}/rules` | إنشاء قاعدة مستقلة لتصنيف | `uq_bpr_policy_category_context_active` (قاعدة بيانات) تمنع التكرار البسيط. | يمكن إنشاء قاعدة مستقلة لتصنيف حتى وإن كان موجوداً داخل مجموعة. |
| PUT | `/api/v1/benefit-policies/{policyId}/rules/{ruleId}` | تعديل القاعدة (نسبة التغطية، إلخ) | لا يسمح بتغيير التصنيف بعد إنشائه. | - |
| GET | `/api/v1/benefit-policies/{policyId}/rules` | جلب جميع القواعد | - | ترجع كافة القواعد سواء كانت مستقلة أو ضمن مجموعة. |
| POST | `/api/v1/benefit-policies/{policyId}/rules/bulk` | إنشاء قواعد متعددة دفعة واحدة | - | يمكنها تجاوز واجهة المستخدم وإدخال بيانات متعارضة. |

### 2. `BenefitStructureController`
| Method | Endpoint | العملية | Validation | المخاطر |
|--------|----------|---------|------------|---------|
| GET | `/api/v1/benefit-policies/{policyId}/structure` | جلب هيكلة المجموعات والأوعية | - | - |
| POST | `/api/v1/benefit-policies/{policyId}/structure/groups` | إنشاء مجموعة جديدة | `UNIQUE(policy_id, code)` | لا تتحقق مما إذا كانت المجموعة فارغة أو بلا وعاء. |
| POST | `/api/v1/benefit-policies/{policyId}/structure/buckets` | إنشاء وعاء لمجموعة | - | يمكن إنشاء وعاء بلا ربط. |
| POST | `/api/v1/benefit-policies/{policyId}/structure/rules/{ruleId}/buckets` | ربط قاعدة بوعاء | يمنع التكرار المباشر `uq_benefit_rule_bucket` | يسمح بربط القاعدة بأكثر من وعاء مختلف (لا يوجد Exclusive Constraint). |

## اختبار التزامن (Concurrency & Race Conditions)
- **إنشاء قاعدة مكررة**: قاعدة البيانات تحتوي على `uq_bpr_policy_category_context_active` مما يمنع طلبين متزامنين (Race Condition) من إنشاء نفس التصنيف الحر مرتين لنفس الوثيقة (سينتهي أحدهما بـ DataIntegrityViolationException).
- **إضافة تصنيف لمجموعة وهو موجود كمستقل**: الـ Backend والـ Database لا تمنع ذلك. لأن المجموعة ترتبط بالقاعدة ولا تمنع وجود القاعدة ككيان فردي في الجدول.
- **ربط القاعدة بأكثر من وعاء**: لا توجد حماية تمنع إرسال طلب `POST /rules/{ruleId}/buckets` لوعاء `A` ثم لوعاء `B` لنفس القاعدة. الجدول الوسيط سيقبل الاثنين.

## الخلاصات
* لا توجد طبقة تحقق (Validation) قوية في `Service Layer` تمنع إضافة تصنيف في مجموعة إذا كان التصنيف موجوداً بصورة مستقلة، أو العكس.
* الـ API مصمم بطريقة "أضف ما تشاء ثم اربطه بما تشاء"، وهذا يتماشى مع المعمارية المفككة، لكنه يتعارض مع المنطق الحصري (X-OR) المطلوب من مالك النظام.
* إرسال `amountLimit` عبر `POST /rules` أثناء إنشاء القاعدة ينشئ سقفاً فردياً. لا يوجد ما يمنع ربط هذه القاعدة لاحقاً بوعاء مشترك عبر `POST /buckets`، مما يُفضي إلى وجود سقفين معاً.
