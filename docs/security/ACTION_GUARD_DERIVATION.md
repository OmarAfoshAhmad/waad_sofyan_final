# اشتقاق حراسة الأزرار من صلاحيات الخادم (P1.2)

التاريخ: 2026-09-12  
القاعدة: زرٌ «حساس» = داخل شاشة تُفتح بصلاحية `*_VIEW` لكنه يستدعي endpoint يحتاج صلاحية أعلى (`*_CREATE / *_MANAGE / *_DELETE / *_APPROVE / *_REVIEW / *_POST / DANGER_ZONE_EXECUTE`). يُلفّ بـ`PermissionGuard` الموجود بـ`requiredPermissions` المطابقة **حرفياً** لما يفحصه الخادم. الخادم يبقى الحَكَم؛ هذا UX فقط.

شرطان ملزمان: لا نخفي زراً يسمح به الخادم للمستخدم، ولا نقرأ اسم الدور — `user.permissions` فقط.

## حقيقتان عن الوضع الحالي

1. `PermissionGuard` يدعم `requiredPermission(s)` من `user.permissions` — يُستعمل كما هو، لا مكوّن جديد.
2. الـprops ‏`requiredRole` و`resource`/`action` **غير معرّفة** فيه ويتجاهلها: الأغلفة الموجودة في التسويات (`requiredRole={['SUPER_ADMIN','FINANCE_MANAGER','INSURANCE_ADMIN','ACCOUNTANT']}` — دوران منها لا وجود لهما) تُرجع الأبناء لأي مستخدم مصادَق. حراسة وهمية تُزال.

## دليل حرّاس الوصول → الصلاحية

| الحارس | الصلاحية |
|---|---|
| `claimAccessGuard.canEdit / canCreateFromVisit` | `CLAIM_CREATE` |
| `claimAccessGuard.canReview` | `CLAIM_REVIEW` |
| `claimAccessGuard.canApprove` | `CLAIM_APPROVE` |
| `claimAccessGuard.canReverse` | `CLAIM_REVERSE` |
| `claimAccessGuard.canHardDelete` | `DANGER_ZONE_EXECUTE` |
| `preAuthAccessGuard.canCreate` | `PREAUTH_CREATE` |
| `preAuthAccessGuard.canReview` | `PREAUTH_REVIEW` |
| `preAuthAccessGuard.canApprove` | `PREAUTH_APPROVE` |
| `preAuthAccessGuard.canCancel` | `PREAUTH_CANCEL` |
| `preAuthAccessGuard.canDelete` | `PREAUTH_DELETE` |
| `providerContractAccessGuard.canManage*` | `CONTRACT_MANAGE` |

## الجدول — الأولوية 1: المطالبات والدفعات والموافقات

