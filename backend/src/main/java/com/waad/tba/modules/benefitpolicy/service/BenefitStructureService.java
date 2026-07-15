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
        String code = request.code().trim();
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
        return groupResponse(groupRepository.save(group));
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
        BenefitRuleBucket link = BenefitRuleBucket.builder().rule(rule).bucket(bucket)
                .consumptionOrder(request.consumptionOrder() == null ? 1 : request.consumptionOrder())
                .consumptionMode(request.consumptionMode())
                .mandatory(request.mandatory() == null || request.mandatory()).build();
        BenefitRuleBucket saved = ruleBucketRepository.save(link);
        return new RuleBucketResponse(saved.getId(), ruleId, bucketResponse(bucket), saved.getConsumptionOrder(),
                saved.getConsumptionMode(), saved.isMandatory());
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
        bucketRepository.delete(bucket);
    }

    public void deleteGroup(Long policyId, Long groupId) {
        BenefitGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("BenefitGroup", "id", groupId));
        assertSamePolicy(policyId, group.getPolicy().getId(), "مجموعة المنفعة");
        if (bucketRepository.existsByBenefitGroupId(groupId)) {
            throw new BusinessRuleException("لا يمكن حذف المجموعة قبل حذف جميع الأوعية التابعة لها");
        }
        groupRepository.delete(group);
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
