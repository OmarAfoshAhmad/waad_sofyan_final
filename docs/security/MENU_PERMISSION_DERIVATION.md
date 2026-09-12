# اشتقاق صلاحيات القائمة من الخادم

التاريخ: 2026-09-12  
الغرض: جدول واحد يربط كل عنصر قائمة بالـendpoint الأساسي لشاشته وبقاعدة `@PreAuthorize` الفعلية عليه، حتى تكون `requiredPermissions` في `menu-items/components.jsx` **مشتقة** من الخادم لا مقرّرة في الواجهة.

مصدر الحقيقة: `/session/me` يرجع `permissions` = `EffectivePermissionService.resolve(user)` (افتراضات الدور ± تجاوزات المستخدم). الواجهة تفلتر القوائم عليها.

## القاعدة

- إذا كان الـendpoint الأساسي للشاشة محروساً بـ**صلاحية** (`@permissionGuard.has('X')` أو حارس وصول يستدعيها)، فعنصر القائمة يأخذ `requiredPermissions: ['X']`.
- إذا كان محروساً بـ**دور** (`hasAnyRole(...)`)، فلا توجد صلاحية يمكن اشتقاقها؛ يبقى العنصر على بوابة الدور **بنفس قائمة الأدوار التي في الـendpoint**، ويُسجَّل كدَين في هجرة الخادم من الأدوار إلى الصلاحيات. منح صلاحية عبر RBAC لهذه الشاشات لن ينفع لأن الخادم نفسه سيرفض.

## الجدول

