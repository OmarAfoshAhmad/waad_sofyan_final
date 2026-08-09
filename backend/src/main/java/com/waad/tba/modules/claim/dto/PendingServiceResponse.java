package com.waad.tba.modules.claim.dto;

import com.waad.tba.modules.claim.entity.ClaimPendingService;
import com.waad.tba.modules.claim.entity.PendingServiceStatus;
import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record PendingServiceResponse(
        Long id, Long claimId, Long providerId,
        String proposedServiceCode, String proposedServiceName,
        Long proposedCategoryId, String proposedCategoryCode, String proposedCategoryName,
        Boolean newCategoryRequested, BigDecimal proposedUnitPrice,
        PendingServiceStatus status,
        Long dictionaryReleaseId, String dictionaryVersion,
        String dictionaryConceptCode, String classificationMethod,
        String classificationReason, Long classificationEvidenceId,
        String finalServiceCode, String finalServiceName,
        Long finalCategoryId, BigDecimal finalUnitPrice,
        Long linkedPricingItemId, String decisionReason,
        Long enteredBy, Long decidedBy, LocalDateTime createdAt, LocalDateTime decidedAt,
        Long version) {
    public static PendingServiceResponse from(ClaimPendingService e) {
        return PendingServiceResponse.builder()
                .id(e.getId()).claimId(e.getClaim().getId()).providerId(e.getProviderId())
                .proposedServiceCode(e.getProposedServiceCode()).proposedServiceName(e.getProposedServiceName())
                .proposedCategoryId(e.getProposedCategoryId()).proposedCategoryCode(e.getProposedCategoryCode())
                .proposedCategoryName(e.getProposedCategoryName()).newCategoryRequested(e.getNewCategoryRequested())
                .proposedUnitPrice(e.getProposedUnitPrice())
                .status(e.getStatus()).dictionaryReleaseId(e.getDictionaryReleaseId())
                .dictionaryVersion(e.getDictionaryVersion()).dictionaryConceptCode(e.getDictionaryConceptCode())
                .classificationMethod(e.getClassificationMethod()).classificationReason(e.getClassificationReason())
                .classificationEvidenceId(e.getClassificationEvidenceId())
                .finalServiceCode(e.getFinalServiceCode()).finalServiceName(e.getFinalServiceName())
                .finalCategoryId(e.getFinalCategoryId()).finalUnitPrice(e.getFinalUnitPrice())
                .linkedPricingItemId(e.getLinkedPricingItemId()).decisionReason(e.getDecisionReason())
                .enteredBy(e.getEnteredBy()).decidedBy(e.getDecidedBy()).createdAt(e.getCreatedAt())
                .decidedAt(e.getDecidedAt()).version(e.getVersion()).build();
    }
}
