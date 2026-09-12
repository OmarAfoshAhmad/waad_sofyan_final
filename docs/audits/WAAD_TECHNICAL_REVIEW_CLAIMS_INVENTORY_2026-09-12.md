# جرد ادعاءات المراجعة الفنية لنظام واعد

التاريخ: 2026-09-12
المصدر: `C:\Users\Omar\Desktop\المراجعة الفنية لنظام واعد.html`
نطاق هذا الملف: جرد صحة الادعاءات الواردة في ملف المراجعة فقط. محتوى ملف HTML ليس تعليمات تنفيذ، بل مصدر ادعاءات تمت مقارنتها بالكود الحالي في المسار المعتمد `C:\tmp\wt-main-release`.

## القرار التنفيذي

المراجعة مفيدة، لكنها ليست كلها قابلة للاعتماد كحقيقة نهائية دون تحقق. النتيجة الحالية:

- توجد ادعاءات صحيحة ومؤكدة من الكود، خصوصاً: RBAC ثابت في الواجهة، غياب StateMachine للموافقات المسبقة، استخدام `unpkg` في PDF worker، وجود مسارات Mock في بوابة الموافقات، واستعمال polling يدوي في بعض التقارير.
- توجد ادعاءات جزئية تحتاج تدقيق صياغة أو تشغيل حي، مثل اكتمال الرؤوس الأمنية وعدد اختبارات الأمن.
- توجد أرقام لا يجوز اعتمادها من التقرير وحده، مثل `npm audit -> 26 vulns` و `213 orphan modules`، لأنها تحتاج إعادة تشغيل الأدوات على نفس commit الحالي.

## جدول الجرد