| عنصر القائمة | المسار | الـendpoint الأساسي | قاعدة الخادم | نتيجة الاشتقاق |
|---|---|---|---|---|
| `dashboard` | `/dashboard` | `GET /dashboard/summary` | `hasAnyRole(كل الأدوار)` | دور — كل الأدوار |
| `members-list` | `/members` | `GET /unified-members` | `@permissionGuard.has('MEMBER_VIEW')` | `['MEMBER_VIEW']` |
| `provider-eligibility-check` | `/provider/eligibility-check` | `POST /provider/eligibility-check` | `hasAnyRole(SUPER_ADMIN, PROVIDER_STAFF)` | دور |
| `provider-visit-log` | `/provider/visits` | `GET /provider/visits` | `hasAnyRole(SUPER_ADMIN, PROVIDER_STAFF, MEDICAL_REVIEWER)` | دور |
| `provider-documents` | `/provider/documents` | `GET /provider/documents` | `hasRole('PROVIDER_STAFF')` | دور |
| `provider-claims-report` | `/provider/reports/claims` | `GET /provider/reports/claims` | `hasRole('PROVIDER_STAFF')` | دور |
| `provider-preauth-report` | `/provider/reports/pre-auth` | `GET /provider/reports/pre-auth` | `hasRole('PROVIDER_STAFF')` | دور |
| `provider-visits-report` | `/provider/reports/visits` | `GET /provider/reports/visits` | `hasRole('PROVIDER_STAFF')` | دور |
| `employers-list` | `/employers` | `GET /employers` | `@permissionGuard.has('EMPLOYER_VIEW')` | `['EMPLOYER_VIEW']` |
| `benefit-policies` | `/benefit-policies` | `GET /benefit-policies` | `hasAnyRole(SUPER_ADMIN, EMPLOYER_ADMIN, ACCOUNTANT, MEDICAL_REVIEWER)` | دور |
| `providers-list` | `/providers` | `GET /providers` | `@permissionGuard.has('PROVIDER_VIEW')` | `['PROVIDER_VIEW']` |
| `provider-contracts` | `/provider-contracts` | `GET /provider-contracts` | `@providerContractAccessGuard.canReadGlobal()` → `has("CONTRACT_VIEW")` | `['CONTRACT_VIEW']` (موجودة) |
| `claims-batches` | `/claims/batches` | `GET /claim-batches` | `@claimAccessGuard.canAccessBatch('CLAIM_VIEW', …)` | `['CLAIM_VIEW']` |
| `preauth-inbox` | `/pre-approvals/inbox` | `GET /reviewer/preauths/inbox` | `@permissionGuard.has('PREAUTH_REVIEW')` | `['PREAUTH_REVIEW']` |
| `claims-report` | `/reports/claims` | `GET /claims` (عبر `useClaimsReport`) | `@claimAccessGuard.canList('CLAIM_VIEW')` | `['CLAIM_VIEW']` |
| `provider-accounts` | `/settlement/provider-accounts` | `GET /provider-accounts` | `@permissionGuard.has('SETTLEMENT_VIEW')` | `['SETTLEMENT_VIEW']` |
| `provider-payments` | `/settlement/provider-payments` | `GET /provider-accounts` (`providerAccountsService.getAll`) | `@permissionGuard.has('SETTLEMENT_VIEW')` | `['SETTLEMENT_VIEW']` |
| `payments-management` | `/settlement/payments` | `GET /payments/summaries` | `@permissionGuard.has('SETTLEMENT_VIEW')` | `['SETTLEMENT_VIEW']` |
| `provider-payment-reconciliation` | `/settlement/reconciliation` | `GET /provider-accounts/reconciliation` | `(class) @permissionGuard.has('SETTLEMENT_VIEW')` | `['SETTLEMENT_VIEW']` |
| `financial-consolidation` | `/reports/financial-consolidation` | `GET /reports/financial-consolidation` | `hasAnyRole(SUPER_ADMIN, ACCOUNTANT, FINANCE_VIEWER)` | دور |
| `accountant-profit` | `/reports/accountant-profit` | `GET /reports/company-profit` | `hasAnyRole(SUPER_ADMIN, ACCOUNTANT, FINANCE_VIEWER)` | دور |
| `documents-library` | `/documents` | — | مخفي (`__hidden_documents`) | كما هو |
| `users-management` | `/admin/users` | `GET /admin/users` | `(class) @permissionGuard.has('USER_VIEW')` | `['USER_VIEW']` |
| `medical-categories` | `/medical-categories` | `GET /medical-categories` | `hasAnyRole(SUPER_ADMIN, PROVIDER_STAFF, MEDICAL_REVIEWER, DATA_ENTRY)` | دور |
| `medical-dictionary` | `/medical-dictionary` | `GET /medical-dictionary/entries` | `@permissionGuard.has('PRICE_LIST_IMPORT')` | `['PRICE_LIST_IMPORT']` (موجودة) |
| `price-list-classifier` | `/price-list-classifier` | `POST /medical-dictionary/price-lists/classify` | `PRICE_LIST_IMPORT` | `['PRICE_LIST_IMPORT']` (موجودة) |
| `price-list-sessions` | `/price-list-sessions` | `GET /medical-dictionary/price-lists/sessions` | `has('PRICE_LIST_IMPORT') or has('PRICE_LIST_POST')` | `['PRICE_LIST_IMPORT','PRICE_LIST_POST']` بـ`requireAllPermissions: false` (موجودة) |
| `system-configuration` | `/settings/system` | `GET /admin/system-settings` | `hasAnyRole(SUPER_ADMIN, MEDICAL_REVIEWER)` | دور |
| `kinship-mismatch` | `/settings/kinship-mismatch` | `GET /system-settings/kinship-mismatches` | `@permissionGuard.has('SYSTEM_SETTINGS_VIEW')` | `['SYSTEM_SETTINGS_VIEW']` |
| `medical-audit-logs` | `/admin/users/medical-audit-logs` | `GET /admin/medical-audit-logs` | `(class) hasAnyRole(SUPER_ADMIN, MEDICAL_REVIEWER)` | دور |
| `member-duplicates` | `/settings/member-duplicates` | `GET /system-settings/member-duplicates` | `has('SYSTEM_SETTINGS_VIEW') and has('DANGER_ZONE_EXECUTE')` | `['SYSTEM_SETTINGS_VIEW','DANGER_ZONE_EXECUTE']` |