| الصفحة | الإجراء (استدعاء الخدمة) | الـendpoint | صلاحية الخادم | الحارس الحالي | `requiredPermissions` المطلوبة |
|---|---|---|---|---|---|
| `ClaimBatchEntry` | `claimBatchesService.openOrGetBatch` | `POST /claim-batches/current` | `CLAIM_CREATE` | لا شيء | `['CLAIM_CREATE']` |
| `ClaimBatchEntry` | `claimsService.createDirectEntry` / `saveDraft` / `deleteDraft` | `POST /claims/direct-entry`, `POST|DELETE /claims/draft` | `CLAIM_CREATE` | لا شيء | `['CLAIM_CREATE']` |
| `ClaimBatchEntry` | `claimsService.submit` / `update` / `uploadAttachment` | `POST /claims/{id}/submit`, `PUT /claims/{id}/data`, `POST /claims/{id}/attachments` | `CLAIM_CREATE` | لا شيء | `['CLAIM_CREATE']` |
| `ClaimBatchEntry` | `claimsService.remove` | `DELETE /claims/{id}` | `CLAIM_CREATE` | لا شيء | `['CLAIM_CREATE']` |
| `ClaimBatchDetail` | `claimsService.softDelete` | `DELETE /claims/{id}` | `CLAIM_CREATE` | لا شيء | `['CLAIM_CREATE']` |
| `ClaimBatchDetail` | `claimsService.restore` | `PUT /claims/{id}/restore` | `CLAIM_REVIEW` | لا شيء | `['CLAIM_REVIEW']` |
| `ClaimBatchDetail` | `claimsService.requestCorrection` | `POST /claims/{id}/request-correction` | `CLAIM_REVERSE` | لا شيء | `['CLAIM_REVERSE']` |
| `ClaimBatchDetail` | `claimsService.hardDelete` | `DELETE /claims/{id}/hard` | `DANGER_ZONE_EXECUTE` | لا شيء | `['DANGER_ZONE_EXECUTE']` |
| `ClaimViewMedicalReview` | `claimsService.startReview` / `pauseReview` / `resumeReview` / `returnForInfo` / `reject` | `POST /claims/{id}/{start-review,review/pause,review/resume,return-for-info,reject}` | `CLAIM_REVIEW` | لا شيء | `['CLAIM_REVIEW']` |
| `ClaimViewMedicalReview` | `claimsService.approve` | `POST /claims/{id}/approve` | `CLAIM_APPROVE` | لا شيء | `['CLAIM_APPROVE']` |
| `PreApprovalsInbox` | `reviewerPreAuthService.startReview` | `POST /reviewer/preauths/{id}/start-review` | `PREAUTH_REVIEW` | لا شيء | `['PREAUTH_REVIEW']` |
| `PreApprovalsInbox` | `reviewerPreAuthService.rejectAll` | `POST /reviewer/preauths/{id}/reject` | `PREAUTH_APPROVE` | لا شيء | `['PREAUTH_APPROVE']` |
| `PreAuthReviewPage` | `startReview` / `makeLineDecision` / `requestInfo` | `…/start-review`, `…/lines/{lineId}/decision`, `…/request-info` | `PREAUTH_REVIEW` | لا شيء | `['PREAUTH_REVIEW']` |
| `PreAuthReviewPage` | `finalizeReview` / `rejectAll` | `…/finalize`, `…/reject` | `PREAUTH_APPROVE` | لا شيء | `['PREAUTH_APPROVE']` |

## الأولوية 2: التسويات والمدفوعات

| الصفحة | الإجراء | الـendpoint | صلاحية الخادم | الحارس الحالي | المطلوب |
|---|---|---|---|---|---|
| `ProviderAccountsList` | (الصفحة كلها) | `GET /provider-accounts` | `SETTLEMENT_VIEW` | `requiredRole={[…]}` **وهمي** | يُزال الغلاف؛ الشاشة محمية بالقائمة والمسار |
| `PaymentsManagement` | (الصفحة كلها) | `GET /payments/summaries` | `SETTLEMENT_VIEW` | `requiredRole={[…]}` **وهمي** | يُزال الغلاف |
| `PaymentsManagement` → `PaymentFormModal` | `paymentsService` create / update / delete | `POST|PUT|DELETE /payments` | `SETTLEMENT_MANAGE` | لا شيء | `['SETTLEMENT_MANAGE']` على أزرار الإنشاء/التعديل/الحذف |
| `ProviderAccountView` | (الصفحة كلها) | `GET /provider-accounts/…` | `SETTLEMENT_VIEW` | `resource/action` **وهمي** | يُزال |
| `ProviderAccountView` | `providerPaymentsService.createProviderInstallment` | `POST /provider-payments` | `SETTLEMENT_MANAGE` | لا شيء | `['SETTLEMENT_MANAGE']` |
| `ProviderAccountView` / `ProviderPaymentsList` | `providerAccountsService.recalculateBalance` | `POST /provider-accounts/by-provider/{id}/recalculate-balance` | `SETTLEMENT_MANAGE` **و** `DANGER_ZONE_EXECUTE` | لا شيء | `['SETTLEMENT_MANAGE','DANGER_ZONE_EXECUTE']` |
| `ProviderPaymentsList` | `providerAccountsService.recalculateAllBalances` | `POST /provider-accounts/recalculate-all-balances` | `SETTLEMENT_MANAGE` **و** `DANGER_ZONE_EXECUTE` | لا شيء | `['SETTLEMENT_MANAGE','DANGER_ZONE_EXECUTE']` |
| `reconciliation/*` | `reconciliationService.adjust` / `reconcileByProvider` | `POST /provider-accounts/reconciliation/by-provider/{id}/adjust` | `SETTLEMENT_MANAGE` | لا شيء | `['SETTLEMENT_MANAGE']` |
| `reconciliation/NewProviderPaymentDialog` | إنشاء/ترحيل/عكس دفعة | `POST /provider-payments`, `…/{id}/post`, `…/{id}/reverse` | `SETTLEMENT_MANAGE` | لا شيء | `['SETTLEMENT_MANAGE']` |