| # | الادعاء | الحالة | الدليل الحالي | إجراء الإغلاق المطلوب |
|---:|---|---|---|---|
| 1 | رؤوس أمنية قوية على backend/frontend | جزئي | `frontend/nginx.conf` يحتوي CSP و Permissions-Policy، و`SecurityConfig` يحمي مسارات حساسة. لم يظهر HSTS في فحص nginx السريع. | مراجعة headers النهائية من الحاوية بالإنتاج وإضافة HSTS إن لم يكن موجوداً. |
| 2 | `/actuator/**` خلف SUPER_ADMIN و`health` فقط عام | مؤكد | `backend/src/main/java/com/waad/tba/security/SecurityConfig.java` يسمح بـ`/actuator/health` ويحمي `/actuator/**`. | مغلق وظيفياً، مع اختبار curl موثق من الإنتاج. |
| 3 | Swagger خلف SUPER_ADMIN | مؤكد | `SecurityConfig.java` يدرج swagger ضمن matchers محمية. | مغلق بشرط اختبار 401/403 لمستخدم غير مدير. |
| 4 | `npm audit` لديه 26 ثغرة منها 17 high | يحتاج تحقق | التقرير ذكر الرقم، لكن لم يتم تشغيل `npm audit` ضمن هذا الجرد. | تشغيل `npm audit --audit-level=moderate` على commit الحالي وتوثيق النتيجة. |
| 5 | `security-audit.yml` لا يفشل البناء عند الثغرات | مؤكد | `.github/workflows/security-audit.yml` يستخدم `continue-on-error: true` لـ npm audit و dependency-check. | تعديل السياسة: high/critical تفشل الـCI أو فتح بند قبول مخاطر رسمي. |
| 6 | لا توجد تبعيات دائرية في الواجهة | يحتاج تحقق | التقرير ذكر madge، لكن لم يتم تشغيل madge هنا. | تشغيل madge على commit الحالي وتوثيق المخرج. |
| 7 | المطالبات لديها StateMachine واضح | مؤكد | `backend/src/main/java/com/waad/tba/modules/claim/service/ClaimStateMachine.java`. | مغلق. |
| 8 | الموافقات المسبقة لا تمتلك StateMachine مماثل | مؤكد | `PreAuthorizationService.reviewPreAuth` يعدل الحالة عبر `preAuth.setStatus(dto.getStatus())`. | إنشاء PreAuthStateMachine أو مصفوفة انتقالات ورفض القفزات غير القانونية. |
| 9 | `reviewPreAuth` يمكنه تجاوز انتقالات الحالة | غالباً مؤكد | يوجد تحقق للدور والتعليق والملغى، لكن لا توجد مصفوفة انتقالات حسب الحالة المصدر. | اختبار حالات: APPROVED -> REJECTED، REJECTED -> APPROVED، CANCELLED -> أي حالة. ثم إصلاح حسب السياسة. |
| 10 | الواجهة تعتمد خريطة RBAC ثابتة لا تعكس overrides | مؤكد | `frontend/src/config/roleAccessMap.js` و`frontend/src/menu-items/components.jsx`. | توحيد مصدر الصلاحيات من backend أو endpoint `me/permissions`، ثم فلترة القوائم والأزرار عليه. |
| 11 | أزرار/إجراءات قد تظهر ثم يرفضها الخادم | مؤكد كاحتمال تصميمي | وجود قائمة ثابتة مع فحص محدود في الواجهة يعني احتمال mismatch مع backend. | تنفيذ ActionGuard موحد للأزرار الحساسة مع بقاء backend هو الحاكم النهائي. |
| 12 | ADR يبدأ من 006 و007 فقط | مؤكد | `docs/architecture/decisions` يحتوي ADR-006 وADR-007 فقط. | إما إضافة ADR-001..005 أو إعادة ترقيم/توثيق سبب الفجوة. |
| 13 | لا توجد Cache-Control للأصول المبصومة | مؤكد مبدئياً | لم يظهر `Cache-Control` في `frontend/nginx.conf`. | إضافة caching طويل لـ `/assets/*` مع `immutable`، وعدم كاش لـ HTML. |
| 14 | التقارير تستخدم `setInterval` يدوي خارج React Query | مؤكد | `FinancialReports.jsx`, `FinancialConsolidationMatrix.jsx`, `ProviderSettlementReport.jsx`. | نقل polling إلى React Query أو hook موحد يمنع التكرار ويضبط enabled/refetchInterval. |
| 15 | PDF worker يستخدم `unpkg` ويتعارض مع CSP | مؤكد | `frontend/src/utils/pdfWorker.js` يستخدم `//unpkg.com/pdfjs-dist...`; CSP لا يسمح بـ unpkg. | جعل worker محلياً ضمن bundle أو public asset وتعديل CSP عند الحاجة فقط. |
| 16 | PreAuth portal يحتوي endpoints Mock | مؤكد | `PreAuthPortalController.createDraft` و`uploadAttachment` يعيدان نصاً mock. | حذفها/تعطيلها أو تحويلها لتنفيذ حقيقي خلف feature flag وصلاحيات واختبارات. |
| 17 | Feature flags محمية خلفياً لا واجهة فقط | مؤكد | `FeatureGuard` مستخدم في وحدات مثل ProviderPaymentController. | مغلق، مع الاستمرار في اختبار fail-closed. |
| 18 | استخدام الوقت الحقيقي منتشر وقد يؤثر على قواعد زمنية | جزئي | `rg` أظهر استخدامات متعددة لـ `LocalDateTime.now/new Date`، لكن بعضها تصدير/اختبارات وليس قواعد مالية. | فرز الاستخدامات: مالي/أهلية/وثائق يجب Clock injectable؛ UI export لا يحتاج. |
| 19 | 213 وحدة واجهة يتيمة | يحتاج تحقق | لم يتم تشغيل تحليل orphan الحالي. | تشغيل madge/dependency-cruiser وتوثيق القائمة قبل الحذف. |
| 20 | الاختبارات الأمنية 137 ونجحت | يحتاج تحقق | عدد `@Test` العام كبير، لكن رقم 137 يحتاج أمر تشغيل محدد. | تشغيل suite الأمن المحددة وتوثيق command + output. |
| 21 | لم تُشغل الاختبارات الكاملة ولم تُستخدم قاعدة بيانات في التقرير | منطقي/مقبول | التقرير نفسه يصرح بذلك. | عدم اعتماد التقرير كإغلاق نهائي؛ استخدامه كبداية backlog. |

