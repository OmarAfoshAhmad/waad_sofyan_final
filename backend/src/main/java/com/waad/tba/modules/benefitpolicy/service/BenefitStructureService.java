package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.modules.benefitpolicy.dto.BenefitStructureDtos.*;
import com.waad.tba.modules.benefitpolicy.entity.*;
import com.waad.tba.modules.benefitpolicy.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class BenefitStructureService {
    private final BenefitPolicyRepository policyRepository;
    private final BenefitPolicyRuleRepository ruleRepository;
    private final BenefitGroupRepository groupRepository;
    private final BenefitLimitBucketRepository bucketRepository;
    private final BenefitRuleBucketRepository ruleBucketRepository;
    private final BenefitBucketConsumptionRepository consumptionRepository;

    @Transactional(readOnly = true)
    public StructureResponse getStructure(Long policyId) {
        requirePolicy(policyId);
        return new StructureResponse(
                groupRepository.findByPolicyIdOrderByCode(policyId).stream().map(this::groupResponse).toList(),
                bucketRepository.findByPolicyIdOrderByCode(policyId).stream().map(this::bucketResponse).toList(),
                ruleBucketRepository.findByRuleBenefitPolicyIdOrderByConsumptionOrder(policyId).stream()
                        .map(link -> new RuleBucketResponse(link.getId(), link.getRule().getId(),
                                bucketResponse(link.getBucket()), link.getConsumptionOrder(),
                                link.getConsumptionMode(), link.isMandatory()))
                        .toList());
    }

    public GroupResponse createGroup(Long policyId, GroupRequest request) {
        BenefitPolicy policy = requirePolicy(policyId);
        String code = request.code() == null || request.code().isBlank()
                ? nextGroupCode(policyId)
                : request.code().trim();
        String name = request.nameAr().trim();
        if (groupRepository.existsByPolicyIdAndCodeIgnoreCase(policyId, code)) {
            throw new BusinessRuleException("يوجد ضمن هذه الوثيقة مجموعة منفعة بالكود نفسه: " + code);
        }
        if (groupRepository.findByPolicyIdAndNameArIgnoreCase(policyId, name).isPresent()) {
            throw new BusinessRuleException("يوجد ضمن هذه الوثيقة مجموعة منفعة بالاسم نفسه: " + name);
        }
        BenefitGroup group = BenefitGroup.builder()
                .policy(policy).code(code).nameAr(name)
                .contextType(request.contextType()).aggregationMode(request.aggregationMode())
                .active(request.active() == null || request.active()).build();
        BenefitGroup savedGroup = groupRepository.save(group);

        // Simplified administration contract: the user creates one benefit
        // group. Its technical bucket and rule links are internal details.
        List<Long> ruleIds = request.ruleIds() == null ? List.of() : request.ruleIds().stream()
                .filter(Objects::nonNull).distinct().toList();
        if (ruleIds.size() < 2) {
            throw new BusinessRuleException("مجموعة المنافع يجب أن تضم منفعتين على الأقل");
        }
        if (!ruleIds.isEmpty()) {
            String bucketCode = "AUTO-GRP-" + code;
            BenefitLimitBucket bucket = BenefitLimitBucket.builder()
                    .policy(policy).benefitGroup(savedGroup).code(bucketCode).nameAr(name)
                    .contextType(request.contextType()).amountLimit(request.amountLimit())
                    .timesLimit(request.timesLimit()).daysLimit(request.daysLimit())
                    .periodType(request.periodType() == null
                            ? com.waad.tba.modules.benefitpolicy.enums.LimitPeriodType.POLICY_PERIOD
                            : request.periodType())
                    .periodValue(1)
                    .countingMethod(request.countingMethod() == null
                            ? com.waad.tba.modules.benefitpolicy.enums.CountingMethod.EACH_UNIT
                            : request.countingMethod())
                    .consumptionBasis(com.waad.tba.modules.benefitpolicy.enums.ConsumptionBasis.COMPANY_SHARE)
                    .shared(ruleIds.size() > 1).active(request.active() == null || request.active()).build();
            BenefitLimitBucket savedBucket = bucketRepository.save(bucket);
            int order = 1;
            for (Long ruleId : ruleIds) {
                BenefitPolicyRule rule = ruleRepository.findById(ruleId)
                        .orElseThrow(() -> new ResourceNotFoundException("BenefitPolicyRule", "id", ruleId));
                assertSamePolicy(policyId, rule.getBenefitPolicy().getId(), "قاعدة المجموعة");
                if (rule.getEncounterType() != request.contextType()
                        && request.contextType() != com.waad.tba.modules.providercontract.enums.EncounterType.ANY) {
                    throw new BusinessRuleException("سياق إحدى قواعد المجموعة لا يطابق سياق المجموعة: " + ruleId);
                }
                ruleBucketRepository.save(BenefitRuleBucket.builder().rule(rule).bucket(savedBucket)
                        .consumptionOrder(order++)
                        .consumptionMode(com.waad.tba.modules.benefitpolicy.enums.ConsumptionMode.PRIMARY)
                        .mandatory(true).build());
            }
        }
        return groupResponse(savedGroup);
    }

    public GroupResponse updateGroup(Long policyId, Long groupId, GroupRequest request) {
        BenefitGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("BenefitGroup", "id", groupId));
        assertSamePolicy(policyId, group.getPolicy().getId(), "مجموعة المنفعة");
        String name = request.nameAr().trim();
        groupRepository.findByPolicyIdAndNameArIgnoreCase(policyId, name).ifPresent(existing -> {
            if (!Objects.equals(existing.getId(), groupId))
                throw new BusinessRuleException("يوجد ضمن هذه الوثيقة مجموعة منفعة بالاسم نفسه: " + name);
        });
        List<Long> ruleIds = request.ruleIds() == null ? List.of() : request.ruleIds().stream()
                .filter(Objects::nonNull).distinct().toList();
        if (ruleIds.size() < 2) throw new BusinessRuleException("مجموعة المنافع يجب أن تضم منفعتين على الأقل");

        group.setNameAr(name);
        group.setContextType(request.contextType());
        group.setAggregationMode(request.aggregationMode());
        group.setActive(request.active() == null || request.active());
        BenefitGroup savedGroup = groupRepository.save(group);

        BenefitLimitBucket bucket = bucketRepository.findByBenefitGroupId(groupId).stream()
                .filter(item -> item.getCode().startsWith("AUTO-GRP-"))
                .findFirst().orElseGet(() -> BenefitLimitBucket.builder()
                        .policy(group.getPolicy()).benefitGroup(group).code("AUTO-GRP-" + group.getCode())
                        .periodValue(1).consumptionBasis(com.waad.tba.modules.benefitpolicy.enums.ConsumptionBasis.COMPANY_SHARE)
                        .build());
        bucket.setNameAr(name);
        bucket.setContextType(request.contextType());
        bucket.setAmountLimit(request.amountLimit());
        bucket.setTimesLimit(request.timesLimit());
        bucket.setDaysLimit(request.daysLimit());
        bucket.setPeriodType(request.periodType() == null
                ? com.waad.tba.modules.benefitpolicy.enums.LimitPeriodType.POLICY_PERIOD : request.periodType());
        bucket.setCountingMethod(request.countingMethod() == null
                ? com.waad.tba.modules.benefitpolicy.enums.CountingMethod.EACH_UNIT : request.countingMethod());
        bucket.setShared(true);
        bucket.setActive(request.active() == null || request.active());
        BenefitLimitBucket savedBucket = bucketRepository.save(bucket);

        ruleBucketRepository.deleteAll(ruleBucketRepository.findByBucketId(savedBucket.getId()));
        int order = 1;
        for (Long ruleId : ruleIds) {
            BenefitPolicyRule rule = ruleRepository.findById(ruleId)
                    .orElseThrow(() -> new ResourceNotFoundException("BenefitPolicyRule", "id", ruleId));
            assertSamePolicy(policyId, rule.getBenefitPolicy().getId(), "قاعدة المجموعة");
            if (rule.getEncounterType() != request.contextType()
                    && request.contextType() != com.waad.tba.modules.providercontract.enums.EncounterType.ANY)
                throw new BusinessRuleException("سياق إحدى المنافع لا يطابق نطاق المجموعة: " + ruleId);
            ruleBucketRepository.save(BenefitRuleBucket.builder().rule(rule).bucket(savedBucket)
                    .consumptionOrder(order++).consumptionMode(com.waad.tba.modules.benefitpolicy.enums.ConsumptionMode.PRIMARY)
                    .mandatory(true).build());
        }
        return groupResponse(savedGroup);
    }

    private String nextGroupCode(Long policyId) {
        long sequence = groupRepository.countByPolicyId(policyId) + 1;
        String candidate;
        do {
            candidate = "GRP-" + String.format("%04d", sequence++);
        } while (groupRepository.existsByPolicyIdAndCodeIgnoreCase(policyId, candidate));
        return candidate;
    }

    public BucketResponse createBucket(Long policyId, BucketRequest request) {
        if (request.periodType() == com.waad.tba.modules.benefitpolicy.enums.LimitPeriodType.MULTI_YEAR_POLICY
                && (request.periodValue() == null || request.periodValue() < 2)) {
            throw new BusinessRuleException("الدورة متعددة السنوات تتطلب عدد سنوات أكبر من سنة واحدة");
        }
        BenefitPolicy policy = requirePolicy(policyId);
        String code = request.code().trim();
        String name = request.nameAr().trim();
        if (bucketRepository.existsByPolicyIdAndCodeIgnoreCase(policyId, code)) {
            throw new BusinessRuleException("يوجد ضمن هذه الوثيقة وعاء سقف بالكود نفسه: " + code);
        }
        if (bucketRepository.findByPolicyIdAndNameArIgnoreCase(policyId, name).isPresent()) {
            throw new BusinessRuleException("يوجد ضمن هذه الوثيقة وعاء سقف بالاسم نفسه: " + name);
        }
        BenefitGroup group = groupRepository.findById(request.benefitGroupId())
                .orElseThrow(() -> new ResourceNotFoundException("BenefitGroup", "id", request.benefitGroupId()));
        assertSamePolicy(policyId, group.getPolicy().getId(), "مجموعة المنفعة");
        BenefitLimitBucket parent = request.parentBucketId() == null ? null : bucketRepository.findById(request.parentBucketId())
                .orElseThrow(() -> new ResourceNotFoundException("BenefitLimitBucket", "id", request.parentBucketId()));
        if (parent != null) assertSamePolicy(policyId, parent.getPolicy().getId(), "الوعاء الأب");
        BenefitLimitBucket bucket = BenefitLimitBucket.builder()
                .policy(policy).benefitGroup(group).code(code).nameAr(name)
                .contextType(request.contextType()).amountLimit(request.amountLimit()).timesLimit(request.timesLimit())
                .daysLimit(request.daysLimit()).periodType(request.periodType()).periodValue(request.periodValue() == null ? 1 : request.periodValue()).countingMethod(request.countingMethod())
                .consumptionBasis(request.consumptionBasis()).parentBucket(parent)
                .shared(Boolean.TRUE.equals(request.shared())).active(request.active() == null || request.active()).build();
        return bucketResponse(bucketRepository.save(bucket));
    }

    public RuleBucketResponse linkRuleBucket(Long policyId, Long ruleId, RuleBucketRequest request) {
        BenefitPolicyRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("BenefitPolicyRule", "id", ruleId));
        BenefitLimitBucket bucket = bucketRepository.findById(request.bucketId())
                .orElseThrow(() -> new ResourceNotFoundException("BenefitLimitBucket", "id", request.bucketId()));
        assertSamePolicy(policyId, rule.getBenefitPolicy().getId(), "قاعدة الوثيقة");
        assertSamePolicy(policyId, bucket.getPolicy().getId(), "وعاء السقف");
        if (ruleBucketRepository.findByRuleIdAndBucketId(ruleId, request.bucketId()).isPresent()) {
            throw new BusinessRuleException("قاعدة التغطية مرتبطة مسبقًا بهذا الوعاء");
        }
        
        // Enforce X-OR Exclusivity: A rule can only belong to buckets of a SINGLE group
        java.util.List<BenefitRuleBucket> existingLinks = ruleBucketRepository.findByRuleIdOrderByConsumptionOrder(ruleId);
        if (!existingLinks.isEmpty()) {
            Long existingGroupId = existingLinks.get(0).getBucket().getBenefitGroup().getId();
            if (!existingGroupId.equals(bucket.getBenefitGroup().getId())) {
                throw new BusinessRuleException("لا يمكن ربط التصنيف بهذا الوعاء لأنه مسجل كقاعدة مفردة أو ينتمي لمجموعة أخرى. يرجى إزالة الارتباطات القديمة أولاً.");
            }
        }
        BenefitRuleBucket link = BenefitRuleBucket.builder().rule(rule).bucket(bucket)
                .consumptionOrder(request.consumptionOrder() == null ? 1 : request.consumptionOrder())
                .consumptionMode(request.consumptionMode())
                .mandatory(request.mandatory() == null || request.mandatory()).build();
        BenefitRuleBucket saved = ruleBucketRepository.save(link);
        return new RuleBucketResponse(saved.getId(), ruleId, bucketResponse(bucket), saved.getConsumptionOrder(),
                saved.getConsumptionMode(), saved.isMandatory());
    }

    public BucketResponse upsertIndividualLimit(Long policyId, Long ruleId, IndividualLimitRequest request) {
        BenefitPolicyRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("BenefitPolicyRule", "id", ruleId));
        assertSamePolicy(policyId, rule.getBenefitPolicy().getId(), "منفعة الوثيقة");
        java.util.List<BenefitRuleBucket> existingLinks = ruleBucketRepository.findByRuleIdOrderByConsumptionOrder(ruleId);
        BenefitRuleBucket existingLink = existingLinks.stream()
                .filter(link -> link.getBucket().getCode().startsWith("AUTO-BEN-LIMIT-"))
                .findFirst().orElse(null);
                
        boolean noLimit = request.amountLimit() == null && request.timesLimit() == null && request.daysLimit() == null;
        
        // Enforce X-OR Exclusivity: Prevent setting an individual limit if rule is part of a shared group
        if (existingLink == null && !existingLinks.isEmpty() && !noLimit) {
            throw new BusinessRuleException("التصنيف موجود داخل مجموعة منافع، لا يمكن تعيين سقف فردي له. يرجى إزالته من المجموعة أولاً.");
        }
        if (noLimit) {
            if (existingLink != null) {
                BenefitLimitBucket oldBucket = existingLink.getBucket();
                BenefitGroup oldGroup = oldBucket.getBenefitGroup();
                ruleBucketRepository.delete(existingLink);
                bucketRepository.delete(oldBucket);
                groupRepository.delete(oldGroup);
            }
            return null;
        }

        BenefitGroup group;
        BenefitLimitBucket bucket;
        if (existingLink == null) {
            String groupCode = "AUTO-BEN-RULE-" + ruleId;
            group = BenefitGroup.builder().policy(rule.getBenefitPolicy()).code(groupCode).nameAr(rule.getLabel())
                    .contextType(rule.getEncounterType()).aggregationMode(com.waad.tba.modules.benefitpolicy.enums.AggregationMode.INDIVIDUAL)
                    .active(true).build();
            group = groupRepository.save(group);
            bucket = BenefitLimitBucket.builder().policy(rule.getBenefitPolicy()).benefitGroup(group)
                    .code("AUTO-BEN-LIMIT-RULE-" + ruleId).nameAr(rule.getLabel()).contextType(rule.getEncounterType())
                    .periodValue(1).consumptionBasis(com.waad.tba.modules.benefitpolicy.enums.ConsumptionBasis.COMPANY_SHARE)
                    .shared(false).active(true).build();
        } else {
            bucket = existingLink.getBucket();
            group = bucket.getBenefitGroup();
            group.setNameAr(rule.getLabel());
            group.setContextType(rule.getEncounterType());
            group.setActive(true);
            groupRepository.save(group);
        }
        bucket.setNameAr(rule.getLabel());
        bucket.setContextType(rule.getEncounterType());
        bucket.setAmountLimit(request.amountLimit());
        bucket.setTimesLimit(request.timesLimit());
        bucket.setDaysLimit(request.daysLimit());
        bucket.setPeriodType(request.periodType() == null
                ? com.waad.tba.modules.benefitpolicy.enums.LimitPeriodType.POLICY_PERIOD : request.periodType());
        bucket.setCountingMethod(request.countingMethod() == null
                ? com.waad.tba.modules.benefitpolicy.enums.CountingMethod.EACH_UNIT : request.countingMethod());
        bucket = bucketRepository.save(bucket);
        if (existingLink == null) {
            ruleBucketRepository.save(BenefitRuleBucket.builder().rule(rule).bucket(bucket).consumptionOrder(1)
                    .consumptionMode(com.waad.tba.modules.benefitpolicy.enums.ConsumptionMode.PRIMARY).mandatory(true).build());
        }
        return bucketResponse(bucket);
    }

    public void deleteBucket(Long policyId, Long bucketId) {
        BenefitLimitBucket bucket = bucketRepository.findById(bucketId)
                .orElseThrow(() -> new ResourceNotFoundException("BenefitLimitBucket", "id", bucketId));
        assertSamePolicy(policyId, bucket.getPolicy().getId(), "وعاء السقف");
        if (!bucketRepository.findByParentBucketId(bucketId).isEmpty()) {
            throw new BusinessRuleException("لا يمكن حذف وعاء لديه أوعية فرعية");
        }
        if (ruleBucketRepository.existsByBucketId(bucketId)) {
            throw new BusinessRuleException("لا يمكن حذف الوعاء قبل فك جميع روابط قواعد التغطية منه");
        }
        assertBucketHasNoFinancialHistory(bucket);
        bucketRepository.delete(bucket);
    }

    public void deleteGroup(Long policyId, Long groupId) {
        BenefitGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("BenefitGroup", "id", groupId));
        assertSamePolicy(policyId, group.getPolicy().getId(), "مجموعة المنفعة");
        List<BenefitLimitBucket> buckets = bucketRepository.findByBenefitGroupId(groupId);
        boolean generatedOnly = buckets.stream().allMatch(bucket -> bucket.getCode().startsWith("AUTO-GRP-"));
        if (!generatedOnly) {
            throw new BusinessRuleException("لا يمكن حذف المجموعة قبل حذف جميع السقوف المتقدمة التابعة لها");
        }
        buckets.forEach(this::assertBucketHasNoFinancialHistory);
        for (BenefitLimitBucket bucket : buckets) {
            ruleBucketRepository.deleteAll(ruleBucketRepository.findByBucketId(bucket.getId()));
            bucketRepository.delete(bucket);
        }
        groupRepository.delete(group);
    }

    private void assertBucketHasNoFinancialHistory(BenefitLimitBucket bucket) {
        if (consumptionRepository.existsByBucketId(bucket.getId())) {
            throw new BusinessRuleException(
                    "لا يمكن حذف المنفعة أو المجموعة لوجود سجل استهلاك مالي مرتبط بها. يمكن تعطيلها مع الاحتفاظ بالسجل التاريخي: "
                            + bucket.getNameAr());
        }
    }

    public void deleteLink(Long policyId, Long linkId) {
        BenefitRuleBucket link = ruleBucketRepository.findById(linkId)
                .orElseThrow(() -> new ResourceNotFoundException("BenefitRuleBucket", "id", linkId));
        assertSamePolicy(policyId, link.getRule().getBenefitPolicy().getId(), "رابط قاعدة التغطية");
        ruleBucketRepository.delete(link);
    }

    private BenefitPolicy requirePolicy(Long id) {
        return policyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BenefitPolicy", "id", id));
    }
    private void assertSamePolicy(Long expected, Long actual, String label) {
        if (!Objects.equals(expected, actual)) throw new BusinessRuleException(label + " لا يتبع الوثيقة المحددة");
    }
    private GroupResponse groupResponse(BenefitGroup g) {
        return new GroupResponse(g.getId(), g.getCode(), g.getNameAr(), g.getContextType(), g.getAggregationMode(),
                g.getCoveragePercent(), g.getCopayPercentage(), g.isRequiresPreApproval(), g.getNotes(), g.getSourceClause(), g.isActive());
    }
    private BucketResponse bucketResponse(BenefitLimitBucket b) {
        return new BucketResponse(b.getId(), b.getBenefitGroup().getId(), b.getCode(), b.getNameAr(), b.getContextType(),
                b.getAmountLimit(), b.getTimesLimit(), b.getDaysLimit(), b.getPeriodType(), b.getPeriodValue(), b.getCountingMethod(),
                b.getConsumptionBasis(), b.getParentBucket() == null ? null : b.getParentBucket().getId(),
                b.isShared(), b.isActive());
    }
}
