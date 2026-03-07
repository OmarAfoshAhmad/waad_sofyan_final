package com.waad.tba.modules.provider.service;

import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.provider.dto.ProviderVisitRegisterRequest;
import com.waad.tba.modules.provider.dto.ProviderVisitResponse;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.entity.VisitStatus;
import com.waad.tba.modules.visit.entity.VisitType;
import com.waad.tba.modules.visit.repository.VisitRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.security.AuthorizationService;
import com.waad.tba.services.pdf.HtmlToPdfService;
import com.waad.tba.services.pdf.PdfTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

/**
 * Provider Visit Service
 * 
 * Handles visit registration and management for the Provider Portal.
 * 
 * NEW FLOW (2026-01-13):
 * 1. Eligibility Check → Register Visit → Create Claim/Pre-Auth
 * 2. Visit is the central entity linking Member to Claim/Pre-Auth
 * 3. Member cannot be changed once visit is registered
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("deprecation")
public class ProviderVisitService {
    
    private final VisitRepository visitRepository;
    private final MemberRepository memberRepository;
    private final ProviderRepository providerRepository;
    private final PreAuthorizationRepository preAuthorizationRepository;
    private final AuthorizationService authorizationService;
    private final UserRepository userRepository;
    private final PdfTemplateService pdfTemplateService;
    private final HtmlToPdfService htmlToPdfService;
    
    /**
     * Register a new visit for a member (after eligibility check).
     *  
     * @param request Visit registration request
     * @param providerUsername Username of the provider registering the visit
     * @return ProviderVisitResponse with visit details
     */
    @Transactional
    public ProviderVisitResponse registerVisit(ProviderVisitRegisterRequest request, String providerUsername) {
        log.info("📋 Registering visit for member ID: {}", request.getMemberId());
        
        // 1. Validate member exists and is active
        Member member = memberRepository.findById(request.getMemberId())
            .orElse(null);
        
        if (member == null) {
            return ProviderVisitResponse.builder()
                .success(false)
                .message("العضو غير موجود")
                .build();
        }
        
        if (member.getStatus() != Member.MemberStatus.ACTIVE) {
            return ProviderVisitResponse.builder()
                .success(false)
                .message("العضو غير نشط. لا يمكن تسجيل زيارة.")
                .build();
        }
        
        // 2. Resolve Provider Context
        // Priority 1: From authenticated user (for PROVIDER role)
        // Priority 2: From request (for ADMIN/INSURANCE overrides)
        Long providerId = null;
        Provider provider = null;

        // Try to look up user by username to get providerId
        User currentUser = userRepository.findByUsername(providerUsername).orElse(null);
        if (currentUser != null && currentUser.getProviderId() != null) {
            providerId = currentUser.getProviderId();
            log.info("🏥 Resolved provider from user context: user={}, providerId={}", 
                     providerUsername, providerId);
        } else {
             // Fallback to request if no user context or user has no providerId
             providerId = request.getProviderId();
             log.info("⚠️ No provider found in user context for '{}', using request providerId: {}", 
                     providerUsername, providerId);
        }

        if (providerId != null) {
            provider = providerRepository.findById(providerId).orElse(null);
        }
        
        // Block if no provider context found (Critical for Architecture)
        if (provider == null) {
             return ProviderVisitResponse.builder()
                .success(false)
                .message("خطأ في النظام: لا يوجد مقدم خدمة مرتبط بالمستخدم الحالي")
                .build();
        }
        
        // 3. Determine visit type
        VisitType visitType = VisitType.OUTPATIENT; // Default
        if (request.getVisitType() != null) {
            try {
                visitType = VisitType.valueOf(request.getVisitType().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid visit type: {}, using default OUTPATIENT", request.getVisitType());
            }
        }
        
        // 4. Create visit
        Visit visit = Visit.builder()
            .member(member)
            .employer(member.getEmployer())
            .providerId(providerId)
            .visitDate(request.getVisitDate() != null ? request.getVisitDate() : LocalDate.now())
            .visitType(visitType)
            .status(VisitStatus.REGISTERED)
            .eligibilityCheckId(request.getEligibilityCheckId())
            .doctorName(request.getDoctorName())
            .specialty(request.getSpecialty())
            .diagnosis(request.getDiagnosis())
            .notes(request.getNotes())
            .active(true)
            .build();
        
        visit = visitRepository.save(visit);
        
        log.info("✅ Visit registered: id={}, member={}, type={}", 
                 visit.getId(), member.getFullName(), visitType);
        
        // 5. Build response
        return mapToResponse(visit, member, provider, true);
    }
    
    /**
     * Get visits for the provider's visit log.
     * 
     * @param providerId Provider ID (optional, null = all visits for admin)
     * @param memberId Member ID filter (optional)
     * @param memberName Member name/card/civilId search (optional)
     * @param status Status filter (optional)
     * @param fromDate Date range start (optional)
     * @param toDate Date range end (optional)
     * @param pageable Pagination parameters
     * @return Page of ProviderVisitResponse
     */
    @Transactional(readOnly = true)
    public Page<ProviderVisitResponse> getVisitLog(
            Long providerId,
            Long memberId,
            String memberName,
            String status,
            VisitType visitType,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable) {
        
        // Normalize empty strings to null for proper query handling
        String normalizedMemberName = (memberName != null && !memberName.trim().isEmpty()) 
            ? memberName.trim() : null;
        String normalizedStatus = (status != null && !status.trim().isEmpty()) 
            ? status.trim() : null;
        
        log.debug("📋 Fetching visit log: provider={}, member={}, memberName={}, status={}, visitType={}, from={}, to={}",
              providerId, memberId, normalizedMemberName, normalizedStatus, visitType, fromDate, toDate);

        // Validate status if provided
        if (normalizedStatus != null) {
            try {
                VisitStatus.valueOf(normalizedStatus);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid filter status: {}, ignoring", normalizedStatus);
                normalizedStatus = null;
            }
        }

        final String finalNormalizedStatus = normalizedStatus;
        
        Specification<Visit> specification = (root, query, cb) -> {
            query.distinct(true);
            Join<Visit, Member> memberJoin = root.join("member", JoinType.LEFT);
            java.util.List<Predicate> predicates = new java.util.ArrayList<>();

            predicates.add(cb.isTrue(root.get("active")));

            if (providerId != null) {
                predicates.add(cb.equal(root.get("providerId"), providerId));
            }
            if (memberId != null) {
                predicates.add(cb.equal(memberJoin.get("id"), memberId));
            }
            if (normalizedMemberName != null) {
                String pattern = "%" + normalizedMemberName.toLowerCase() + "%";
                Predicate byFullName = cb.like(cb.lower(memberJoin.get("fullName")), pattern);
                Predicate byCardNumber = cb.like(cb.lower(memberJoin.get("cardNumber")), pattern);
                Predicate byCivilId = cb.like(cb.lower(memberJoin.get("civilId")), pattern);
                predicates.add(cb.or(byFullName, byCardNumber, byCivilId));
            }
            if (finalNormalizedStatus != null) {
                VisitStatus statusEnum = VisitStatus.valueOf(finalNormalizedStatus);
                predicates.add(cb.equal(root.get("status"), statusEnum));
            }
            if (visitType != null) {
                predicates.add(cb.equal(root.get("visitType"), visitType));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("visitDate"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("visitDate"), toDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Visit> visits = visitRepository.findAll(specification, pageable);
        
        return visits.map(v -> {
            // Load provider for each visit to ensure providerName is populated
            Provider visitProvider = null;
            if (v.getProviderId() != null) {
                visitProvider = providerRepository.findById(v.getProviderId()).orElse(null);
            }
            return mapToResponse(v, v.getMember(), visitProvider, true);
        });
    }
    
    /**
     * Get a single visit by ID.
     * 
     * @param visitId Visit ID
     * @return ProviderVisitResponse
     */
    @Transactional(readOnly = true)
    public ProviderVisitResponse getVisitById(Long visitId) {
        Visit visit = visitRepository.findById(visitId).orElse(null);
        
        if (visit == null) {
            return ProviderVisitResponse.builder()
                .success(false)
                .message("الزيارة غير موجودة")
                .build();
        }
        
        Provider provider = null;
        if (visit.getProviderId() != null) {
            provider = providerRepository.findById(visit.getProviderId()).orElse(null);
        }
        
        return mapToResponse(visit, visit.getMember(), provider, true);
    }
    
    /**
     * Map Visit entity to ProviderVisitResponse DTO.
     */
    private ProviderVisitResponse mapToResponse(Visit visit, Member member, Provider provider, boolean success) {
        // Count linked claims and pre-authorizations
        int claimCount = visit.getClaims() != null ? visit.getClaims().size() : 0;
        
        // Get latest claim status
        Long latestClaimId = null;
        String latestClaimStatus = null;
        String latestClaimStatusLabel = null;
        if (visit.getClaims() != null && !visit.getClaims().isEmpty()) {
            var latestClaim = visit.getClaims().stream()
                .max((c1, c2) -> {
                    if (c1.getCreatedAt() == null && c2.getCreatedAt() == null) return 0;
                    if (c1.getCreatedAt() == null) return -1;
                    if (c2.getCreatedAt() == null) return 1;
                    return c1.getCreatedAt().compareTo(c2.getCreatedAt());
                })
                .orElse(null);
            if (latestClaim != null) {
                latestClaimId = latestClaim.getId();
                latestClaimStatus = latestClaim.getStatus() != null ? latestClaim.getStatus().name() : null;
                latestClaimStatusLabel = latestClaim.getStatus() != null ? latestClaim.getStatus().getArabicLabel() : null;
            }
        }
        
        // Count pre-authorizations by visitId and get latest status
        int preAuthCount = 0;
        Long latestPreAuthId = null;
        String latestPreAuthStatus = null;
        String latestPreAuthStatusLabel = null;
        try {
            List<PreAuthorization> preAuths = preAuthorizationRepository.findByVisitIdAndActiveTrue(visit.getId());
            preAuthCount = preAuths != null ? preAuths.size() : 0;
            
            // Get latest pre-auth status
            if (preAuths != null && !preAuths.isEmpty()) {
                var latestPreAuth = preAuths.stream()
                    .max((p1, p2) -> {
                        if (p1.getCreatedAt() == null && p2.getCreatedAt() == null) return 0;
                        if (p1.getCreatedAt() == null) return -1;
                        if (p2.getCreatedAt() == null) return 1;
                        return p1.getCreatedAt().compareTo(p2.getCreatedAt());
                    })
                    .orElse(null);
                if (latestPreAuth != null) {
                    latestPreAuthId = latestPreAuth.getId();
                    latestPreAuthStatus = latestPreAuth.getStatus() != null ? latestPreAuth.getStatus().name() : null;
                    latestPreAuthStatusLabel = latestPreAuth.getStatus() != null ? latestPreAuth.getStatus().getArabicLabel() : null;
                }
            }
        } catch (Exception e) {
            log.debug("Could not count pre-auths for visit: {}", visit.getId());
        }
        
        ProviderVisitResponse.ProviderVisitResponseBuilder builder = ProviderVisitResponse.builder()
            .success(success)
            .message(success ? "تم بنجاح" : null)
            .visitId(visit.getId())
            .visitDate(visit.getVisitDate())
            .visitType(visit.getVisitType() != null ? visit.getVisitType().name() : null)
            .visitTypeLabel(visit.getVisitType() != null ? visit.getVisitType().getArabicLabel() : null)
            .status(visit.getStatus() != null ? visit.getStatus().name() : null)
            .statusLabel(visit.getStatus() != null ? visit.getStatus().getLabelAr() : null)
            .doctorName(visit.getDoctorName())
            .specialty(visit.getSpecialty())
            .diagnosis(visit.getDiagnosis())
            .treatment(visit.getTreatment())
            .totalAmount(visit.getTotalAmount())
            .notes(visit.getNotes())
            .canCreateClaim(visit.allowsClaimCreation())
            .canCreatePreAuth(visit.allowsPreAuthCreation())
            .claimCount(claimCount)
            .latestClaimId(latestClaimId)
            .latestClaimStatus(latestClaimStatus)
            .latestClaimStatusLabel(latestClaimStatusLabel)
            .preAuthCount(preAuthCount)
            .latestPreAuthId(latestPreAuthId)
            .latestPreAuthStatus(latestPreAuthStatus)
            .latestPreAuthStatusLabel(latestPreAuthStatusLabel)
            .createdAt(visit.getCreatedAt())
            .updatedAt(visit.getUpdatedAt());
        
        // Member info
        if (member != null) {
            builder.memberId(member.getId())
                   .memberName(member.getFullName())
                   .memberCivilId(member.getNationalNumber())
                   .memberBarcode(member.getBarcode())
                   .memberCardNumber(member.getCardNumber())
                   .memberStatus(member.getStatus() != null ? member.getStatus().name() : null);
            
            if (member.getEmployer() != null) {
                builder.employerName(member.getEmployer().getName());
            }
        }
        
        // Provider info
        if (provider != null) {
            builder.providerId(provider.getId())
                   .providerName(provider.getName());
        } else if (visit.getProviderId() != null) {
            builder.providerId(visit.getProviderId());
        }
        
        return builder.build();
    }

    /**
     * Generate PDF for a visit.
     * 
     * SECURITY: Provider can only access their own visits.
     * 
     * @param visitId Visit ID
     * @param providerId Provider ID (from JWT)
     * @return PDF bytes
     */
    @Transactional(readOnly = true)
    public byte[] generateVisitPdf(Long visitId, Long providerId) {
        log.info("[PROVIDER-VISIT] Generating PDF for visit {} (provider={})", visitId, providerId);
        
        Visit visit = visitRepository.findById(visitId)
            .orElseThrow(() -> new IllegalArgumentException("Visit not found: " + visitId));
        
        // Security: Verify provider owns this visit
        if (!visit.getProviderId().equals(providerId)) {
            throw new SecurityException("Provider " + providerId + " cannot access visit " + visitId);
        }
        
        Member member = visit.getMember();
        Provider provider = providerRepository.findById(providerId).orElse(null);
        
        // Prepare data for PDF template
        Map<String, Object> data = new HashMap<>();
        data.put("visitId", visit.getId());
        data.put("visitDate", visit.getVisitDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        data.put("visitTime", visit.getCreatedAt() != null ? visit.getCreatedAt().toLocalTime().toString() : "");
        data.put("visitType", visit.getVisitType() != null ? visit.getVisitType().name() : "");
        data.put("visitStatus", visit.getStatus() != null ? visit.getStatus().name() : "");
        data.put("serviceName", visit.getMedicalServiceName());
        data.put("totalAmount", visit.getTotalAmount() != null ? visit.getTotalAmount() : 0.0);
        data.put("notes", visit.getNotes() != null ? visit.getNotes() : "");
        
        if (member != null) {
            data.put("memberName", member.getFullName());
            data.put("memberCivilId", member.getNationalNumber());
            data.put("memberBarcode", member.getBarcode());
            data.put("memberCardNumber", member.getCardNumber());
            if (member.getEmployer() != null) {
                data.put("employerName", member.getEmployer().getName());
            }
        }
        
        if (provider != null) {
            data.put("providerName", provider.getName());
        }
        
        data.put("generatedDate", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        
        try {
            // Process template
            String html = pdfTemplateService.processTemplate("pdf/visit-report", data, java.util.Locale.forLanguageTag("ar"));
            
            // Convert to PDF
            byte[] pdfBytes = htmlToPdfService.convertHtmlToPdf(html);
            
            log.info("[PROVIDER-VISIT] PDF generated: {} bytes", pdfBytes.length);
            return pdfBytes;
        } catch (IOException e) {
            log.error("[PROVIDER-VISIT] PDF generation failed", e);
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage(), e);
        }
    }
}