## الأولوية 3: مقدمو الخدمة والعقود — **محروسة فعلاً**

`ProvidersList` (12 حارساً: `PROVIDER_MANAGE`, `PROVIDER_MANAGE+DANGER_ZONE_EXECUTE`, `PROVIDER_STANDARD_SERVICES_MANAGE`, `PRICE_LIST_IMPORT`)، `ProviderContractsList` (5)، `ProviderContractView` (2) — كلها `requiredPermission(s)` مطابقة للخادم. لا عمل. **فجوة واحدة:** `PriceListSessionsPage` — زر «ترحيل إلى العقد» يستدعي `…/post-to-contract` (`PRICE_LIST_POST`) بلا حارس، بينما الشاشة تُفتح بـ`PRICE_LIST_IMPORT` **أو** `PRICE_LIST_POST` → يحتاج `['PRICE_LIST_POST']`.

## الأولوية 4: المستفيدون وجهات العمل — **محروسة فعلاً**

- `EmployersList`: 6 حرّاس `EMPLOYER_MANAGE` + `BENEFIT_POLICY_VIEW`.
- المستفيدون: `memberCapabilities.js` يشتق القدرات من `user.permissions` ويُختبر نصياً ضد `MemberOperationPermissions` في الخادم (`memberCapabilityPermissions.test.js`). لا عمل.

## الأولوية 5: إعدادات النظام وDanger zone

| الصفحة | الإجراء | الـendpoint | صلاحية الخادم | الحارس الحالي | المطلوب |
|---|---|---|---|---|---|
| `UsersList` | إنشاء / تبديل حالة | `POST /admin/users`, `PATCH …/toggle-status` | `USER_MANAGE` | **محروس فعلاً**: علم `canManageUsers = permissions.has('USER_MANAGE')` (الجرد الأولي عدّ `<PermissionGuard>` فقط) | لا عمل. لا يوجد زر إعادة تعيين كلمة مرور في الواجهة (endpoint فقط، `USER_MANAGE`+`SESSION_REVOKE`) |
| `RolePermissions` | حفظ قالب الدور / تجاوزات المستخدم | `PUT /admin/access-control/roles/{code}/permissions`, `…/users/{id}/permission-overrides` | `ROLE_PERMISSION_MANAGE` | لا شيء | `['ROLE_PERMISSION_MANAGE']` |
| `KinshipMismatchChecker` | fix / ignore / bulk-* | `POST /system-settings/kinship-mismatches/…` | `DANGER_ZONE_EXECUTE` | لا شيء | `['DANGER_ZONE_EXECUTE']` |
| `MemberDuplicatesResolver` | merge / reset-kinship | `POST /system-settings/member-duplicates/…` | `DANGER_ZONE_EXECUTE` | الشاشة نفسها تتطلبه (القائمة) | لا عمل — الشاشة لا تُفتح بدونه |
| `SystemSettingsPage` | حفظ الإعدادات / أعلام الميزات | `PUT /admin/system-settings`, `/admin/features` | **دور** (`hasAnyRole` / `hasRole('SUPER_ADMIN')`) | لا شيء | **لا يُشتق** — endpoints دورية؛ يُترك حتى هجرة الخادم |