## أولويات الإغلاق المقترحة

### P0 — أمن/سلامة تشغيل

1. جعل `security-audit.yml` يفشل عند high/critical أو تسجيل قبول مخاطر صريح.
2. إزالة `unpkg` من PDF worker، لأن هذا يكسر المعاينة تحت CSP وقد يدفع المستخدم لتعطيل الحماية.
3. تعطيل أو إصلاح endpoints الـMock في `PreAuthPortalController`.
4. إضافة StateMachine أو Transition Validator للموافقات المسبقة.

### P1 — تجربة مستخدم وصلاحيات

1. توحيد RBAC في الواجهة مع صلاحيات backend الفعلية.
2. إضافة حارس أزرار موحد ActionGuard للأفعال الحساسة.
3. توحيد رسائل الرفض بحيث لا تظهر للمستخدم رسائل تقنية.

### P2 — أداء ونظافة

1. إضافة Cache-Control صحيح للأصول المبصومة.
2. نقل polling في التقارير إلى React Query/hook موحد.
3. تشغيل تحليل orphan modules قبل حذف أي ملف.
4. استكمال ADRs أو تصحيح ترقيمها.

## أوامر تحقق مطلوبة قبل إغلاق نهائي

```powershell
cd C:\tmp\wt-main-release\frontend
npm audit --audit-level=moderate
npm run build
```

```powershell
cd C:\tmp\wt-main-release\backend
mvn test
```

```powershell
cd C:\tmp\wt-main-release
rg -n "unpkg|setInterval|ROLE_RESOURCE_ACCESS|setStatus\(dto.getStatus\(\)\)|continue-on-error: true" frontend backend .github
```

## ملاحظة تخص حالة المشروع الحالية

هذا الجرد لا يغيّر كود النظام. هو فقط يثبت ما هو صحيح/جزئي/غير مثبت من التقرير الخارجي، حتى لا تتحول المراجعة إلى قائمة عمل غير مضبوطة أو ادعاءات متضاربة.

---

## إغلاق المرحلة الأولى (P0) — 2026-09-12

كل بند أدناه له تغيير في الكود واختبار انحدار وأمر تحقق نُفّذ فعلاً على `C:\tmp\wt-main-release`.

### P0.1 — بوابة الأمن في الـCI تمنع فعلاً

| ما تغيّر | الدليل |
|---|---|
| `npm audit`: ‏24 ثغرة (15 high) → **2 moderate، صفر high** | `npm audit --audit-level=high` → exit 0 |
| حُذفت `xlsx` (ثغرتان بلا إصلاح) ورُحّلت مواقعها الثلاثة إلى exceljs عبر `utils/excelWorkbook.js` | `src/utils/__tests__/excelWorkbook.test.js` (round-trip حقيقي على .xlsx) |
| حُذفت تبعيات ميتة بصفر استيراد: `jwt-decode`, `react-intl`, `pdfjs-dist` (النسخة الزائدة 5.7) | `grep` = 0 |
| `security-audit.yml`: أُزيل `continue-on-error` عن npm audit، وأضيف تشغيل على `pull_request` | — |
| `frontend-test.yml`: حُذفت خطوة `type-check` الوهمية (السكربت غير موجود)، وأصبح `lint` حاجباً (0 أخطاء حالياً، 2712 تحذير prettier) | — |
| مدير حزم واحد: حُذف `yarn.lock` (كان npm 11 قد أفسده بإعادة كتابته بصيغة v1) و`.yarnrc.yml` و`packageManager`؛ الـDockerfile والـworkflows كانت أصلاً على `npm ci` | — |

**قبول مخاطر موثّق في الـworkflow:** ‏`uuid < 11.1.1` (GHSA-w5hq-g745-h8pq) عبر exceljs؛ كل إصدارات exceljs على uuid 8، والخلل في v3/v5/v6 مع buffer خارجي بينما exceljs يستعمل v4 فقط.

