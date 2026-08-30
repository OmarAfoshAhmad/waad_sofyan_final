package com.waad.tba.modules.claim.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.claim.api.ClaimApiMapper;
import com.waad.tba.modules.claim.api.request.CreateClaimRequest;
import com.waad.tba.modules.claim.api.request.DirectClaimEntryRequest;
import com.waad.tba.modules.claim.dto.ClaimViewDto;
import com.waad.tba.modules.member.service.MemberContextResolver;
import com.waad.tba.modules.visit.dto.VisitCreateDto;
import com.waad.tba.modules.visit.entity.VisitType;
import com.waad.tba.modules.visit.service.VisitService;

import lombok.RequiredArgsConstructor;

/** Atomic boundary for the internal screen that creates both visit and claim. */
@Service
@RequiredArgsConstructor
public class DirectClaimEntryService {

    private final VisitService visitService;
    private final ClaimService claimService;
    private final ClaimApiMapper claimApiMapper;
    private final MemberContextResolver memberContextResolver;

    @Transactional
    public ClaimViewDto create(DirectClaimEntryRequest request) {
        CreateClaimRequest claimRequest = request.getClaim();
        if (claimRequest.getVisitId() != null) {
            throw new BusinessRuleException("مسار الإدخال المباشر ينشئ الزيارة بنفسه ولا يقبل زيارة سابقة");
        }
        if (claimRequest.getMemberId() == null || claimRequest.getProviderId() == null
                || claimRequest.getServiceDate() == null) {
            throw new BusinessRuleException("المستفيد ومقدم الخدمة وتاريخ الخدمة مطلوبة لإنشاء المطالبة");
        }
        if (claimRequest.getDoctorName() == null || claimRequest.getDoctorName().isBlank()) {
            throw new BusinessRuleException("اسم الطبيب مطلوب؛ لا يجوز تسجيل طبيب افتراضي في مطالبة مالية");
        }

        var datedMember = memberContextResolver.resolveForOrFail(
                claimRequest.getMemberId(), claimRequest.getServiceDate());
        if (!request.getEmployerId().equals(datedMember.employer().getId())) {
            throw new BusinessRuleException("المستفيد لا يتبع جهة عمل الدفعة في تاريخ الخدمة");
        }

        var visit = visitService.create(VisitCreateDto.builder()
                .memberId(claimRequest.getMemberId())
                .providerId(claimRequest.getProviderId())
                .visitDate(claimRequest.getServiceDate())
                .doctorName(claimRequest.getDoctorName().trim())
                .diagnosis(claimRequest.getDiagnosisDescription())
                .notes(claimRequest.getNotes())
                .visitType(VisitType.LEGACY_BACKLOG)
                .build());

        var claimDto = claimApiMapper.toCreateDto(claimRequest);
        claimDto.setVisitId(visit.getId());
        return claimService.createClaim(claimDto);
    }
}