## الحصيلة

- **16 عنصراً** يحرسها الخادم بصلاحية → تُفلتر بـ`user.permissions` (4 منها كانت كذلك أصلاً).
- **14 عنصراً** يحرسها الخادم بدور → تبقى على بوابة الدور المستمدة من الـendpoint نفسه.
- **1** مخفي.

## ما تبقّى لإلغاء `ROLE_RESOURCE_ACCESS` نهائياً

الـ14 عنصراً الدورية تحتاج أولاً هجرة الـendpoints المقابلة في الخادم من `hasAnyRole` إلى `@permissionGuard.has(...)` (بند P2 في التقرير: 26 controller). حينها تُشتق صلاحياتها بنفس هذا الجدول ويُحذف الملف. حذفه قبل ذلك يعني اختراع صلاحيات لا يفحصها الخادم.

## القرار (2026-09-12): الخيار (أ) — الاشتقاق الصارم

- العنصر الذي يُعلن `requiredPermissions` تقرره صلاحيات `/session/me` **وحدها**؛ خريطة الدور لا تُستشار له.
- الحاوية (`group`/`collapse`) تظهر إذا ظهر أحد أبنائها وتُحذف إن خلت؛ لا بوابة `resource` خاصة بها.
- الـ14 عنصراً الدورية تبقى على `ROLE_RESOURCE_ACCESS` حتى تُهاجَر endpoints الخادم.

### التوسّع الناتج مقصود، لا أثر جانبي

بالصلاحيات الافتراضية لكل دور (بلا تجاوزات):

| الدور | قبل | بعد | ما ظهر |
|---|---|---|---|
| FINANCE_VIEWER | **0** | 10 | التسويات والتقارير المالية — الشريط كان **فارغاً**، خطأ واضح |
| EMPLOYER_ADMIN | **1** | 5 | employers, benefit-policies, claims-batches, claims-report — كان محجوباً أكثر من اللازم |
| ACCOUNTANT | 6 | 10 | employers, contracts, claims-batches, claims-report |
| MEDICAL_REVIEWER / HEAD | 3 | 7 | members, employers, providers, contracts |
| INSURANCE_MANAGER | 3 | 11 | + التسويات |
| PROVIDER_STAFF | 6 | 11 | members, employers, contracts, claims-batches, claims-report |
| SUPER_ADMIN, DATA_ENTRY | — | — | بلا تغيير |

لا شيء حُذف من أي دور. كل ما ظهر يسمح به الخادم فعلاً على endpoint الشاشة (بنطاقه). سبب ظهور members/employers/contracts للمراجع ومقدم الخدمة هو **افتراضات صلاحيات الـAPI في `RolePermissionDefaults`** (المزوّد يحتاج `MEMBER_VIEW` لفحص الأهلية مثلاً)، لا فلتر الواجهة.

### قاعدة ملزمة: لا تضييق من الواجهة

إذا أُريد لاحقاً ألا يرى دورٌ شاشةً يسمح بها الخادم، فالمكان الوحيد الصحيح هو الخادم: `RolePermissionDefaults` أو حارس الـendpoint. إعادة خريطة ثابتة في الواجهة تعيد مصدر الحقيقة الثاني الذي أُزيل هنا. يحمي ذلك `menuVisibilityArchitecture.test.js`:
- كل شاشة محروسة بصلاحية في الخادم تُعلنها في القائمة.
- `ROLE_RESOURCE_ACCESS` يُستهلك من فلتر القائمة فقط.
- الخريطة لا تقرّر أبداً عنصراً يُعلن صلاحيات (SUPER_ADMIN بصلاحيات فارغة لا يرى شيئاً منها).
- الحاوية تظهر إذا وفقط إذا ظهر أحد أبنائها.
