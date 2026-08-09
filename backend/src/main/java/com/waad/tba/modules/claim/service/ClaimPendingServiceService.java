package com.waad.tba.modules.claim.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.claim.dto.*;
import com.waad.tba.modules.claim.entity.*;
import com.waad.tba.modules.claim.repository.*;
import com.waad.tba.modules.claim.mapper.ClaimMapper;
import com.waad.tba.modules.medicaldictionary.dto.V50ClassificationInput;
import com.waad.tba.modules.medicaldictionary.service.V50MedicalClassificationEngine;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.modules.providercontract.repository.*;
import com.waad.tba.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ClaimPendingServiceService {
    private final ClaimRepository claimRepository;
    private final ClaimPendingServiceRepository pendingRepository;
    private final ClaimMapper claimMapper;
    private final MedicalCategoryRepository categoryRepository;
    private final ProviderContractRepository contractRepository;
    private final ProviderContractPricingItemRepository pricingRepository;
    private final V50MedicalClassificationEngine classifier;
    private final AuthorizationService authorizationService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Transactional
    public PendingServiceResponse create(Long claimId, PendingServiceCreateRequest request) {
        Claim claim = claimRepository.findByIdForUpdate(claimId)
                .orElseThrow(() -> new BusinessRuleException("المطالبة غير موجودة"));
        if (claim.getStatus() != ClaimStatus.UNDER_REVIEW) {
            throw new BusinessRuleException("لا يمكن إدخال خدمة مبدئية إلا بعد استلام المطالبة للمراجعة");
        }
        categoryRepository.findActiveById(request.getProposedCategoryId())
                .orElseThrow(() -> new BusinessRuleException("التصنيف الطبي المقترح غير موجود أو غير نشط"));
        var actor = authorizationService.getCurrentUser();
        if (actor == null) throw new BusinessRuleException("المستخدم غير معروف");

        var decision = classifier.classify(new V50ClassificationInput(
                request.getServiceName(), request.getServiceCode(), List.of(), request.getServiceCode(),
                null, List.of(), null, claim.getProviderName()));

        ClaimPendingService saved = pendingRepository.saveAndFlush(ClaimPendingService.builder()
                .claim(claim).providerId(claim.getProviderId())
                .proposedServiceCode(blank(request.getServiceCode()))
                .proposedServiceName(request.getServiceName().trim())
                .proposedCategoryId(request.getProposedCategoryId())
                .proposedUnitPrice(request.getProposedUnitPrice())
                .dictionaryReleaseId(decision.releaseId()).dictionaryVersion(decision.dictionaryVersion())
                .dictionaryConceptCode(decision.conceptCode()).classificationMethod(decision.matchMethod())
                .classificationReason(decision.reason()).classificationEvidenceId(decision.evidenceId())
                .enteredBy(actor.getId()).status(PendingServiceStatus.PRELIMINARY).build());

        claim.addLine(ClaimLine.builder()
                .claim(claim)
                .pendingServiceId(saved.getId())
                .serviceCode(saved.getProposedServiceCode())
                .serviceName(saved.getProposedServiceName())
                .serviceCategoryId(saved.getProposedCategoryId())
                .quantity(1)
                .requestedUnitPrice(saved.getProposedUnitPrice())
                .unitPrice(saved.getProposedUnitPrice())
                .rejected(false)
                .build());
        claimMapper.recalculateForApproval(claim);
        claimRepository.save(claim);
        return PendingServiceResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<PendingServiceResponse> list(Long claimId) {
        return pendingRepository.findByClaimIdOrderByIdAsc(claimId).stream()
                .map(PendingServiceResponse::from).toList();
    }

    @Transactional
    public PendingServiceResponse decide(Long claimId, Long pendingId, PendingServiceDecisionRequest request) {
        Claim claim = claimRepository.findByIdForUpdate(claimId)
                .orElseThrow(() -> new BusinessRuleException("المطالبة غير موجودة"));
        if (claim.getStatus() != ClaimStatus.UNDER_REVIEW) {
            throw new BusinessRuleException("قرار رئيس القسم مسموح فقط أثناء المراجعة");
        }
        ClaimPendingService pending = pendingRepository.findByIdAndClaimId(pendingId, claimId)
                .orElseThrow(() -> new BusinessRuleException("الخدمة المعلقة غير موجودة في هذه المطالبة"));
        if (pending.getStatus().isResolved()) {
            throw new BusinessRuleException("تم حسم هذه الخدمة سابقاً ولا يجوز استبدال القرار");
        }
        if (request.getDecision() == PendingServiceStatus.PRELIMINARY) {
            throw new BusinessRuleException("PRELIMINARY ليست قراراً نهائياً");
        }
        var actor = authorizationService.getCurrentUser();
        if (actor == null) throw new BusinessRuleException("المستخدم غير معروف");
        assertFinalApprover(actor.getUserType());
        PendingServiceStatus from = pending.getStatus();

        pending.setFinalServiceCode(or(request.getFinalServiceCode(), pending.getProposedServiceCode()));
        pending.setFinalServiceName(or(request.getFinalServiceName(), pending.getProposedServiceName()));
        pending.setFinalCategoryId(request.getFinalCategoryId() != null
                ? request.getFinalCategoryId() : pending.getProposedCategoryId());
        pending.setFinalUnitPrice(request.getFinalUnitPrice() != null
                ? request.getFinalUnitPrice() : pending.getProposedUnitPrice());

        if (request.getDecision() == PendingServiceStatus.APPROVED_CLAIM_ONLY
                || request.getDecision() == PendingServiceStatus.APPROVED_FOR_CONTRACT) {
            if (pending.getFinalServiceName() == null || pending.getFinalServiceName().isBlank()) {
                throw new BusinessRuleException("اسم الخدمة النهائي مطلوب");
            }
            categoryRepository.findActiveById(pending.effectiveCategoryId())
                    .orElseThrow(() -> new BusinessRuleException("التصنيف النهائي غير موجود أو غير نشط"));
            if (pending.getFinalUnitPrice() == null || pending.getFinalUnitPrice().signum() <= 0) {
                throw new BusinessRuleException("السعر النهائي يجب أن يكون أكبر من صفر");
            }
        }

        if (request.getDecision() == PendingServiceStatus.LINKED_EXISTING) {
            if (request.getLinkedPricingItemId() == null) throw new BusinessRuleException("اختر سعر العقد المراد ربطه");
            ProviderContractPricingItem linked = pricingRepository.findEffectiveInContractById(
                    claim.getProviderContractId(), request.getLinkedPricingItemId(), claim.getServiceDate())
                    .orElseThrow(() -> new BusinessRuleException("سعر العقد المحدد غير ساري في تاريخ الخدمة"));
            pending.setLinkedPricingItemId(linked.getId());
            pending.setFinalServiceCode(linked.getServiceCode());
            pending.setFinalServiceName(linked.getServiceName());
            pending.setFinalCategoryId(linked.getMedicalCategory() == null ? null : linked.getMedicalCategory().getId());
            pending.setFinalUnitPrice(linked.getContractPrice());
            if (pending.getFinalCategoryId() == null) {
                throw new BusinessRuleException("سعر العقد المحدد غير مربوط بتصنيف طبي صالح");
            }
            categoryRepository.findActiveById(pending.getFinalCategoryId())
                    .orElseThrow(() -> new BusinessRuleException("تصنيف سعر العقد غير موجود أو غير نشط"));
        } else if (request.getDecision() == PendingServiceStatus.APPROVED_FOR_CONTRACT) {
            pending.setLinkedPricingItemId(createContractPrice(claim, pending, request.getContractEffectiveFrom()).getId());
        }

        pending.setStatus(request.getDecision());
        pending.setDecisionReason(request.getReason().trim());
        if (request.getDecision().isResolved()) {
            pending.setDecidedBy(actor.getId());
            pending.setDecidedAt(LocalDateTime.now());
        }
        ClaimPendingService saved = pendingRepository.save(pending);
        appendDecision(saved, from, actor.getId(), request.getReason());
        claimMapper.recalculateForApproval(claim);
        claimRepository.save(claim);
        return PendingServiceResponse.from(saved);
    }

    private ProviderContractPricingItem createContractPrice(Claim claim, ClaimPendingService pending, LocalDate requestedFrom) {
        var contract = contractRepository.findById(claim.getProviderContractId())
                .orElseThrow(() -> new BusinessRuleException("عقد المطالبة غير موجود"));
        LocalDate from = requestedFrom != null ? requestedFrom : claim.getServiceDate();
        if (from.isBefore(contract.getStartDate()) || contract.getEndDate() != null && from.isAfter(contract.getEndDate())) {
            throw new BusinessRuleException("تاريخ السعر الجديد خارج مدة العقد");
        }
        var category = categoryRepository.findActiveById(pending.effectiveCategoryId())
                .orElseThrow(() -> new BusinessRuleException("التصنيف النهائي غير موجود أو غير نشط"));
        Optional<ProviderContractPricingItem> existing = pending.getFinalServiceCode() != null
                ? pricingRepository.findEffectiveInContractByCode(contract.getId(), pending.getFinalServiceCode(), from)
                : pricingRepository.findEffectiveInContractByName(contract.getId(), pending.getFinalServiceName(), from);
        existing.ifPresent(old -> {
            if (old.getEffectiveFrom().isBefore(from)) old.setEffectiveTo(from); else old.setActive(false);
            pricingRepository.save(old);
        });
        return pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(contract).serviceCode(pending.getFinalServiceCode()).serviceName(pending.getFinalServiceName())
                .medicalCategory(category).categoryName(category.getName())
                .basePrice(pending.getFinalUnitPrice()).contractPrice(pending.getFinalUnitPrice())
                .effectiveFrom(from).active(true).classificationSource("CLAIM_REVIEW_HEAD")
                .dictionaryReleaseId(pending.getDictionaryReleaseId()).dictionaryVersion(pending.getDictionaryVersion())
                .dictionaryConceptCode(pending.getDictionaryConceptCode())
                .classificationMethodV50(pending.getClassificationMethod())
                .classificationEvidenceId(pending.getClassificationEvidenceId()).build());
    }

    private void appendDecision(ClaimPendingService p, PendingServiceStatus from, Long actorId, String reason) {
        try {
            String snapshot = objectMapper.writeValueAsString(PendingServiceResponse.from(p));
            jdbc.update("INSERT INTO claim_pending_service_decisions " +
                    "(pending_service_id, from_status, to_status, reason, actor_id, snapshot_json) " +
                    "VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb))",
                    p.getId(), from.name(), p.getStatus().name(), reason.trim(), actorId, snapshot);
        } catch (Exception e) {
            throw new IllegalStateException("تعذر تسجيل قرار الخدمة المعلقة", e);
        }
    }

    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String or(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }

    private void assertFinalApprover(String userType) {
        if (!Set.of("SUPER_ADMIN", "INSURANCE_MANAGER", "MEDICAL_REVIEW_HEAD").contains(userType)) {
            throw new BusinessRuleException(
                    "اعتماد الخدمة مسموح فقط لرئيس قسم المراجعين أو مدير التأمين أو مدير النظام");
        }
    }
}