## الأولوية 6: التقارير

- `ProviderReportsController` exports (`/provider/reports/*/export`): `hasRole('PROVIDER_STAFF')` — دور، لا يُشتق.
- `MedicalAuditLogController` exports: `hasAnyRole` — دور.
- تقارير المطالبات/المالية: التصدير يعمل على بيانات `GET` نفسها بلا endpoint مستقل محروس → **لا حارس** (شرطك).

## الحصيلة

| | عدد الإجراءات | الحالة |
|---|---|---|
| أولوية 1 | 14 مجموعة أزرار | **بلا حارس** — العمل الأساسي |
| أولوية 2 | 8 + 3 أغلفة وهمية تُزال | **بلا حارس فعلي** |
| أولوية 3 | 1 (`post-to-contract`) | الباقي محروس |
| أولوية 4 | 0 | محروس |
| أولوية 5 | 2 صفحتان (`RolePermissions`, `KinshipMismatchChecker`) | بلا حارس؛ `UsersList` محروس بعلم؛ `SystemSettingsPage` دوري يُترك |
| أولوية 6 | 0 | لا endpoint مستقل محروس بصلاحية |

## ما لا يدخل في P1.2

- الاختباران الموروثان (`UnifiedCoverageModalClaimContextArchitecture`، `ClaimBatchEntrySafetyArchitecture`) — سببهما منطق مطالبات من `de39dafb` لا حراسة أزرار؛ يُتابعان كبند مستقل.
- حرّاس المسارات (`isRouteGuard` بلا صلاحية، أو `resource/action`) — نفس المرض، لكن نطاقه المسارات لا الأزرار؛ بند لاحق.

## الإغلاق (2026-09-12)

| الأولوية | commit | ما حُرس |
|---|---|---|
| 1 | `2741d4b5` | مراجعة المطالبات (`CLAIM_REVIEW`/`CLAIM_APPROVE`)، تفاصيل الدفعة (`CLAIM_CREATE`/`CLAIM_REVIEW`/`CLAIM_REVERSE`/`DANGER_ZONE_EXECUTE`)، ذيل الإدخال (`CLAIM_CREATE`)، صندوق الموافقات وصفحة مراجعتها (`PREAUTH_REVIEW`/`PREAUTH_APPROVE`) |
| 2 | `51091aed` | أُزيلت 3 أغلفة وهمية؛ المدفوعات وأقساط المزوّد (`SETTLEMENT_MANAGE`)؛ إعادة الحساب (`SETTLEMENT_MANAGE` **و** `DANGER_ZONE_EXECUTE`)؛ درج التسوية = علم الترحيل **و** `SETTLEMENT_MANAGE` |
| 3, 5 | هذا الـcommit | جلسات الأسعار (`PRICE_LIST_POST` للترحيل، `PRICE_LIST_IMPORT` للحذف)، المصنّف (`PRICE_LIST_POST`)، قوالب الأدوار (`ROLE_PERMISSION_MANAGE`)، عدم تطابق القرابة (`DANGER_ZONE_EXECUTE`) |

كل حارس بـ`user.permissions` حصراً؛ لا `requiredRole` في أي صفحة مُمسوسة، ويحميه مسح مصدر في اختبارات المطالبات والتسويات. الخادم يبقى الحَكَم.

**لم يُلمس عمداً:** `SystemSettingsPage` وأعلام الميزات (endpoints دورية)، التقارير (لا endpoint تصدير مستقل محروس)، حرّاس المسارات، والاختباران الموروثان.
