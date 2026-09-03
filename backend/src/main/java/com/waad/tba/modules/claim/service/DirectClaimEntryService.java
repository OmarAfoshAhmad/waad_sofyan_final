package com.waad.tba.modules.claim.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;



import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.claim.api.ClaimApiMapper;
import com.waad.tba.modules.claim.api.request.CreateClaimRequest;
import com.waad.tba.modules.claim.api.request.DirectClaimEntryRequest;
import com.waad.tba.modules.claim.dto.ClaimViewDto;
import com.waad.tba.modules.claim.mapper.ClaimMapper;
import com.waad.tba.modules.claim.repository.ClaimRepository;
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
    private final ClaimProviderEmployerAccessService employerAccess;
    private final DirectClaimEntryFingerprint fingerprints;
    private final ClaimRepository claimRepository;
    private final ClaimMapper claimMapper;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public ClaimViewDto create(DirectClaimEntryRequest request) {
        String idempotencyKey = requireIdempotencyKey(request.getIdempotencyKey());
        // Canonicalize before hashing so harmless surrounding whitespace cannot
        // turn a retry of the same command into a different payload.
        request.setIdempotencyKey(idempotencyKey);
        String fingerprint = fingerprints.of(request);

        // PostgreSQL transaction advisory lock serializes identical commands,
        // including the first insert race before the unique index can help.
        jdbcTemplate.queryForObject(
                "SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(?, 0))",
                Integer.class, idempotencyKey);

        var replay = claimRepository.findByDirectEntryIdempotencyKey(idempotencyKey);
        if (replay.isPresent()) {
            if (!fingerprint.equals(replay.get().getDirectEntryRequestFingerprint())) {
                throw new BusinessRuleException("مفتاح إعادة الإرسال مستخدم لبيانات مطالبة مختلفة");
            }
            return claimMapper.toViewDto(replay.get());
        }

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

        // Resolved once, here, and handed to claim creation below. It used to be
        // resolved again inside createClaim, so one request asked the same dated
        // question twice and two answers could in principle disagree.
        var datedMember = memberContextResolver.resolveForOrFail(
                claimRequest.getMemberId(), claimRequest.getServiceDate());
        employerAccess.requireMemberBelongsToEmployer(
                request.getEmployerId(), datedMember.employer(), claimRequest.getServiceDate());

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
        ClaimViewDto created = claimService.createClaim(claimDto, datedMember);
        var persisted = claimRepository.findById(created.getId())
                .orElseThrow(() -> new IllegalStateException("تعذر تثبيت مفتاح أمر المطالبة المنشأة"));
        persisted.setDirectEntryIdempotencyKey(idempotencyKey);
        persisted.setDirectEntryRequestFingerprint(fingerprint);
        claimRepository.save(persisted);
        return created;
    }

    private String requireIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException("مفتاح إعادة الإرسال مطلوب لإنشاء المطالبة بأمان");
        }
        String normalized = value.trim();
        if (normalized.length() > 120) {
            throw new BusinessRuleException("مفتاح إعادة الإرسال أطول من الحد المسموح");
        }
        return normalized;
    }

}
