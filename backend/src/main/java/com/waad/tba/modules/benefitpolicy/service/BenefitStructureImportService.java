package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.modules.benefitpolicy.dto.BenefitStructureImportResult;
import com.waad.tba.modules.benefitpolicy.entity.*;
import com.waad.tba.modules.benefitpolicy.enums.*;
import com.waad.tba.modules.benefitpolicy.repository.*;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.enums.CategoryContext;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.claimcontext.repository.ClaimContextDefinitionRepository;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BenefitStructureImportService {
    public enum ImportMode { MERGE, REPLACE }
    private final BenefitPolicyRepository policyRepository;
    private final BenefitPolicyRuleRepository ruleRepository;
    private final MedicalCategoryRepository categoryRepository;
    private final BenefitGroupRepository groupRepository;
    private final BenefitLimitBucketRepository bucketRepository;
    private final BenefitRuleBucketRepository linkRepository;
    private final BenefitDefinitionRepository definitionRepository;
    private final ClaimContextDefinitionRepository claimContextRepository;

    public byte[] createSimplifiedTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet benefits = workbook.createSheet("المنافع");
            writeHeader(benefits, "كود التصنيف", "اسم المنفعة", "السياق", "نسبة التغطية", "نسبة التحمل",
                    "موافقة مسبقة", "السقف المالي", "حد المرات", "حد الأيام", "الفترة", "قيمة الفترة", "طريقة العد",
                    "أساس احتساب السقف", "نشط", "سياق القرار");
            Sheet groups = workbook.createSheet("المجموعات");
            writeHeader(groups, "كود المجموعة", "اسم المجموعة", "السياق", "أكواد المنافع مفصولة بفاصلة",
                    "السقف المالي", "حد المرات", "حد الأيام", "الفترة", "قيمة الفترة", "طريقة العد",
                    "أساس احتساب السقف", "نشط", "سياق القرار");
            Sheet instructions = workbook.createSheet("تعليمات");
            String[][] notes = {
                    {"الحقل", "التوضيح"},
                    {"المنافع", "أدخل كود تصنيف معتمداً لكل منفعة. اترك السقف المالي وحد المرات والأيام فارغة إذا لم يوجد سقف فردي."},
                    {"المجموعات", "ضع أكواد منفعتين أو أكثر وافصل بينها بفاصلة. المجموعة قد تكون بلا سقف."},
                    {"نمط الاستيراد", "الاستيراد من الواجهة دمج آمن: يضيف ويحدث فقط ولا يعطل بيانات غير موجودة في الملف."},
                    {"طريقة العد", "EACH_UNIT تحسب الكمية/الجلسات، وهي القيمة الموصى بها."},
                    {"أساس احتساب السقف", "ELIGIBLE_AMOUNT يحد إجمالي الخدمة المقبول قبل توزيع التحمل؛ COMPANY_SHARE يحد التزام الشركة بعد التحمل. لا تتركه غامضاً عند وجود سقف مالي."}
                    ,{"سياق القرار", "اختياري. اكتب MATERNITY أو PREGNANCY_COMPLICATIONS عند اختلاف القرار عن نوع الزيارة؛ الأدوية تصنيف منفعة وليست سياق مطالبة."}
            };
            for (int rowIndex = 0; rowIndex < notes.length; rowIndex++) {
                Row row = instructions.createRow(rowIndex);
                for (int column = 0; column < notes[rowIndex].length; column++) row.createCell(column).setCellValue(notes[rowIndex][column]);
            }
            addListValidation(benefits, 2, "OUTPATIENT", "INPATIENT", "ANY");
            addListValidation(benefits, 5, "نعم", "لا");
            addListValidation(benefits, 9, "PER_SERVICE", "PER_VISIT", "DAILY", "WEEKLY", "MONTHLY", "QUARTERLY",
                    "ANNUAL", "CUSTOM_DAYS", "CUSTOM_WEEKS", "CUSTOM_MONTHS", "CUSTOM_YEARS", "POLICY_PERIOD", "LIFETIME");
            addListValidation(benefits, 11, "EACH_UNIT", "EACH_LINE", "PER_DAY", "PER_VISIT");
            addListValidation(benefits, 12, "ELIGIBLE_AMOUNT", "COMPANY_SHARE");
            addListValidation(benefits, 13, "نعم", "لا");
            addListValidation(benefits, 14, "OUTPATIENT", "INPATIENT", "FULL_COVERAGE", "MATERNITY", "PREGNANCY_COMPLICATIONS");
            addListValidation(groups, 2, "OUTPATIENT", "INPATIENT", "ANY");
            addListValidation(groups, 7, "PER_SERVICE", "PER_VISIT", "DAILY", "WEEKLY", "MONTHLY", "QUARTERLY",
                    "ANNUAL", "CUSTOM_DAYS", "CUSTOM_WEEKS", "CUSTOM_MONTHS", "CUSTOM_YEARS", "POLICY_PERIOD", "LIFETIME");
            addListValidation(groups, 9, "EACH_UNIT", "EACH_LINE", "PER_DAY", "PER_VISIT");
            addListValidation(groups, 10, "ELIGIBLE_AMOUNT", "COMPANY_SHARE");
            addListValidation(groups, 11, "نعم", "لا");
            addListValidation(groups, 12, "OUTPATIENT", "INPATIENT", "FULL_COVERAGE", "MATERNITY", "PREGNANCY_COMPLICATIONS");
            for (Sheet sheet : List.of(benefits, groups)) {
                sheet.createFreezePane(0, 1);
                for (int i = 0; i < sheet.getRow(0).getLastCellNum(); i++) sheet.autoSizeColumn(i);
            }
            instructions.setColumnWidth(0, 22 * 256);
            instructions.setColumnWidth(1, 100 * 256);
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessRuleException("تعذر إنشاء قالب الاستيراد: " + e.getMessage());
        }
    }

    private void writeHeader(Sheet sheet, String... headers) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) row.createCell(i).setCellValue(headers[i]);
    }

    private void addListValidation(Sheet sheet, int column, String... values) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createExplicitListConstraint(values);
        CellRangeAddressList range = new CellRangeAddressList(1, 10000, column, column);
        DataValidation validation = helper.createValidation(constraint, range);
        validation.setShowErrorBox(true);
        validation.createErrorBox("قيمة غير معتمدة", "اختر قيمة من القائمة المنسدلة");
        sheet.addValidationData(validation);
    }

    @Transactional
    public BenefitStructureImportResult importWorkbook(Long policyId, MultipartFile file, boolean dryRun, ImportMode mode) {
        BenefitPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new ResourceNotFoundException("BenefitPolicy", "id", policyId));
        Parsed parsed = parse(file);
        if (!dryRun) {
            assertImportAllowed(policy);
        }
        List<String> errors = validate(policyId, parsed, mode);
        if (!errors.isEmpty() && !dryRun) {
            throw new BusinessRuleException("ملف المنافع غير صالح: " + String.join(" | ", errors));
        }
        Counter counter = estimateImpact(policyId, parsed, mode);
        if (errors.isEmpty() && !dryRun) {
            counter = new Counter();
            apply(policy, parsed, counter, mode);
        }
        return BenefitStructureImportResult.builder()
                .dryRun(dryRun).rules(parsed.rules.size()).groups(parsed.groups.size())
                .buckets(parsed.buckets.size()).links(parsed.links.size())
                .specialBenefits(parsed.specials.size()).created(counter.created).updated(counter.updated)
                .deactivated(counter.deactivated).mode(mode.name())
                .warnings(parsed.warnings).errors(errors).build();
    }

    private Counter estimateImpact(Long policyId, Parsed p, ImportMode mode) {
        Counter c = new Counter();
        for (RuleRow row : p.rules) {
            MedicalCategory category = categoryRepository.findByCode(row.categoryCode).orElse(null);
            boolean exists = category != null && ruleRepository
                    .findFirstByBenefitPolicyIdAndMedicalCategoryIdAndClaimContextCodeOrderByIdDesc(
                            policyId, category.getId(), row.claimContextCode).isPresent();
            if (exists) c.updated++; else c.created++;
        }
        for (GroupRow row : p.groups) {
            if (groupRepository.findByPolicyIdAndCodeIgnoreCase(policyId, row.code).isPresent()) c.updated++; else c.created++;
        }
        for (BucketRow row : p.buckets) {
            if (bucketRepository.findByPolicyIdAndCodeIgnoreCase(policyId, row.code).isPresent()) c.updated++; else c.created++;
        }
        if (mode == ImportMode.REPLACE) c.deactivated = countMissingConfiguration(policyId, p);
        return c;
    }

    private void assertImportAllowed(BenefitPolicy policy) {
        if (!policy.isActive()) {
            throw new BusinessRuleException("لا يمكن اعتماد الاستيراد إلا لوثيقة فعالة؛ فعّل الوثيقة أولاً أو اجعلها مسودة فعالة");
        }
        if (policy.getStatus() == BenefitPolicy.BenefitPolicyStatus.DRAFT) {
            return;
        }
        boolean emptyStructure = ruleRepository.countByBenefitPolicyId(policy.getId()) == 0
                && groupRepository.countByPolicyId(policy.getId()) == 0
                && bucketRepository.countByPolicyId(policy.getId()) == 0
                && linkRepository.countByRuleBenefitPolicyId(policy.getId()) == 0;
        if (emptyStructure && (policy.getStatus() == BenefitPolicy.BenefitPolicyStatus.ACTIVE
                || policy.getStatus() == BenefitPolicy.BenefitPolicyStatus.SUSPENDED)) {
            return;
        }
        throw new BusinessRuleException("لا يمكن اعتماد الاستيراد إلا لمسودة، أو كتهيئة أولى لوثيقة نشطة/موقوفة لا تحتوي إعدادات تغطية بعد");
    }

    private Parsed parse(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessRuleException("ملف الاستيراد فارغ");
        if (file.getSize() > 10L * 1024 * 1024) throw new BusinessRuleException("حجم ملف الاستيراد يتجاوز 10 ميجابايت");
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BusinessRuleException("صيغة الملف غير مدعومة؛ المطلوب ملف xlsx");
        }
        try (InputStream input = file.getInputStream(); Workbook workbook = new XSSFWorkbook(input)) {
            Parsed p = new Parsed();
            if (workbook.getSheet("المنافع") != null) {
                parseSimplified(workbook, p);
                return p;
            }
            read(workbook, "Rules", 10, (row, n) -> {
                EncounterType context = benefitContext(row, 2, n);
                p.rules.add(new RuleRow(text(row, 0), text(row, 1), context, context.name(),
                        integer(row, 3), decimal(row, 4), integer(row, 5), bool(row, 6, false),
                        integer(row, 7, 100), text(row, 8), bool(row, 9, true), n));
            });
            read(workbook, "Groups", 5, (row, n) -> p.groups.add(new GroupRow(
                    text(row, 0), text(row, 1), benefitContext(row, 2, n),
                    enumValue(AggregationMode.class, row, 3, n), bool(row, 4, true), n)));
            read(workbook, "Buckets", 15, (row, n) -> p.buckets.add(new BucketRow(
                    text(row, 0), text(row, 1), text(row, 2), benefitContext(row, 3, n),
                    decimal(row, 4), integer(row, 5), integer(row, 6), enumValue(LimitPeriodType.class, row, 7, n), integer(row, 8, 1),
                    enumValue(CountingMethod.class, row, 9, n), enumValue(ConsumptionBasis.class, row, 10, n),
                    enumValue(BenefitScopeType.class, row, 11, n), text(row, 12),
                    bool(row, 13, false), bool(row, 14, true), n)));
            read(workbook, "Links", 6, (row, n) -> {
                EncounterType context = benefitContext(row, 1, n);
                p.links.add(new LinkRow(text(row, 0), context, context.name(), text(row, 2),
                        integer(row, 3, 1), enumValue(ConsumptionMode.class, row, 4, n), bool(row, 5, true), n));
            });
            Sheet special = workbook.getSheet("SpecialBenefits");
            if (special != null) read(workbook, "SpecialBenefits", 11, (row, n) -> p.specials.add(new SpecialRow(
                    text(row, 0), text(row, 1), integer(row, 2), decimal(row, 3), decimal(row, 4),
                    integer(row, 5), enumValue(LimitPeriodType.class, row, 6, n), bool(row, 7, false),
                    text(row, 8), text(row, 9), bool(row, 10, true), n)));
            Sheet review = workbook.getSheet("Review");
            if (review != null) read(workbook, "Review", 5, (row, n) -> {
                String message = "Review صف " + n + ": البند «" + text(row, 2) + "»؛ السبب: " + text(row, 3)
                        + "؛ المعالجة المطلوبة: " + text(row, 4);
                if ("BLOCKER".equalsIgnoreCase(text(row, 0))) p.reviewErrors.add(message);
                else p.warnings.add(message);
            });
            return p;
        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessRuleException("تعذر قراءة ملف المنافع: " + e.getMessage());
        }
    }

    private void parseSimplified(Workbook workbook, Parsed p) {
        boolean benefitsHasPeriodValue = "قيمة الفترة".equals(text(workbook.getSheet("المنافع").getRow(0), 10));
        boolean benefitsHasBasis = "أساس احتساب السقف".equals(text(workbook.getSheet("المنافع").getRow(0), benefitsHasPeriodValue ? 12 : 11));
        read(workbook, "المنافع", 12, (row, n) -> {
            String category = text(row, 0);
            EncounterType context = benefitContext(row, 2, n, true);
            int countingColumn = benefitsHasPeriodValue ? 11 : 10;
            int basisColumn = countingColumn + 1;
            int activeColumn = basisColumn + (benefitsHasBasis ? 1 : 0);
            int decisionContextColumn = activeColumn + 1;
            String decisionContext = normalizedDecisionContext(text(row, decisionContextColumn), context);
            p.rules.add(new RuleRow(category, text(row, 1), context, decisionContext, integer(row, 3, 100), decimal(row, 4), 0,
                    bool(row, 5, false), 100, "استيراد مبسط", bool(row, activeColumn, true), n));
            BigDecimal amount = decimal(row, 6); Integer times = integer(row, 7); Integer days = integer(row, 8);
            if (amount != null || times != null || days != null) {
                String safe = category == null ? "ROW" + n : category.trim();
                // Keyed by the decision context, not the encounter type. A
                // benefit may carry one ceiling under INPATIENT and another
                // under MATERNITY, and both rows are INPATIENT encounters -- so
                // naming the generated group after the encounter type alone made
                // the two collide and the whole file was refused as duplicate.
                String scope = safe + "-" + decisionContext;
                String groupCode = "AUTO-BEN-" + scope;
                String bucketCode = "AUTO-BEN-LIMIT-" + scope;
                String limitName = autoLimitName(text(row, 1), safe, decisionContext);
                p.groups.add(new GroupRow(groupCode, limitName, context, AggregationMode.INDIVIDUAL, bool(row, activeColumn, true), n));
                p.buckets.add(new BucketRow(bucketCode, limitName, groupCode, context,
                        amount, times, days, enumOrDefault(LimitPeriodType.class, row, 9, n, LimitPeriodType.POLICY_PERIOD),
                        benefitsHasPeriodValue ? integer(row, 10, 1) : 1,
                        enumOrDefault(CountingMethod.class, row, countingColumn, n, CountingMethod.EACH_UNIT),
                        benefitsHasBasis ? enumValue(ConsumptionBasis.class, row, basisColumn, n) : ConsumptionBasis.ELIGIBLE_AMOUNT,
                        BenefitScopeType.CATEGORY, null, false, bool(row, activeColumn, true), n));
                p.links.add(new LinkRow(category, context, decisionContext, bucketCode, 1, ConsumptionMode.PRIMARY, true, n));
            }
        });
        if (workbook.getSheet("المجموعات") != null) {
            boolean groupsHasPeriodValue = "قيمة الفترة".equals(text(workbook.getSheet("المجموعات").getRow(0), 8));
            boolean groupsHasBasis = "أساس احتساب السقف".equals(text(workbook.getSheet("المجموعات").getRow(0), groupsHasPeriodValue ? 10 : 9));
            read(workbook, "المجموعات", 10, (row, n) -> {
                String code = text(row, 0); EncounterType context = benefitContext(row, 2, n, true);
                int countingColumn = groupsHasPeriodValue ? 9 : 8;
                int basisColumn = countingColumn + 1;
                int activeColumn = basisColumn + (groupsHasBasis ? 1 : 0);
                int decisionContextColumn = activeColumn + 1;
                String decisionContext = normalizedDecisionContext(text(row, decisionContextColumn), context);
                boolean active = bool(row, activeColumn, true); String bucketCode = "AUTO-GRP-" + code;
                p.groups.add(new GroupRow(code, text(row, 1), context, AggregationMode.SHARED, active, n));
                p.buckets.add(new BucketRow(bucketCode, text(row, 1), code, context, decimal(row, 4), integer(row, 5), integer(row, 6),
                        enumOrDefault(LimitPeriodType.class, row, 7, n, LimitPeriodType.POLICY_PERIOD),
                        groupsHasPeriodValue ? integer(row, 8, 1) : 1,
                        enumOrDefault(CountingMethod.class, row, countingColumn, n, CountingMethod.EACH_UNIT),
                        groupsHasBasis ? enumValue(ConsumptionBasis.class, row, basisColumn, n) : ConsumptionBasis.ELIGIBLE_AMOUNT,
                        BenefitScopeType.GROUP, null, true, active, n));
                String members = text(row, 3);
                if (members != null) for (String member : members.split("[,،]"))
                    if (!member.isBlank()) p.links.add(new LinkRow(member.trim(), context, decisionContext, bucketCode, 1, ConsumptionMode.PRIMARY, true, n));
            });
        }
    }

    private List<String> validate(Long policyId, Parsed p, ImportMode mode) {
        List<String> errors = new ArrayList<>(p.reviewErrors);
        Set<String> groupCodes = uniqueCodes(p.groups.stream().map(GroupRow::code).toList(), "Groups", errors);
        Set<String> bucketCodes = uniqueCodes(p.buckets.stream().map(BucketRow::code).toList(), "Buckets", errors);
        uniqueNames(p.groups.stream().map(GroupRow::name).toList(), "Groups", errors);
        uniqueNames(p.buckets.stream().map(BucketRow::name).toList(), "Buckets", errors);
        Set<String> ruleKeys = new HashSet<>();
        for (RuleRow r : p.rules) {
            if (blank(r.categoryCode) || r.context == null || r.coverage == null) {
                errors.add("Rules صف " + r.row + ": بيانات ناقصة — category_code=" + r.categoryCode
                        + ", encounter_type=" + r.context + ", coverage_percent=" + r.coverage);
                continue;
            }
            String key = r.categoryCode + "|" + r.claimContextCode;
            if (!ruleKeys.add(key)) errors.add("Rules صف " + r.row + ": قاعدة مكررة " + key);
            MedicalCategory category = categoryRepository.findByCode(r.categoryCode).orElse(null);
            if (category == null) errors.add("Rules صف " + r.row + ": تصنيف غير معتمد " + r.categoryCode);
            else if (!supports(category, r.context)) errors.add("Rules صف " + r.row + ": السياق " + r.context
                    + " غير مسموح للتصنيف " + r.categoryCode);
            var claimContext = claimContextRepository.findById(r.claimContextCode).orElse(null);
            if (claimContext == null || !claimContext.isActive()) {
                errors.add("Rules صف " + r.row + ": سياق قرار غير معتمد أو غير فعال " + r.claimContextCode);
            } else if (claimContext.getBaseEncounterType() != EncounterType.ANY
                    && claimContext.getBaseEncounterType() != r.context) {
                errors.add("Rules صف " + r.row + ": سياق القرار " + r.claimContextCode
                        + " لا يطابق نوع الزيارة " + r.context);
            }
            if (r.coverage < 0 || r.coverage > 100) errors.add("Rules صف " + r.row + ": نسبة تغطية خارج 0-100");
        }
        for (GroupRow g : p.groups) {
            if (blank(g.code) || blank(g.name) || g.context == null || g.mode == null)
                errors.add("Groups صف " + g.row + ": بيانات ناقصة — group_code=" + g.code + ", group_name=" + g.name
                        + ", context_type=" + g.context + ", aggregation_mode=" + g.mode);
            if (!blank(g.name)) groupRepository.findByPolicyIdAndNormalizedNameAr(policyId, g.name).ifPresent(existing -> {
                if (mode != ImportMode.REPLACE && (g.code == null || !existing.getCode().equalsIgnoreCase(g.code)))
                    errors.add("Groups صف " + g.row + ": الاسم «" + g.name + "» مستخدم مسبقًا للمجموعة " + existing.getCode());
            });
        }
        for (BucketRow b : p.buckets) {
            if (blank(b.code) || blank(b.name))
                errors.add("Buckets صف " + b.row + ": bucket_code أو bucket_name فارغ — code=" + b.code + ", name=" + b.name);
            if (!groupCodes.contains(b.groupCode))
                errors.add("Buckets صف " + b.row + ": group_code «" + b.groupCode + "» غير موجود في ورقة Groups");
            if (b.benefitScopeType == null)
                errors.add("Buckets صف " + b.row + ": benefit_scope_type مطلوب (SERVICE/CATEGORY/GROUP)");
            if (!blank(b.parentCode) && !bucketCodes.contains(b.parentCode))
                errors.add("Buckets صف " + b.row + ": الوعاء الأب غير موجود " + b.parentCode);
            if (requiresPeriodValue(b.period) && (b.periodValue == null || b.periodValue < 2))
                errors.add("Buckets صف " + b.row + ": مدة السقف المخصصة تتطلب period_value أكبر من 1");
            if (b.amount != null && b.amount.signum() < 0) errors.add("Buckets صف " + b.row + ": السقف المالي لا يقبل قيمة سالبة");
            if (b.times != null && b.times < 0) errors.add("Buckets صف " + b.row + ": حد المرات لا يقبل قيمة سالبة");
            if (b.days != null && b.days < 0) errors.add("Buckets صف " + b.row + ": حد الأيام لا يقبل قيمة سالبة");
            if (!blank(b.name)) bucketRepository.findByPolicyIdAndNormalizedNameAr(policyId, b.name).ifPresent(existing -> {
                if (mode != ImportMode.REPLACE && (b.code == null || !existing.getCode().equalsIgnoreCase(b.code)))
                    errors.add("Buckets صف " + b.row + ": الاسم «" + b.name + "» مستخدم مسبقًا للوعاء " + existing.getCode());
            });
        }
        validateParentCycles(p.buckets, errors);
        for (LinkRow l : p.links) {
            if (!ruleKeys.contains(l.categoryCode + "|" + l.claimContextCode))
                errors.add("Links صف " + l.row + ": القاعدة غير موجودة في Rules");
            if (!bucketCodes.contains(l.bucketCode)) errors.add("Links صف " + l.row + ": bucket_code «" + l.bucketCode + "» غير موجود في ورقة Buckets");
        }
        for (SpecialRow s : p.specials) {
            BenefitDefinition definition = definitionRepository.findByCode(s.definitionCode).orElse(null);
            if (definition == null || definition.getBenefitType() != BenefitDefinition.BenefitType.SPECIAL_EXPENSE)
                errors.add("SpecialBenefits صف " + s.row + ": منفعة خاصة غير معتمدة " + s.definitionCode);
            if (s.coverage == null || s.coverage < 0 || s.coverage > 100 || s.period == null)
                errors.add("SpecialBenefits صف " + s.row + ": coverage_percent من 0 إلى 100 و period_type مطلوبان");
        }
        return errors;
    }

    private void apply(BenefitPolicy policy, Parsed p, Counter c, ImportMode mode) {
        if (mode == ImportMode.REPLACE) {
            c.deactivated = countMissingConfiguration(policy.getId(), p);
            deactivateMissingConfiguration(policy.getId(), p);
        }
        Map<String, BenefitGroup> groups = new HashMap<>();
        for (GroupRow row : p.groups) {
            BenefitGroup group = groupRepository.findByPolicyIdAndCodeIgnoreCase(policy.getId(), row.code)
                    .or(() -> mode == ImportMode.REPLACE && !blank(row.name)
                            ? groupRepository.findByPolicyIdAndNormalizedNameAr(policy.getId(), row.name)
                            : Optional.empty())
                    .orElse(null);
            if (group == null) { group = new BenefitGroup(); group.setPolicy(policy); group.setCode(row.code); c.created++; }
            else c.updated++;
            group.setCode(row.code);
            group.setNameAr(row.name); group.setContextType(row.context); group.setAggregationMode(row.mode); group.setActive(row.active);
            groups.put(row.code, groupRepository.save(group));
        }
        for (SpecialRow row : p.specials) {
            BenefitDefinition definition = definitionRepository.findByCode(row.definitionCode).orElseThrow();
            String groupCode = "SPECIAL-" + definition.getCode().substring(4);
            BenefitGroup group = groupRepository.findByPolicyIdAndCodeIgnoreCase(policy.getId(), groupCode).orElse(null);
            if (group == null) { group = new BenefitGroup(); group.setPolicy(policy); group.setCode(groupCode); c.created++; }
            else c.updated++;
            group.setBenefitDefinition(definition); group.setNameAr(row.name); group.setContextType(EncounterType.SPECIAL);
            group.setAggregationMode(AggregationMode.INDIVIDUAL); group.setActive(row.active);
            group.setCoveragePercent(row.coverage); group.setCopayPercentage(row.copay);
            group.setRequiresPreApproval(row.preApproval); group.setNotes(row.notes); group.setSourceClause(row.sourceClause);
            groups.put(groupCode, groupRepository.save(group));
            String bucketCode = "LIMIT-" + definition.getCode().substring(4);
            BenefitLimitBucket bucket = bucketRepository.findByPolicyIdAndCodeIgnoreCase(policy.getId(), bucketCode).orElse(null);
            if (bucket == null) { bucket = new BenefitLimitBucket(); bucket.setPolicy(policy); bucket.setCode(bucketCode); c.created++; }
            else c.updated++;
            bucket.setBenefitGroup(group); bucket.setNameAr(row.name); bucket.setContextType(EncounterType.SPECIAL);
            bucket.setAmountLimit(row.amount); bucket.setTimesLimit(row.times); bucket.setPeriodType(row.period); bucket.setPeriodValue(1);
            bucket.setCountingMethod(CountingMethod.EACH_LINE); bucket.setConsumptionBasis(ConsumptionBasis.ELIGIBLE_AMOUNT);
            bucket.setBenefitScopeType(BenefitScopeType.SERVICE);
            bucket.setShared(false); bucket.setActive(row.active); bucketRepository.save(bucket);
        }
        Map<String, BenefitLimitBucket> buckets = new HashMap<>();
        for (BucketRow row : p.buckets) {
            BenefitLimitBucket b = bucketRepository.findByPolicyIdAndCodeIgnoreCase(policy.getId(), row.code)
                    .or(() -> mode == ImportMode.REPLACE && !blank(row.name)
                            ? bucketRepository.findByPolicyIdAndNormalizedNameAr(policy.getId(), row.name)
                            : Optional.empty())
                    .orElse(null);
            if (b == null) { b = new BenefitLimitBucket(); b.setPolicy(policy); b.setCode(row.code); c.created++; }
            else c.updated++;
            b.setCode(row.code);
            b.setBenefitGroup(groups.get(row.groupCode)); b.setNameAr(row.name); b.setContextType(row.context);
            b.setAmountLimit(row.amount); b.setTimesLimit(row.times); b.setDaysLimit(row.days); b.setPeriodType(row.period); b.setPeriodValue(row.periodValue);
            b.setCountingMethod(row.counting); b.setConsumptionBasis(row.basis); b.setShared(row.shared); b.setActive(row.active);
            b.setBenefitScopeType(row.benefitScopeType);
            buckets.put(row.code, bucketRepository.save(b));
        }
        for (BucketRow row : p.buckets) if (!blank(row.parentCode)) {
            BenefitLimitBucket b = buckets.get(row.code); b.setParentBucket(buckets.get(row.parentCode)); bucketRepository.save(b);
        }
        Map<String, BenefitPolicyRule> rules = new HashMap<>();
        for (RuleRow row : p.rules) {
            MedicalCategory category = categoryRepository.findByCode(row.categoryCode).orElseThrow();
            BenefitPolicyRule rule = ruleRepository
                    .findFirstByBenefitPolicyIdAndMedicalCategoryIdAndClaimContextCodeOrderByIdDesc(
                            policy.getId(), category.getId(), row.claimContextCode).orElse(null);
            if (rule == null) { rule = new BenefitPolicyRule(); rule.setBenefitPolicy(policy); rule.setMedicalCategory(category); c.created++; }
            else c.updated++;
            rule.setEncounterType(row.context);
            rule.setClaimContextCode(row.claimContextCode);
            rule.setCoveragePercent(row.coverage); rule.setCopayPercentage(row.copay); rule.setWaitingPeriodDays(row.waitingDays);
            rule.setRequiresPreApproval(row.preApproval); rule.setPriority(row.priority); rule.setNotes(row.notes);
            rule.setInheritanceEnabled(false); rule.setActive(row.active); rule.setDeleted(false);
            rules.put(row.categoryCode + "|" + row.claimContextCode, ruleRepository.save(rule));
        }
        for (LinkRow row : p.links) {
            BenefitPolicyRule rule = rules.get(row.categoryCode + "|" + row.claimContextCode);
            BenefitLimitBucket bucket = buckets.get(row.bucketCode);
            BenefitRuleBucket link = linkRepository.findByRuleIdAndBucketId(rule.getId(), bucket.getId()).orElse(null);
            if (link == null) { link = new BenefitRuleBucket(); link.setRule(rule); link.setBucket(bucket); c.created++; }
            else c.updated++;
            link.setConsumptionOrder(row.order); link.setConsumptionMode(row.mode); link.setMandatory(row.mandatory);
            linkRepository.save(link);
        }
    }

    private boolean supports(MedicalCategory category, EncounterType context) {
        return category.getContexts().contains(CategoryContext.ANY)
                || category.getContexts().contains(CategoryContext.valueOf(context.name()));
    }

    private Set<String> uniqueCodes(List<String> values, String sheet, List<String> errors) {
        Set<String> result = new HashSet<>();
        Set<String> normalizedValues = new HashSet<>();
        for (String value : values) {
            String normalized = blank(value) ? null : value.trim().toLowerCase(Locale.ROOT);
            if (normalized == null || !normalizedValues.add(normalized)) errors.add(sheet + ": كود فارغ أو مكرر " + value);
            else result.add(value);
        }
        return result;
    }

    private void validateParentCycles(List<BucketRow> rows, List<String> errors) {
        Map<String, String> parents = new HashMap<>();
        for (BucketRow row : rows) if (!blank(row.code)) {
            parents.put(normalizedCode(row.code), blank(row.parentCode) ? null : normalizedCode(row.parentCode));
        }
        for (String start : parents.keySet()) {
            Set<String> path = new HashSet<>();
            String current = start;
            while (!blank(current)) {
                if (!path.add(current)) {
                    errors.add("Buckets: توجد حلقة في تسلسل الأوعية الأب تبدأ من «" + start + "»");
                    break;
                }
                current = parents.get(current);
            }
        }
    }

    private void deactivateMissingConfiguration(Long policyId, Parsed p) {
        Set<String> ruleKeys = p.rules.stream()
                .map(row -> normalizedCode(row.categoryCode) + "|" + row.claimContextCode).collect(java.util.stream.Collectors.toSet());
        for (BenefitPolicyRule rule : ruleRepository.findByBenefitPolicyId(policyId)) {
            String key = normalizedCode(rule.getMedicalCategory().getCode()) + "|" + rule.getClaimContextCode();
            if (!ruleKeys.contains(key)) { rule.setActive(false); ruleRepository.save(rule); }
        }

        Set<String> groupCodes = p.groups.stream().map(GroupRow::code).map(this::normalizedCode)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> bucketCodes = p.buckets.stream().map(BucketRow::code).map(this::normalizedCode)
                .collect(java.util.stream.Collectors.toSet());
        for (SpecialRow row : p.specials) {
            String suffix = row.definitionCode.substring(4);
            groupCodes.add(normalizedCode("SPECIAL-" + suffix));
            bucketCodes.add(normalizedCode("LIMIT-" + suffix));
        }
        for (BenefitGroup group : groupRepository.findByPolicyIdOrderByCode(policyId)) {
            if (!groupCodes.contains(normalizedCode(group.getCode()))) { group.setActive(false); groupRepository.save(group); }
        }
        for (BenefitLimitBucket bucket : bucketRepository.findByPolicyIdOrderByCode(policyId)) {
            if (!bucketCodes.contains(normalizedCode(bucket.getCode()))) { bucket.setActive(false); bucketRepository.save(bucket); }
        }
    }

    private int countMissingConfiguration(Long policyId, Parsed p) {
        Set<String> ruleKeys = p.rules.stream().map(row -> normalizedCode(row.categoryCode) + "|" + row.claimContextCode)
                .collect(java.util.stream.Collectors.toSet());
        int count = (int) ruleRepository.findByBenefitPolicyId(policyId).stream()
                .filter(rule -> rule.isActive() && !ruleKeys.contains(normalizedCode(rule.getMedicalCategory().getCode()) + "|" + rule.getClaimContextCode()))
                .count();
        Set<String> groupCodes = p.groups.stream().map(GroupRow::code).map(this::normalizedCode)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> bucketCodes = p.buckets.stream().map(BucketRow::code).map(this::normalizedCode)
                .collect(java.util.stream.Collectors.toSet());
        count += (int) groupRepository.findByPolicyIdOrderByCode(policyId).stream()
                .filter(group -> group.isActive() && !groupCodes.contains(normalizedCode(group.getCode()))).count();
        count += (int) bucketRepository.findByPolicyIdOrderByCode(policyId).stream()
                .filter(bucket -> bucket.isActive() && !bucketCodes.contains(normalizedCode(bucket.getCode()))).count();
        return count;
    }

    private void uniqueNames(List<String> values, String sheet, List<String> errors) {
        Set<String> result = new HashSet<>();
        for (String value : values) {
            if (!blank(value) && !result.add(value.trim().toLowerCase(Locale.ROOT))) {
                errors.add(sheet + ": اسم مكرر ضمن الملف «" + value + "»");
            }
        }
    }

    private void read(Workbook wb, String name, int columns, RowConsumer consumer) {
        Sheet sheet = wb.getSheet(name);
        if (sheet == null) throw new BusinessRuleException("ورقة " + name + " مطلوبة");
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i); if (row == null || blank(text(row, 0))) continue;
            consumer.accept(row, i + 1);
        }
    }
    private String text(Row row, int i) { Cell c=row.getCell(i); if(c==null) return null; String v=new DataFormatter().formatCellValue(c).trim(); return v.isEmpty()?null:v; }
    private Integer integer(Row row, int i) { String v=text(row,i); if(v==null)return null; return new BigDecimal(v.replace("%","").replace(",","")).intValueExact(); }
    private Integer integer(Row row, int i, int fallback) { Integer v=integer(row,i); return v==null?fallback:v; }
    private BigDecimal decimal(Row row, int i) { String v=text(row,i); if(v==null)return null; return new BigDecimal(v.replace("%","").replace(",","")); }
    private boolean bool(Row row, int i, boolean fallback) { String v=text(row,i); if(v==null)return fallback; return Set.of("TRUE","YES","1","نعم","صح").contains(v.toUpperCase()); }
    private <E extends Enum<E>> E enumValue(Class<E> type, Row row, int i, int n) { String v=text(row,i); if(v==null)return null; try{return Enum.valueOf(type,v.toUpperCase());}catch(Exception e){throw new BusinessRuleException("قيمة غير صحيحة في الصف "+n+": "+v);} }
    private <E extends Enum<E>> E enumOrDefault(Class<E> type, Row row, int i, int n, E fallback) { E value=enumValue(type,row,i,n); return value==null?fallback:value; }
    private EncounterType benefitContext(Row row, int i, int n) {
        return benefitContext(row, i, n, false);
    }

    /**
     * The base visit type a rule applies under -- OUTPATIENT, INPATIENT or ANY
     * -- kept separate from the dynamic claim/decision context (MATERNITY,
     * PREGNANCY_COMPLICATIONS, ...) that the current template carries in its own
     * "سياق القرار" column.
     *
     * <p>Parses the cell directly instead of delegating to {@code enumValue}: a
     * business context typed here (MATERNITY is not an {@link EncounterType}
     * constant at all) used to fail inside {@code enumValue} first, with the
     * generic "قيمة غير صحيحة" message and no mention of what was expected or
     * where the value belonged -- the more specific guidance below was
     * unreachable for exactly the mistake someone using the new business
     * contexts was most likely to make.
     *
     * @param hasDecisionContextColumn whether this sheet also carries a "سياق
     *        القرار" column. True only for the current template's "المنافع" and
     *        "المجموعات" sheets; the legacy English-named Rules/Groups/Buckets/
     *        Links sheets have no such column, and pointing a user at one that
     *        does not exist there would be worse than saying nothing.
     */
    private EncounterType benefitContext(Row row, int i, int n, boolean hasDecisionContextColumn) {
        String raw = text(row, i);
        EncounterType value = null;
        if (raw != null) {
            try {
                value = EncounterType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // value stays null; reported below with the raw text.
            }
        }
        if (value == EncounterType.OUTPATIENT || value == EncounterType.INPATIENT || value == EncounterType.ANY) {
            return value;
        }
        String guidance = hasDecisionContextColumn
                ? " إن كنت تقصد سياقاً تجارياً مثل MATERNITY أو PREGNANCY_COMPLICATIONS، اكتبه في عمود «سياق القرار» لا في عمود «السياق»."
                : "";
        throw new BusinessRuleException("عمود «السياق» في الصف " + n
                + " يمثل نوع الزيارة الأساسي ويقبل فقط OUTPATIENT أو INPATIENT أو ANY؛ القيمة المكتوبة «"
                + raw + "» ليست كذلك." + guidance);
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String normalizedCode(String value) { return value == null ? null : value.trim().toLowerCase(Locale.ROOT); }
    private String normalizedDecisionContext(String value, EncounterType fallback) {
        String normalized = blank(value) ? fallback.name() : value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z][A-Z0-9_]{1,59}")) {
            throw new BusinessRuleException("كود سياق القرار غير صالح: " + value);
        }
        return normalized;
    }
    private boolean requiresPeriodValue(LimitPeriodType periodType) {
        if (periodType == null) return false;
        return switch (periodType) {
            case MULTI_YEAR_POLICY, CUSTOM_DAYS, CUSTOM_WEEKS, CUSTOM_MONTHS, CUSTOM_YEARS -> true;
            default -> false;
        };
    }
    /**
     * Names the generated ceiling after the decision context rather than the
     * encounter type, so two ceilings on the same benefit -- one for ordinary
     * admission and one for childbirth -- read differently in the policy screen
     * instead of sharing a name and colliding on import.
     */
    private String autoLimitName(String name, String fallback, String decisionContext) {
        String base = blank(name) ? fallback : name;
        String label = claimContextRepository.findById(decisionContext)
                .map(context -> context.getNameAr())
                .filter(nameAr -> !blank(nameAr))
                .orElse(switch (decisionContext == null ? "" : decisionContext) {
                    case "INPATIENT" -> "إيواء";
                    case "OUTPATIENT" -> "عيادات خارجية";
                    default -> "عام";
                });
        return base + " - " + label;
    }

    private interface RowConsumer { void accept(Row row, int number); }
    private record RuleRow(String categoryCode,String categoryName,EncounterType context,String claimContextCode,Integer coverage,BigDecimal copay,Integer waitingDays,boolean preApproval,Integer priority,String notes,boolean active,int row){}
    private record GroupRow(String code,String name,EncounterType context,AggregationMode mode,boolean active,int row){}
    private record BucketRow(String code,String name,String groupCode,EncounterType context,BigDecimal amount,Integer times,Integer days,LimitPeriodType period,Integer periodValue,CountingMethod counting,ConsumptionBasis basis,BenefitScopeType benefitScopeType,String parentCode,boolean shared,boolean active,int row){}
    private record LinkRow(String categoryCode,EncounterType context,String claimContextCode,String bucketCode,Integer order,ConsumptionMode mode,boolean mandatory,int row){}
    private record SpecialRow(String definitionCode,String name,Integer coverage,BigDecimal copay,BigDecimal amount,Integer times,LimitPeriodType period,boolean preApproval,String notes,String sourceClause,boolean active,int row){}
    private static class Parsed { List<RuleRow> rules=new ArrayList<>(); List<GroupRow> groups=new ArrayList<>(); List<BucketRow> buckets=new ArrayList<>(); List<LinkRow> links=new ArrayList<>(); List<SpecialRow> specials=new ArrayList<>(); List<String> warnings=new ArrayList<>(); List<String> reviewErrors=new ArrayList<>(); }
    private static class Counter { int created; int updated; int deactivated; }
}