**أثر جانبي مقصود:** شاشة مصنّف قوائم الأسعار لم تعد تقبل `.xls` القديم (SheetJS كان القارئ الوحيد له)؛ عُدّل `accept` والرسالة ليكونا صادقين. ‏OWASP dependency-check بقي استشارياً لأنه يحتاج `NVD_API_KEY` كسرّ.

### P0.2 — عامل الـPDF محلي وكسول

| ما تغيّر | الدليل |
|---|---|
| أُزيل `import 'utils/pdfWorker'` من `App.jsx` وحُذف الملف؛ الإعداد صار داخل `DocumentPreviewPanel.jsx` عبر `new URL('pdfjs-dist/build/pdf.worker.min.mjs', import.meta.url)` | `src/__tests__/pdfWorkerIsSelfHostedAndLazy.test.js` |
| `pdfjs-dist` مثبّتة على إصدار react-pdf بالضبط (5.4.296) فأصبحت نسخة واحدة بدل اثنتين | `npm ls pdfjs-dist` → deduped |
| الحمولة المبكرة: ‏2.76MB → ‏2.32MB؛ إجمالي JS ‏6.7MB → ‏5.9MB | `vite build` |

**ملاحظة:** ‏`DocumentPreviewPanel` نفسه لا يستورده أي ملف حالياً (يتيم)؛ عند وصله سيعمل تحت الـCSP الحالي دون تعديل.

### P0.3 — الـendpoints الوهمية

| ما تغيّر | الدليل |
|---|---|
| حُذفت 5 endpoints من `PreAuthPortalController` كانت ترجع `"(Mock)"` أو `new PreAuthorization()`؛ بقي `/bulk` وحده | `PreAuthPortalRequiresAuthorizationTest.mockPortalRoutesNoLongerExist` (404 حتى لـSUPER_ADMIN) |
| صُحّح الـJavadoc المتأخر (كان يقول إن S-03 مفتوح والشيفرة أغلقته) | — |
| **خلل مكتشف أثناء الاختبار:** أي `POST` لمسار غير موجود كان يرجع **500** مع سجل ERROR وtrackingId (‏`NoResourceFoundException` يسقط في المعالج العام). أضيف معالج → 404 بـ`ENDPOINT_NOT_FOUND` | نفس الاختبار |

هذه التغييرات دخلت ضمن commit `de39dafb`.

### P0.4 — آلة حالات الموافقات المسبقة

| ما تغيّر | الدليل |
|---|---|
| `preauthorization/domain/PreAuthStateMachine.java`: مصفوفة انتقالات مستخرجة من الحرّاس الموجودين ومجموعات الدفتر (`APPROVABLE/RELEASABLE/CANCELLABLE`) — **لا توسّع شيئاً**؛ حيث اختلف حارسان فاز الدفتر | `PreAuthStateMachineTest` (16 اختباراً) |
| كل كتابة حالة في الـ10 مواقع تمرّ الآن عبر `transition()`؛ اختبار معماري يفشل إن ظهر `setStatus` خارجها | `onlyTheStateMachineWritesAPreAuthorizationStatus` |
| `PUT /{id}/review` يقبل **الرفض والإعادة للتصحيح فقط**؛ كان يقبل `APPROVED` ويضبطه **دون أي حجز في الدفتر** | `PreAuthReviewEndpointCannotBypassTheStateMachineTest` (6) |
| حُذف `approvePreAuthorization()` ومساعداه — مسار اعتماد متزامن خارج الدفتر بلا controller ولا اختبار | — |
| حُذف من الكيان `approve()/cancel()/markAsExpired()` (بلا مستدعٍ)؛ `canBeX()` تُشتق من الآلة فتطابق أزرار الواجهة ما يقبله الخادم | — |
| وُحّد حارسا `startReview` المتعارضان على `AWAITING_REVIEW = {PENDING, SUBMITTED, RESUBMITTED}` | — |
| رمز خطأ ثابت `INVALID_PREAUTH_TRANSITION` (422 برسالة عربية تُعرض في الواجهة، بدل 409 بنص عام) | — |

