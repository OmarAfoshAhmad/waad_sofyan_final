package com.waad.tba.modules.provider.service;

import com.waad.tba.modules.provider.dto.ProviderEligibilityRequest;
import com.waad.tba.modules.provider.dto.ProviderEligibilityResponse;
import com.waad.tba.modules.member.dto.FamilyEligibilityResponseDto;
import com.waad.tba.modules.member.dto.MemberViewDto;
import com.waad.tba.modules.member.dto.DependentViewDto;
import com.waad.tba.modules.member.service.UnifiedMemberService;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Provider Portal Service.
 * 
 * Business logic for healthcare provider operations:
 * - Real-time eligibility verification
 * - Member card scanning
 * - Coverage information retrieval
 * - Annual limit calculations
 * 
 * Uses existing UnifiedMemberService for core member operations.
 * Uses BenefitPolicyCoverageService for annual limit calculations.
 * 
 * @since Phase 1 - Provider Portal
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderPortalService {
    
    private final UnifiedMemberService unifiedMemberService;
    private final MemberRepository memberRepository;
    private final BenefitPolicyCoverageService benefitPolicyCoverageService;
    private final BenefitPolicyRepository benefitPolicyRepository;
    private final ClaimRepository claimRepository;
    private final com.waad.tba.modules.visit.repository.VisitRepository visitRepository;
    private final com.waad.tba.security.ProviderContextGuard providerContextGuard;
    private final PreAuthorizationRepository preAuthorizationRepository;
    
    /**
     * Check Member Eligibility for Provider.
     * 
     * الفحص يتم فقط بالباركود أو رقم البطاقة.
     * الرقم الوطني لا يُستخدم للفحص - يظهر فقط كمعلومات أساسية.
     * 
     * Flow:
     * 1. Lookup member by barcode or card number
     * 2. Retrieve family information (principal + dependents)
     * 3. Calculate annual limits for each member
     * 4. Determine eligibility status
     * 5. Format response for provider UI
     * 
     * @param request Provider eligibility request (barcode required)
     * @param providerUsername Username of provider making request
     * @return Provider-formatted eligibility response
     * @throws IllegalArgumentException if request is invalid or member not found
     */
    @Transactional(readOnly = true)
    public ProviderEligibilityResponse checkEligibility(
            ProviderEligibilityRequest request, 
            String providerUsername) {
        
        log.info("🏥 Processing provider eligibility check: barcode={}, provider={}", 
                 request.getBarcode(), providerUsername);
        
        if (!request.isValid()) {
            log.warn("⚠️ Invalid eligibility request: barcode is empty");
            throw new IllegalStateException(
                "يجب إدخال بيانات البحث (الاسم، أو رقم البطاقة، أو الباركود)"
            );
        }
        
        // Step 1: Lookup member using barcode or card number or memberId
        String lookupKey = request.getBarcode(); // Note: field is barcode, but contains the lookup key
        log.debug("🔍 Looking up member with barcode/cardNumber: {} or memberId: {}", lookupKey, request.getMemberId());
        
        Member member = null;
        if (request.getMemberId() != null) {
            member = memberRepository.findById(request.getMemberId()).orElse(null);
        }
        
        if (member == null) {
            member = findMember(lookupKey);
        }
        
        if (member == null) {
            log.warn("⚠️ Member not found for lookup key: {}", lookupKey);
            throw new IllegalStateException(
                "لم يتم العثور على أي مستفيد مطابق لبيانات البحث المدخلة: " + lookupKey
            );
        }
        
        log.debug("✓ Found member: id={}, barcode={}", member.getId(), member.getBarcode());
        
        // Step 2: Get family eligibility (reuse existing service)
        String barcode = resolveFamilyBarcode(member);
        FamilyEligibilityResponseDto familyEligibility = unifiedMemberService.checkEligibility(barcode);
        
        // Step 3: Build provider response
        ProviderEligibilityResponse response = buildProviderResponse(familyEligibility, barcode);
        
        log.info("✅ Provider eligibility check completed: eligible={}, familySize={}", 
                 response.getEligible(), response.getTotalFamilyMembers());
        
        return response;
    }
    
    /**
     * Find member by barcode, card number, or name.
     * Uses eager fetching to ensure employer organization and benefit policy are loaded.
     */
    private Member findMember(String lookupKey) {
        // Try barcode first (with eager loading)
        Member member = memberRepository.findByBarcode(lookupKey).orElse(null);
        
        if (member == null) {
            // Try card number (with eager loading)
            member = memberRepository.findByCardNumberWithDetails(lookupKey).orElse(null);
        }

        if (member == null) {
            // Try general search by name, civil ID, or card number
            java.util.List<Member> results = memberRepository.search(lookupKey);
            if (results != null && !results.isEmpty()) {
                if (results.size() == 1) {
                    member = results.get(0);
                } else {
                    // More than one member found, force them to use a specific identifier
                    throw new IllegalStateException(
                        "يوجد أكثر من مستفيد مطابق لهذا البحث. يرجى استخدام رقم البطاقة أو الباركود لتحديد المستفيد بدقة."
                    );
                }
            }
        }
        
        if (member != null) {
            // Log employer info for debugging
            if (member.getEmployer() != null) {
                log.debug("✅ Found member with employer: {} ({})", 
                         member.getEmployer().getName(),
                         member.getEmployer().getId());
            } else {
                log.warn("⚠️ Found member ID={} but NO employer organization!", member.getId());
            }
        }
        
        return member;
    }

    /**
     * Resolve the family barcode from looked-up member.
     * - Principal: own barcode
     * - Dependent: parent's barcode
     */
    private String resolveFamilyBarcode(Member member) {
        if (member == null) {
            throw new IllegalArgumentException("Member is required");
        }

        if (member.isPrincipal()) {
            if (member.getBarcode() == null || member.getBarcode().isBlank()) {
                throw new IllegalArgumentException("Principal member has no barcode");
            }
            return member.getBarcode();
        }

        Member parent = member.getParent();
        if (parent == null || parent.getBarcode() == null || parent.getBarcode().isBlank()) {
            throw new IllegalArgumentException("Dependent member is not linked to a valid principal barcode");
        }
        return parent.getBarcode();
    }
    
    /**
     * Build provider-specific response from family eligibility data.
     */
    private ProviderEligibilityResponse buildProviderResponse(
            FamilyEligibilityResponseDto familyData, 
            String barcode) {
        
        MemberViewDto principal = familyData.getPrincipal();
        List<DependentViewDto> dependents = familyData.getDependents() != null 
            ? familyData.getDependents() 
            : new ArrayList<>();
        
        // Build family members list
        List<ProviderEligibilityResponse.FamilyMemberInfo> familyMembers = new ArrayList<>();
        
        // Add principal with calculated limits
        Member principalMemberEntity = null;
        if (principal != null) {
            principalMemberEntity = memberRepository.findById(principal.getId()).orElse(null);
            familyMembers.add(buildFamilyMemberInfo(principal, principalMemberEntity, true, barcode));
        }
        
        // Add dependents with calculated limits
        for (DependentViewDto dependent : dependents) {
            Member dependentMember = memberRepository.findById(dependent.getId())
                .orElse(null);
            familyMembers.add(buildFamilyMemberInfo(dependent, dependentMember, false, barcode));
        }
        
        // Determine overall status - also check employer organization
        boolean overallEligible = familyData.getEligible() != null && familyData.getEligible();
        boolean hasEmployer = familyData.getEmployerOrgId() != null && familyData.getEmployerOrgName() != null;
        
        String statusCode;
        String message;
        
        if (!hasEmployer) {
            // No employer - cannot be eligible
            statusCode = "ERROR";
            message = "العائلة غير مؤهلة - المؤمن عليه غير مرتبط بجهة عمل";
            overallEligible = false;
            log.warn("⚠️ Eligibility check failed: No employer organization for barcode {}", barcode);
        } else if (overallEligible) {
            statusCode = "SUCCESS";
            message = "العائلة مؤهلة - يرجى اختيار المريض من القائمة أدناه";
        } else {
            statusCode = "ERROR";
            message = "العائلة غير مؤهلة - يرجى التواصل مع شركة التأمين";
        }
        
        // Calculate principal limits
        BigDecimal principalAnnualLimit = BigDecimal.ZERO;
        BigDecimal principalUsedAmount = BigDecimal.ZERO;
        BigDecimal principalRemainingLimit = BigDecimal.ZERO;
        Double principalUsagePercentage = 0.0;
        
        BenefitPolicy principalPolicy = resolveEffectivePolicy(principalMemberEntity, LocalDate.now());
        if (principalPolicy != null) {
                principalAnnualLimit = principalPolicy.getAnnualLimit() != null 
                    ? principalPolicy.getAnnualLimit() 
                    : BigDecimal.ZERO;

                principalUsedAmount = calculateUsedAmount(principalMemberEntity, principalPolicy, LocalDate.now());
                principalRemainingLimit = calculateRemainingLimit(principalPolicy, principalUsedAmount);
                
                if (principalAnnualLimit.compareTo(BigDecimal.ZERO) > 0) {
                    principalUsagePercentage = principalUsedAmount
                        .divide(principalAnnualLimit, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();
                }
        }
        
        // Build warnings
        List<String> warnings = buildWarnings(familyMembers);
        
        // Build covered services (from principal's benefit policy)
        List<String> coveredServices = List.of(
            "الاستشارات الطبية",
            "الفحوصات المخبرية", 
            "الأشعة والتصوير الطبي",
            "الأدوية",
            "العلاج الطبيعي"
        );
        
        return ProviderEligibilityResponse.builder()
            // Eligibility status
            .eligible(overallEligible)
            .message(message)
            .statusCode(statusCode)
            
            // Member information
            .principalMember(principal)
            .familyMembers(familyMembers)
            .totalFamilyMembers(familyMembers.size())
            .eligibleMembersCount(familyData.getEligibleMembersCount())
            
            // Coverage information
            .benefitPolicyId(principalPolicy != null ? principalPolicy.getId() : familyData.getBenefitPolicyId())
            .benefitPolicyName(principalPolicy != null ? principalPolicy.getName() : familyData.getBenefitPolicyName())
            .policyStatus(principalPolicy != null && principalPolicy.getStatus() != null
                ? principalPolicy.getStatus().name()
                : familyData.getBenefitPolicyStatus())
            .employerName(familyData.getEmployerOrgName())
            .coverageType(familyData.getBenefitPolicyName())
            .effectiveDate(principal != null && principal.getStartDate() != null 
                ? principal.getStartDate().toString() 
                : null)
            .endDate(principal != null && principal.getEndDate() != null 
                ? principal.getEndDate().toString() 
                : null)
            
            // Annual limit information (principal)
            .principalAnnualLimit(principalAnnualLimit)
            .principalUsedAmount(principalUsedAmount)
            .principalRemainingLimit(principalRemainingLimit)
            .principalUsagePercentage(principalUsagePercentage)
            
            // Additional information
            .warnings(warnings)
            .coveredServices(coveredServices)
            .checkTimestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .barcode(barcode)
            
            .build();
    }
    
    /**
     * Build family member info from MemberViewDto (Principal).
     */
    private ProviderEligibilityResponse.FamilyMemberInfo buildFamilyMemberInfo(
            MemberViewDto memberDto, 
            Member member,
            boolean isPrincipal,
            String principalBarcode) {
        
        // Calculate annual limits using BenefitPolicyCoverageService
        BigDecimal annualLimit = BigDecimal.ZERO;
        BigDecimal usedAmount = BigDecimal.ZERO;
        BigDecimal remainingLimit = BigDecimal.ZERO;
        Double usagePercentage = 0.0;
        
        BenefitPolicy policy = resolveEffectivePolicy(member, LocalDate.now());
        if (policy != null) {
            annualLimit = policy.getAnnualLimit() != null 
                ? policy.getAnnualLimit() 
                : BigDecimal.ZERO;

            usedAmount = calculateUsedAmount(member, policy, LocalDate.now());
            remainingLimit = calculateRemainingLimit(policy, usedAmount);
            
            if (annualLimit.compareTo(BigDecimal.ZERO) > 0) {
                usagePercentage = usedAmount
                    .divide(annualLimit, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
            }
        }
        
        Integer age = memberDto.getBirthDate() != null 
            ? Period.between(memberDto.getBirthDate(), LocalDate.now()).getYears() 
            : null;
        
        boolean eligible = Boolean.TRUE.equals(memberDto.getEligibilityStatus()) && 
                          Boolean.TRUE.equals(memberDto.getActive());
        
        String eligibilityMessage = eligible 
            ? "مؤهل للخدمة" 
            : (Boolean.FALSE.equals(memberDto.getActive()) 
                ? "غير نشط" 
                : "غير مؤهل");
        
        ProviderEligibilityResponse.FamilyMemberInfo.FamilyMemberInfoBuilder builder = ProviderEligibilityResponse.FamilyMemberInfo.builder()
            .memberId(memberDto.getId())
            .isPrincipal(isPrincipal)
            .fullName(memberDto.getFullName())
            .relationship(isPrincipal ? "SELF" : null)
            .birthDate(memberDto.getBirthDate() != null ? memberDto.getBirthDate().toString() : null)
            .age(age)
            .gender(memberDto.getGender() != null ? memberDto.getGender().toString() : null)
            .nationalId(memberDto.getNationalNumber())
            .barcode(memberDto.getBarcode())
            .eligible(eligible)
            .eligibilityMessage(eligibilityMessage)
            .annualLimit(annualLimit)
            .usedAmount(usedAmount)
            .remainingLimit(remainingLimit)
            .usagePercentage(usagePercentage)
            .active(memberDto.getActive())
                .cardNumber(memberDto.getCardNumber())
                .profileImage(resolveProfileImageUrl(memberDto.getId(), memberDto.getPhotoUrl(), member))
            .photoPath(member != null ? member.getProfilePhotoPath() : null);
            
        populateOpenVisit(builder, memberDto.getId());
        return builder.build();
    }
    
    /**
     * Build family member info from DependentViewDto.
     * Dependents use the principal's barcode for eligibility checks.
     */
    private ProviderEligibilityResponse.FamilyMemberInfo buildFamilyMemberInfo(
            DependentViewDto dependent,
            Member member,
            boolean isPrincipal,
            String principalBarcode) {
        
        // Calculate annual limits using BenefitPolicyCoverageService
        BigDecimal annualLimit = BigDecimal.ZERO;
        BigDecimal usedAmount = BigDecimal.ZERO;
        BigDecimal remainingLimit = BigDecimal.ZERO;
        Double usagePercentage = 0.0;
        
        BenefitPolicy policy = resolveEffectivePolicy(member, LocalDate.now());
        if (policy != null) {
            annualLimit = policy.getAnnualLimit() != null 
                ? policy.getAnnualLimit() 
                : BigDecimal.ZERO;

            usedAmount = calculateUsedAmount(member, policy, LocalDate.now());
            remainingLimit = calculateRemainingLimit(policy, usedAmount);
            
            if (annualLimit.compareTo(BigDecimal.ZERO) > 0) {
                usagePercentage = usedAmount
                    .divide(annualLimit, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
            }
        }
        
        Integer age = dependent.getBirthDate() != null 
            ? Period.between(dependent.getBirthDate(), LocalDate.now()).getYears() 
            : null;
        
        boolean eligible = Boolean.TRUE.equals(dependent.getEligibilityStatus()) && 
                          Boolean.TRUE.equals(dependent.getActive());
        
        String eligibilityMessage = eligible 
            ? "مؤهل للخدمة" 
            : (Boolean.FALSE.equals(dependent.getActive()) 
                ? "غير نشط" 
                : "غير مؤهل");
        
        ProviderEligibilityResponse.FamilyMemberInfo.FamilyMemberInfoBuilder builder = ProviderEligibilityResponse.FamilyMemberInfo.builder()
            .memberId(dependent.getId())
            .isPrincipal(false)
            .fullName(dependent.getFullName())
            .relationship(dependent.getRelationship() != null 
                ? dependent.getRelationship().toString() 
                : null)
            .birthDate(dependent.getBirthDate() != null ? dependent.getBirthDate().toString() : null)
            .age(age)
            .gender(dependent.getGender() != null ? dependent.getGender().toString() : null)
            .nationalId(dependent.getNationalNumber())
            .barcode(principalBarcode) // Dependents use principal's barcode
            .eligible(eligible)
            .eligibilityMessage(eligibilityMessage)
            .annualLimit(annualLimit)
            .usedAmount(usedAmount)
            .remainingLimit(remainingLimit)
            .usagePercentage(usagePercentage)
            .active(dependent.getActive())
            .cardNumber(dependent.getCardNumber())
            .profileImage(resolveProfileImageUrl(dependent.getId(), dependent.getPhotoUrl(), member))
            .photoPath(member != null ? member.getProfilePhotoPath() : null);
            
        populateOpenVisit(builder, dependent.getId());
        return builder.build();
    }

    private void populateOpenVisit(ProviderEligibilityResponse.FamilyMemberInfo.FamilyMemberInfoBuilder builder, Long memberId) {
        Long providerId = providerContextGuard.getProviderFilter();
        if (providerId != null) {
            java.util.List<com.waad.tba.modules.visit.entity.VisitStatus> openStatuses = java.util.List.of(
                com.waad.tba.modules.visit.entity.VisitStatus.REGISTERED, 
                com.waad.tba.modules.visit.entity.VisitStatus.IN_PROGRESS,
                com.waad.tba.modules.visit.entity.VisitStatus.PENDING_PREAUTH,
                com.waad.tba.modules.visit.entity.VisitStatus.PREAUTH_APPROVED
            );
            
            java.util.List<com.waad.tba.modules.visit.entity.Visit> openVisits = visitRepository.findByMemberIdAndProviderIdAndStatusIn(memberId, providerId, openStatuses);
            
            for (com.waad.tba.modules.visit.entity.Visit visit : openVisits) {
                boolean hasClaims = !visit.getClaims().isEmpty();
                boolean hasPreAuths = !preAuthorizationRepository.findByVisitIdAndActiveTrue(visit.getId()).isEmpty();
                
                // If status is REGISTERED/IN_PROGRESS AND has no claims AND no pre-auths, it's virtually empty!
                if (!hasClaims && !hasPreAuths) {
                    builder.hasOpenVisit(true);
                    builder.openVisitId(visit.getId());
                    return; // Found an empty open visit
                }
            }
            builder.hasOpenVisit(false);
        }
    }

    private String resolveProfileImageUrl(Long memberId, String dtoPhotoUrl, Member memberEntity) {
        if (dtoPhotoUrl != null && !dtoPhotoUrl.isBlank()) {
            return dtoPhotoUrl;
        }

        if (memberEntity != null && memberEntity.getPhotoUrl() != null && !memberEntity.getPhotoUrl().isBlank()) {
            return memberEntity.getPhotoUrl();
        }

        if (memberEntity != null && memberEntity.getProfilePhotoPath() != null && !memberEntity.getProfilePhotoPath().isBlank() && memberId != null) {
            return "/api/v1/unified-members/" + memberId + "/photo";
        }

        return null;
    }
    
    /**
     * Calculate used amount for member in current year.
     */
    private BigDecimal calculateUsedAmount(Member member, BenefitPolicy policy, LocalDate asOfDate) {
        if (member == null || policy == null || asOfDate == null) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal annualLimit = policy.getAnnualLimit();
        if (annualLimit == null || annualLimit.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        List<Claim> claims = claimRepository.findByMemberId(member.getId());

        return claims.stream()
            .filter(c -> c.getServiceDate() != null && c.getServiceDate().getYear() == asOfDate.getYear())
            .filter(c -> c.getApprovedAmount() != null)
            .map(Claim::getApprovedAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateRemainingLimit(BenefitPolicy policy, BigDecimal usedAmount) {
        if (policy == null || policy.getAnnualLimit() == null || policy.getAnnualLimit().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return policy.getAnnualLimit().subtract(usedAmount != null ? usedAmount : BigDecimal.ZERO).max(BigDecimal.ZERO);
    }

    private BenefitPolicy resolveEffectivePolicy(Member member, LocalDate asOfDate) {
        if (member == null) {
            return null;
        }

        BenefitPolicy directPolicy = member.getBenefitPolicy();
        if (directPolicy != null && directPolicy.isActive() && directPolicy.isEffectiveOn(asOfDate)) {
            return directPolicy;
        }

        if (member.getEmployer() == null) {
            return null;
        }

        return benefitPolicyRepository
            .findActiveEffectivePolicyForEmployer(member.getEmployer().getId(), asOfDate)
            .orElse(null);
    }
    
    /**
     * Build warning messages for provider.
     */
    private List<String> buildWarnings(List<ProviderEligibilityResponse.FamilyMemberInfo> familyMembers) {
        List<String> warnings = new ArrayList<>();
        
        for (ProviderEligibilityResponse.FamilyMemberInfo member : familyMembers) {
            // Warning: High usage (>= 80%)
            if (member.getUsagePercentage() != null && member.getUsagePercentage() >= 80.0) {
                warnings.add(String.format(
                    "⚠️ الحد السنوي لـ %s وصل إلى %.0f%% (متبقي: %.2f د.ل)",
                    member.getFullName(),
                    member.getUsagePercentage(),
                    member.getRemainingLimit()
                ));
            }
            
            // Warning: Inactive member
            if (Boolean.FALSE.equals(member.getActive())) {
                warnings.add(String.format("❌ العضو %s غير نشط", member.getFullName()));
            }
        }
        
        return warnings;
    }
    
    /**
     * Mask card number for security (show last 4 digits only).
     */
    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() <= 4) {
            return cardNumber;
        }
        
        String lastFour = cardNumber.substring(cardNumber.length() - 4);
        return "****" + lastFour;
    }
}