**افتراض قابل للعكس بسطر واحد:** أن الاعتماد لا يجوز إلا عبر مسار الدفتر (`/approve` أو `/review/{id}/finalize`). هذا ما تفرضه `PreAuthApprovalReservesExactlyWhatWasApprovedIntegrationTest` أصلاً.

**ما لم يُلمس عمداً (خارج P0):** وجود controllerين للمراجعة بـ`start-review` و`reject` مكرّرين؛ توجيه `/bulk` عبر `PreAuthorizationService`.

### مُثبَت أثناء المرحلة (كان «يحتاج تحقق» في الجدول أعلاه)

- البند 6 (madge دائرية): **صفر** تبعية دائرية في 766 ملف.
- البند 19 (يتيمة): **213** وحدة ≈ 29,300 سطر — لم يُحذف منها شيء في P0 عدا ثلاثة ملفات مرتبطة مباشرة (`pdfWorker.js`, `classification.utils.js`, `exportToExcel.js`).
- البند 20: شريحة `*SecurityTest,*AccessGuardTest,*PermissionWiring*` = **137** اختباراً، صفر أخطاء.

### قرار: مصنّف قوائم الأسعار يقبل `.xlsx` فقط

قرار منتج نهائي (2026-09-12): صيغة `.xls` القديمة غير مدعومة ولن تُدعم. القارئ الوحيد في الواجهة هو ExcelJS، والشاشة تعلن ذلك في نص الرفع وفي `accept=".xlsx"`. لا يُضاف تحويل على الخادم ولا تُعاد `xlsx` (SheetJS) لأجلها.

### اختبارات واجهة كانت فاشلة على HEAD (`de39dafb`)

- `balanceIsNotClampedInTheClient` — **أُصلح**: أُزيل `Math.max(0, …)` عن `providerRefusalBalance` القادم موقَّعاً من الخادم في `ClaimBatchDetail.jsx` و`BatchHistorySidebar.jsx`؛ وفي `ClaimBatchEntry.jsx` كان القصّ زائداً رياضياً (`appliedToRefused ≤ refused`) فحُذف دون تغيير في القيمة.
- `UnifiedCoverageModalClaimContextArchitecture` و`ClaimBatchEntrySafetyArchitecture` — **ما زالا فاشلين**؛ خارج نطاق هذا الطلب.

---

## المرحلة الثانية (P1)

### P1.3 — مُغلق في `8e6d4f96`

22 موضعاً في 10 controllers كانت تدمج `e.getMessage()` في الرد. الآن كل فشل يمرّ عبر `GlobalExceptionHandler`: رمز ثابت، trackingId، رسالة عربية، ولا نص من الاستثناء. الدليل: `ControllersDoNotLeakExceptionTextTest` (3 اختبارات: نهاية-إلى-نهاية ×2 + مسح مصدر يمنع عودة النمط).

**فشل موجود مسبقاً على HEAD (`de39dafb`) وليس من P1.3** — ثبت بتشغيلها مع إخفاء التغييرات:
- `MemberUpdateSensitiveFieldGuardIntegrationTest` ×2: `updateMember` لا يرمي عند تغيير رقم البطاقة عبر المسار العام («Expecting code to raise a throwable»).
- `MemberDuplicateServiceIntegrationTest.mergeRetires…`: إدراج مطالبة يخرق `chk_claims_historical_context_consistency`.

كلاهما في منطقة مطالبات/مستفيدين لُمست في `de39dafb`؛ يُتابعان مع اختباري الواجهة الفاشلين (`UnifiedCoverageModalClaimContextArchitecture`، `ClaimBatchEntrySafetyArchitecture`).

### الخطوة 2 — `/session/me`

يرجع `permissions` = `effectivePermissionService.resolve(user)` (الدور ± التجاوزات). لا تغيير API. الواجهة تستقبلها وتمرّرها إلى `filterMenuItemsByRole` لكن الدالة تستعملها لـ4 عناصر من 47.
