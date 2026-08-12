package com.waad.tba.modules.member.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.modules.eligibility.domain.EligibilityResult;
import com.waad.tba.modules.eligibility.service.FamilyEligibilityService;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.member.dto.DependentMemberDto;
import com.waad.tba.modules.member.dto.FamilyEligibilityResponseDto;
import com.waad.tba.modules.member.dto.MemberCreateDto;
import com.waad.tba.modules.member.dto.MemberUpdateDto;
import com.waad.tba.modules.member.dto.MemberViewDto;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.mapper.UnifiedMemberMapper;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.provider.service.ProviderService;
import com.waad.tba.modules.systemadmin.service.AuditLogService;
import com.waad.tba.security.AuthorizationService;
import com.waad.tba.modules.rbac.entity.User;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * ==================== UNIFIED MEMBER ARCHITECTURE ====================
 * Service for managing members in the unified architecture.
 * 
 * Handles:
 * - Creating PRINCIPAL members (with optional dependents inline)
 * - Creating DEPENDENT members (standalone)
 * - Updating both principal and dependent members
 * - Family eligibility checks (barcode scan → family view)
 * - Card number generation (unified with suffix)
 * - Barcode generation (principal only)
 * 
 * Business Rules:
 * - Principal: parent_id = NULL, barcode = REQUIRED
 * - Dependent: parent_id != NULL, barcode = NULL
 * - Card Number: Principal = base, Dependent = base + suffix
 * - Relationship: NULL for principal, REQUIRED for dependent
 * 
 * SECURITY (2026-01-16):
 * - EMPLOYER_ADMIN: Sees ONLY members from their own employer
 * - Feature toggle: canViewMembers controls access
 * =====================================================================
 */
@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("deprecation")
public class UnifiedMemberService {

    private static final int MIN_MEMBER_TEXT_SEARCH_LENGTH = 3;

    private final MemberRepository memberRepository;
    private final EmployerRepository employerRepository;
    private final BenefitPolicyRepository benefitPolicyRepository;
    private final BarcodeGeneratorService barcodeGenerator;
    private final CardNumberGeneratorService cardNumberGenerator;
    private final UnifiedMemberMapper mapper;
    private final AuthorizationService authorizationService;
    private final ProviderService providerService;
    private final MemberFinancialSummaryService financialSummaryService;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;
    private final FamilyEligibilityService familyEligibilityService;

    /**
     * Create a PRINCIPAL member (optionally with dependents inline).
     * 
     * @param dto Member creation DTO
     * @return Created member view DTO with dependents
     */
    @Transactional
    public MemberViewDto createPrincipalMember(MemberCreateDto dto) {
        log.info("🆕 Creating PRINCIPAL member: {}", dto.getFullName());

        // Validate: Must NOT have parentId (principal)
        if (dto.getParentId() != null) {
            throw new BusinessRuleException(
                    "Cannot create principal member with parentId. " +
                            "Use createDependentMember() for dependents.");
        }

        // 1. Load employer (needed for card number formula)
        // An EMPLOYER_ADMIN must not be able to enroll a member into a
        // different employer by sending an arbitrary employerId — force it
        // to their own employer regardless of what the request carries.
        Long scopedEmployerId = authorizationService.resolveEmployerScope(
                authorizationService.getCurrentUser(), dto.getEmployerId());
        Employer employer = employerRepository.findById(scopedEmployerId)
                .orElseThrow(() -> new ResourceNotFoundException("Employer not found: " + scopedEmployerId));

        log.info("✅ Loaded employer: id={}, name={}", employer.getId(), employer.getName());

        BenefitPolicy benefitPolicy;
        if (dto.getBenefitPolicyId() != null) {
            benefitPolicy = loadAndValidateBenefitPolicy(dto.getBenefitPolicyId(), employer.getId());
            log.info("✅ Loaded explicit benefit policy: id={}, name={}", benefitPolicy.getId(),
                    benefitPolicy.getName());
        } else {
            benefitPolicy = findActiveEmployerPolicy(employer.getId());
            if (benefitPolicy != null) {
                log.info("✅ Auto-assigned employer active policy: employerId={}, policyId={}, policyName={}",
                        employer.getId(), benefitPolicy.getId(), benefitPolicy.getName());
            } else {
                log.warn("⚠️ No active effective benefit policy found for employerId={}", employer.getId());
            }
        }

        // 2. Build PRINCIPAL member entity (employer/joinDate/employeeNumber needed for
        // card number)
        Member principal = mapper.toEntity(dto);
        principal.setEmployer(employer);
        principal.setBenefitPolicy(benefitPolicy);
        principal.setParent(null); // PRINCIPAL
        principal.setRelationship(null); // PRINCIPAL has no relationship
        // Sync status with active flag on creation
        if (Boolean.FALSE.equals(dto.getActive())) {
            principal.setStatus(Member.MemberStatus.TERMINATED);
        }

        // 3. Generate CARD NUMBER (formula: EMPLOYER_CODE + EMPLOYEE_NUMBER)
        String cardNumber = dto.getCardNumber();
        if (cardNumber == null || cardNumber.trim().isEmpty()) {
            cardNumber = cardNumberGenerator.generateUniqueForPrincipal(principal);
            log.info("✅ Generated card number for principal: {}", cardNumber);
        } else {
            if (memberRepository.existsByCardNumber(cardNumber)) {
                throw new BusinessRuleException(
                        "Card number already exists: " + cardNumber);
            }
        }
        principal.setCardNumber(cardNumber);
        principal.setBarcode(cardNumber);

        // 4. Save principal
        principal = memberRepository.save(principal);
        log.info("✅ Created PRINCIPAL member ID={}, barcode={}, cardNumber={}, employer={}",
                principal.getId(), principal.getBarcode(), principal.getCardNumber(),
                principal.getEmployer() != null ? principal.getEmployer().getName() : "NONE");

        // 5. Create DEPENDENTS if provided
        List<Member> dependents = new ArrayList<>();
        if (dto.getDependents() != null && !dto.getDependents().isEmpty()) {
            log.info("📦 Creating {} dependents for principal ID={}",
                    dto.getDependents().size(), principal.getId());

            for (DependentMemberDto depDto : dto.getDependents()) {
                Member dependent = createDependentInternal(principal, depDto);
                dependents.add(dependent);
            }
        }

        // Note: familyMembers field removed as part of unified architecture

        // 6. Return view DTO
        return mapper.toViewDto(principal, dependents);
    }

    /**
     * Create a DEPENDENT member under an existing principal (NEW METHOD).
     * 
     * @param principalId ID of the principal member
     * @param dto         Dependent member creation DTO
     * @return Created dependent view DTO
     */
    @Transactional
    public MemberViewDto createDependentMember(Long principalId, DependentMemberDto dto) {
        log.info("🆕 Creating DEPENDENT member under principal ID={}: {}", principalId, dto.getFullName());

        // 1. Load principal member
        Member principal = memberRepository.findById(principalId)
                .orElseThrow(() -> new ResourceNotFoundException("Principal member not found: " + principalId));

        // Validate principal is not a dependent
        if (principal.isDependent()) {
            throw new BusinessRuleException(
                    "Cannot create dependent under another dependent. " +
                            "Dependents can only be created under principal members.");
        }

        // 2. Create dependent (using internal method)
        Member dependent = createDependentInternal(principal, dto);

        // 3. Return view DTO
        return mapper.toViewDto(dependent);
    }

    /**
     * Create a DEPENDENT member (standalone, under existing principal) - LEGACY
     * METHOD.
     * 
     * @param dto Member creation DTO (must have parentId and relationship)
     * @return Created dependent view DTO
     * @deprecated Use createDependentMember(Long, DependentMemberDto) instead
     */
    @Deprecated
    @Transactional
    public MemberViewDto createDependentMember(MemberCreateDto dto) {
        log.info("🆕 Creating DEPENDENT member: {}", dto.getFullName());

        // Validate: Must have parentId (dependent)
        if (dto.getParentId() == null) {
            throw new BusinessRuleException(
                    "Cannot create dependent member without parentId. " +
                            "Use createPrincipalMember() for principals.");
        }

        // Validate: Must have relationship
        if (dto.getRelationship() == null) {
            throw new BusinessRuleException(
                    "Relationship is required for dependent members");
        }

        // 1. Load principal member
        Member principal = memberRepository.findById(dto.getParentId())
                .orElseThrow(() -> new ResourceNotFoundException("Principal member not found: " + dto.getParentId()));

        // Validate principal is not a dependent
        if (principal.isDependent()) {
            throw new BusinessRuleException(
                    "Cannot create dependent under another dependent. " +
                            "Dependents can only be created under principal members.");
        }

        // 2. Create dependent (using internal method)
        DependentMemberDto depDto = DependentMemberDto.builder()
                .relationship(dto.getRelationship())
                .fullName(dto.getFullName())
                .nationalNumber(dto.getNationalNumber())
                .birthDate(dto.getBirthDate())
                .gender(dto.getGender())
                .maritalStatus(dto.getMaritalStatus())
                .phone(dto.getPhone())
                .email(dto.getEmail())
                .occupation(dto.getOccupation())
                .notes(dto.getNotes())
                .active(dto.getActive())
                .build();

        Member dependent = createDependentInternal(principal, depDto);

        // 3. Return view DTO
        return mapper.toViewDto(dependent);
    }

    /**
     * Internal method to create a dependent member.
     * 
     * @param principal Principal member (parent)
     * @param dto       Dependent member DTO
     * @return Created dependent entity
     */
    @Transactional
    protected Member createDependentInternal(Member principal, DependentMemberDto dto) {
        log.debug("Creating dependent: {} ({})", dto.getFullName(), dto.getRelationship());

        // 1. Generate card number with relationship suffix (e.g. JFZ-2025-126565-D1)
        String cardNumber = cardNumberGenerator.generateForDependent(principal, dto.getRelationship());
        log.debug("✅ Generated card number for dependent: {}", cardNumber);

        // 2. Create dependent entity
        Member dependent = mapper.toEntity(dto);
        dependent.setParent(principal);
        dependent.setCardNumber(cardNumber);
        dependent.setBarcode(cardNumber);

        // 3. Inherit from principal
        dependent.setEmployer(principal.getEmployer());
        dependent.setBenefitPolicy(principal.getBenefitPolicy());
        dependent.setPolicyNumber(principal.getPolicyNumber());

        // Sync status with active flag on creation
        if (Boolean.FALSE.equals(dto.getActive())) {
            dependent.setStatus(Member.MemberStatus.TERMINATED);
        }

        // 4. Save
        dependent = memberRepository.save(dependent);
        log.info("✅ Created DEPENDENT member ID={}, cardNumber={}, relationship={}",
                dependent.getId(), dependent.getCardNumber(), dependent.getRelationship());

        return dependent;
    }

    /**
     * Update a member (principal or dependent).
     * 
     * @param id  Member ID
     * @param dto Update DTO
     * @return Updated member view DTO
     */
    @Transactional
    public MemberViewDto updateMember(Long id, MemberUpdateDto dto) {
        log.info("📝 Updating member ID={}", id);

        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + id));

        User currentUser = authorizationService.getCurrentUser();
        if (!authorizationService.canAccessMember(currentUser, id)) {
            log.warn("❌ Access denied: user {} attempted to update member {}",
                    currentUser != null ? currentUser.getUsername() : "unknown", id);
            throw new AccessDeniedException("Access denied to this member");
        }

        // Update common fields
        mapper.updateEntityFromDto(member, dto);

        if (dto.getEmployerId() != null) {
            // An EMPLOYER_ADMIN must not be able to move a member to another
            // employer's roster (and thus another employer's policy/claims
            // liability) by sending a different employerId; resolveEmployerScope
            // forces it back to their own employer. Internal staff pass through
            // unchanged and can still reassign members between employers.
            Long scopedEmployerId = authorizationService.resolveEmployerScope(currentUser, dto.getEmployerId());
            Employer employer = employerRepository.findById(scopedEmployerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Employer not found: " + scopedEmployerId));
            member.setEmployer(employer);
        }

        if (dto.getBenefitPolicyId() != null) {
            Long employerId = member.getEmployer() != null ? member.getEmployer().getId() : null;
            member.setBenefitPolicy(loadAndValidateBenefitPolicy(dto.getBenefitPolicyId(), employerId));
        } else if (member.getBenefitPolicy() == null && member.getEmployer() != null) {
            BenefitPolicy autoPolicy = findActiveEmployerPolicy(member.getEmployer().getId());
            if (autoPolicy != null) {
                member.setBenefitPolicy(autoPolicy);
                log.info("✅ Auto-assigned policy during member update: memberId={}, policyId={}",
                        member.getId(), autoPolicy.getId());
            }
        }

        // Save
        member = memberRepository.save(member);
        log.info("✅ Updated member ID={}", id);

        // Return view based on type
        if (member.isPrincipal()) {
            List<Member> dependents = memberRepository.findByParentId(member.getId());
            return mapper.toViewDto(member, dependents);
        } else {
            return mapper.toViewDto(member);
        }
    }

    /**
     * Activate or deactivate a member.
     *
     * @param id     Member ID
     * @param active true = activate, false = deactivate
     * @return Updated member view DTO
     */
    @Transactional
    public MemberViewDto toggleActive(Long id, boolean active) {
        log.info("🔄 Setting active={} for member ID={}", active, id);

        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + id));

        User currentUser = authorizationService.getCurrentUser();
        if (!authorizationService.canAccessMember(currentUser, id)) {
            log.warn("❌ Access denied: user {} attempted to toggle active status of member {}",
                    currentUser != null ? currentUser.getUsername() : "unknown", id);
            throw new AccessDeniedException("Access denied to this member");
        }

        member.setActive(active);
        member = memberRepository.save(member);

        log.info("✅ Member ID={} active status set to {}", id, active);

        if (member.isPrincipal()) {
            List<Member> dependents = memberRepository.findByParentId(member.getId());
            return mapper.toViewDto(member, dependents);
        }
        return mapper.toViewDto(member);
    }

    /**
     * Explicitly transition a member's membership status (ACTIVE / SUSPENDED / PENDING / TERMINATED).
     *
     * Distinct from {@link #toggleActive}: that only flips the coarse `active` flag used for
     * eligibility checks, but never touched the richer {@code MemberStatus} enum, leaving it
     * permanently stale after creation. This is the first endpoint that lets an operator move a
     * member to SUSPENDED or PENDING (not just active/terminated).
     *
     * `active` is kept in sync: true only while status == ACTIVE, so eligibility checks continue
     * to rely solely on the `active` flag with no behavior change elsewhere.
     *
     * @param id        Member ID
     * @param newStatus Target status
     * @param reason    Optional reason, required for SUSPENDED (stored as blockedReason)
     * @return Updated member view DTO
     */
    @Transactional
    public MemberViewDto changeStatus(Long id, Member.MemberStatus newStatus, String reason) {
        if (newStatus == null) {
            throw new BusinessRuleException("يجب تحديد الحالة الجديدة");
        }

        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + id));

        User currentUser = authorizationService.getCurrentUser();
        if (!authorizationService.canAccessMember(currentUser, id)) {
            log.warn("❌ Access denied: user {} attempted to change status of member {}",
                    currentUser != null ? currentUser.getUsername() : "unknown", id);
            throw new AccessDeniedException("Access denied to this member");
        }

        Member.MemberStatus previousStatus = member.getStatus();
        if (previousStatus == newStatus) {
            throw new BusinessRuleException("المستفيد بالفعل في هذه الحالة: " + newStatus);
        }

        if (newStatus == Member.MemberStatus.SUSPENDED && (reason == null || reason.trim().isEmpty())) {
            throw new BusinessRuleException("سبب الإيقاف مطلوب عند تعليق المستفيد");
        }

        if (newStatus == Member.MemberStatus.ACTIVE) {
            ensureBenefitPolicyForActivation(member);
        }

        member.setStatus(newStatus);
        // `active` is this system's archive/soft-delete flag (same convention as Employer/Provider),
        // NOT a mirror of "status == ACTIVE" — it drives default-list visibility. Only TERMINATED
        // is an archival state; SUSPENDED/PENDING members must stay visible in the normal list with
        // their status chip reflecting the real state, not vanish as if soft-deleted.
        member.setActive(newStatus != Member.MemberStatus.TERMINATED);
        member.setBlockedReason(newStatus == Member.MemberStatus.SUSPENDED ? reason : null);
        member = memberRepository.save(member);

        // Suspending/terminating a principal removes coverage for the whole family; reactivation
        // is deliberately NOT cascaded — each dependent must be reactivated individually so a
        // dependent who was separately suspended for their own reason isn't silently reinstated.
        if (member.isPrincipal()
                && (newStatus == Member.MemberStatus.SUSPENDED || newStatus == Member.MemberStatus.TERMINATED)) {
            List<Member> dependents = memberRepository.findByParentId(id);
            dependents.forEach(dep -> {
                dep.setStatus(newStatus);
                dep.setActive(newStatus != Member.MemberStatus.TERMINATED);
                // Dependents inherit the principal's suspension reason — they weren't suspended
                // for their own reason, so the tooltip/badge should explain it's a family-wide
                // effect of the principal's status, not show a blank reason.
                dep.setBlockedReason(newStatus == Member.MemberStatus.SUSPENDED
                        ? "إيقاف تلقائي لتوقف الرئيسي: " + reason
                        : null);
            });
            memberRepository.saveAll(dependents);
        }

        auditLogService.createAuditLog("STATUS_CHANGE", "MEMBER", id,
                String.format("Status changed from %s to %s%s", previousStatus, newStatus,
                        reason != null && !reason.isBlank() ? " — " + reason : ""),
                currentUser != null ? currentUser.getId() : null,
                currentUser != null ? currentUser.getUsername() : "system", null, null);

        log.info("✅ Member ID={} status changed: {} -> {}", id, previousStatus, newStatus);

        if (member.isPrincipal()) {
            List<Member> dependents = memberRepository.findByParentId(member.getId());
            return mapper.toViewDto(member, dependents);
        }
        return mapper.toViewDto(member);
    }

    /**
     * The database enforces {@code chk_active_member_requires_policy}: a member row cannot be
     * saved with active=true and no benefit policy. Members can end up without one (e.g. a
     * dependent created while the employer had no active policy yet, or a policy that later
     * expired). Rather than let that raw SQL constraint violation surface to the user, try to
     * auto-assign the employer's current active policy, and fail with a clear Arabic message if
     * none exists.
     */
    private void ensureBenefitPolicyForActivation(Member member) {
        if (member.getBenefitPolicy() != null) {
            return;
        }
        Long employerId = member.getEmployer() != null ? member.getEmployer().getId() : null;
        BenefitPolicy autoPolicy = findActiveEmployerPolicy(employerId);
        if (autoPolicy == null) {
            throw new BusinessRuleException(
                    "لا يمكن تفعيل المستفيد لعدم وجود وثيقة تأمين سارية لجهة العمل. يرجى ربط وثيقة تأمين أولاً.");
        }
        member.setBenefitPolicy(autoPolicy);
        log.info("✅ Auto-assigned policy while activating member: memberId={}, policyId={}", member.getId(), autoPolicy.getId());
    }

    private BenefitPolicy findActiveEmployerPolicy(Long employerId) {
        if (employerId == null) {
            return null;
        }
        return benefitPolicyRepository
                .findActiveEffectivePolicyForEmployer(employerId, LocalDate.now())
                .orElse(null);
    }

    private BenefitPolicy loadAndValidateBenefitPolicy(Long policyId, Long employerId) {
        BenefitPolicy policy = benefitPolicyRepository.findById(policyId)
                .orElseThrow(() -> new ResourceNotFoundException("Benefit policy not found: " + policyId));

        if (employerId != null && policy.getEmployer() != null && !employerId.equals(policy.getEmployer().getId())) {
            throw new BusinessRuleException(
                    "Benefit policy " + policyId + " does not belong to employer " + employerId);
        }

        return policy;
    }

    /**
     * Get member by ID (with dependents if principal).
     * 
     * @param id Member ID
     * @return Member view DTO
     */
    @Transactional(readOnly = true)
    public MemberViewDto getMember(Long id) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + id));

        User currentUser = authorizationService.getCurrentUser();
        if (!authorizationService.canAccessMember(currentUser, id)) {
            log.warn("❌ Access denied: user {} attempted to read member {}",
                    currentUser != null ? currentUser.getUsername() : "unknown", id);
            throw new AccessDeniedException("Access denied to this member");
        }

        if (member.isPrincipal()) {
            List<Member> dependents = memberRepository.findByParentId(member.getId());
            return mapper.toViewDto(member, dependents);
        } else {
            return mapper.toViewDto(member);
        }
    }

    /**
     * Check family eligibility by barcode (principal's barcode).
     *
     * Returns principal + all dependents for selection.
     *
     * @param barcode     Principal member's barcode
     * @param serviceDate Date to check eligibility against; defaults to today
     *                    when null (matches the previous hardcoded behavior,
     *                    but now callers -- e.g. checking eligibility for a
     *                    backdated visit -- can override it, the same way
     *                    modules/eligibility.FamilyEligibilityService always
     *                    could).
     * @return Family eligibility response
     */
    @Transactional(readOnly = true)
    public FamilyEligibilityResponseDto checkFamilyEligibility(String barcode, LocalDate serviceDate) {
        log.info("🔍 Checking family eligibility for barcode: {}, serviceDate: {}", barcode, serviceDate);

        // 1. Find principal by barcode
        Member principal = memberRepository.findByBarcode(barcode)
                .orElseThrow(() -> new ResourceNotFoundException("No member found with barcode: " + barcode));

        // Validate it's a principal (should always be true if barcode exists)
        if (principal.isDependent()) {
            throw new BusinessRuleException(
                    "Invalid state: Dependent member has barcode. Only principals should have barcodes.");
        }

        // 2. Resolve family + evaluate eligibility through the SAME orchestrator
        // modules/eligibility.FamilyEligibilityService uses for its own
        // memberId-based endpoint (evaluateFamily). Previously this method had
        // its own independent copy of this loop -- one endpoint resolved the
        // family by barcode and always used today's date, the other by member
        // id with a caller-supplied date, and each ran its own engine-calling
        // loop. Now there is exactly one "evaluate this family" implementation;
        // this method only resolves barcode -> principal and maps the shared
        // result into its own response DTO shape.
        FamilyEligibilityService.FamilyGroup family = familyEligibilityService.resolveFamily(principal);
        List<Member> dependents = family.dependents();
        Map<Long, EligibilityResult> eligibilityByMemberId =
                familyEligibilityService.evaluateFamily(family.principal(), dependents, serviceDate);

        // 3. Build response
        FamilyEligibilityResponseDto response = mapper.toFamilyEligibilityResponse(principal, dependents, eligibilityByMemberId);

        // 4. Populate financial details -- ONE bulk read for principal + every
        // dependent together (MemberFinancialSummaryService.getFinancialSummaries),
        // not one query per family member. "usedAmount"/"remainingLimit" here are the
        // WAAD-FIN-1.0 limit-consumption axis (limitConsumedAmount), never
        // totalApproved -- see MemberFinancialSummaryDto's field docs for why the two
        // are different numbers, and MEMBER_MODULE_CLOSURE_PLAN.md for the incident
        // this axis previously caused (member window overstated remaining coverage).
        try {
            List<Long> familyMemberIds = new ArrayList<>();
            familyMemberIds.add(principal.getId());
            if (response.getDependents() != null) {
                response.getDependents().forEach(dep -> familyMemberIds.add(dep.getId()));
            }
            var summariesByMember = financialSummaryService.getFinancialSummaries(familyMemberIds);

            var principalSummary = summariesByMember.get(principal.getId());
            if (principalSummary != null) {
                response.setAnnualLimit(principalSummary.getAnnualLimit());
                response.setRemainingFamilyLimit(principalSummary.getRemainingCoverage());

                if (response.getPrincipal() != null) {
                    response.getPrincipal().setAnnualLimit(principalSummary.getAnnualLimit());
                    response.getPrincipal().setUsedAmount(principalSummary.getLimitConsumedAmount());
                    response.getPrincipal().setRemainingLimit(principalSummary.getRemainingCoverage());
                    response.getPrincipal().setUsagePercentage(toDouble(principalSummary.getUtilizationPercent()));
                }
            }

            if (response.getDependents() != null) {
                for (var depDto : response.getDependents()) {
                    var depSummary = summariesByMember.get(depDto.getId());
                    if (depSummary == null) {
                        log.warn("⚠️ No financial summary returned for dependent ID={}", depDto.getId());
                        continue;
                    }
                    depDto.setAnnualLimit(depSummary.getAnnualLimit());
                    depDto.setUsedAmount(depSummary.getLimitConsumedAmount());
                    depDto.setRemainingLimit(depSummary.getRemainingCoverage());
                    depDto.setUsagePercentage(toDouble(depSummary.getUtilizationPercent()));
                }
            }
        } catch (Exception e) {
            log.error("💥 Failed to populate financial data for family eligibility: barcode={}", barcode, e);
            // Previously swallowed entirely: the response returned as if financial
            // data were simply absent/zero, with no signal that the read failed.
            // A null/zero remainingFamilyLimit is indistinguishable from "no limit
            // left" -- callers must check financialDataAvailable before trusting
            // the limit fields.
            response.setFinancialDataAvailable(false);
            response.setFinancialDataError("تعذر جلب بيانات السقف المالي، يرجى إعادة المحاولة");
        }

        log.info(
                "✅ Family eligibility check complete: eligible={}, {} total members ({} principal + {} dependents), employer={}",
                response.getEligible(), response.getTotalFamilyMembers(), 1, dependents.size(),
                response.getEmployerOrgName() != null ? response.getEmployerOrgName() : "NONE");

        return response;
    }

    /** Null-safe BigDecimal-to-double conversion for the DTO's Double-typed percentage fields. */
    private static double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : 0.0;
    }

    /**
     * Soft-delete a member (principal or dependent).
     *
     * Sets active=false and status=TERMINATED. Physical deletion is intentionally
     * avoided because FK constraints (claims, visits, pre-auth, etc.) use
     * ON DELETE RESTRICT, which would cause a 500 for any member with related
     * records. The hard-delete path ({@link #hardDeleteMember}) still exists for
     * admin use when all related records have been removed.
     *
     * IMPORTANT: Soft-deleting a principal will cascade the same flags to all
     * dependents.
     *
     * @param id Member ID
     */
    @Transactional
    public void deleteMember(Long id) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + id));

        User currentUser = authorizationService.getCurrentUser();
        if (!authorizationService.canAccessMember(currentUser, id)) {
            log.warn("❌ Access denied: user {} attempted to delete member {}",
                    currentUser != null ? currentUser.getUsername() : "unknown", id);
            throw new AccessDeniedException("Access denied to this member");
        }

        // Collect IDs to check (principal + its dependents)
        List<Long> allIds = new java.util.ArrayList<>();
        allIds.add(id);
        if (member.isPrincipal()) {
            memberRepository.findByParentId(id).forEach(d -> allIds.add(d.getId()));
        }
        String idList = allIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));

        // Block soft deletion if any financial/medical records exist
        long claimsCount = jdbcTemplate
                .queryForObject("SELECT COUNT(*) FROM claims WHERE member_id IN (" + idList + ")", Long.class);
        long preAuthCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM preauthorization_requests WHERE member_id IN (" + idList + ")", Long.class);
        long visitsCount = jdbcTemplate
                .queryForObject("SELECT COUNT(*) FROM visits WHERE member_id IN (" + idList + ")", Long.class);

        if (claimsCount > 0 || preAuthCount > 0 || visitsCount > 0) {
            String details = String.format(
                    "مطالبات: %d، موافقات مسبقة: %d، زيارات: %d",
                    claimsCount, preAuthCount, visitsCount);
            throw new IllegalStateException(
                    "لا يمكن حذف المستفيد لأن له معاملات مالية مرتبطة (" + details + "). " +
                            "يُرجى أرشفة المستفيد بدلاً من الحذف، أو مراجعة السجلات المالية أولاً.");
        }

        member.setActive(false);
        member.setStatus(Member.MemberStatus.TERMINATED);
        memberRepository.save(member);

        if (member.isPrincipal()) {
            List<Member> dependents = memberRepository.findByParentId(id);
            if (!dependents.isEmpty()) {
                log.warn("⚠️ Soft-deleting PRINCIPAL member ID={} — cascading TERMINATED to {} dependents",
                        id, dependents.size());
                dependents.forEach(dep -> {
                    dep.setActive(false);
                    dep.setStatus(Member.MemberStatus.TERMINATED);
                });
                memberRepository.saveAll(dependents);
            }
        }

        log.info("✅ Soft-deleted member ID={} (status=TERMINATED, active=false)", id);
    }

    // ==================== ADDITIONAL METHODS FOR UNIFIED CONTROLLER
    // ====================

    /**
     * Create member (principal with optional inline dependents).
     * Alias for createPrincipalMember for controller compatibility.
     */
    @Transactional
    public MemberViewDto createMember(MemberCreateDto dto) {
        return createPrincipalMember(dto);
    }

    /**
     * Add dependent to existing principal.
     */
    @Transactional
    public MemberViewDto addDependent(Long principalId, DependentMemberDto dto) {
        return createDependentMember(principalId, dto);
    }

    /**
     * Get member with dependents (if principal).
     * Alias for getMember for controller compatibility.
     */
    @Transactional(readOnly = true)
    public MemberViewDto getMemberWithDependents(Long id) {
        return getMember(id);
    }

    /**
     * Check eligibility by barcode.
     * Alias for checkFamilyEligibility for controller compatibility.
     */
    @Transactional(readOnly = true)
    public FamilyEligibilityResponseDto checkEligibility(String barcode) {
        return checkFamilyEligibility(barcode, null);
    }

    /** Alias for checkFamilyEligibility(barcode, serviceDate) for controller compatibility. */
    @Transactional(readOnly = true)
    public FamilyEligibilityResponseDto checkEligibility(String barcode, LocalDate serviceDate) {
        return checkFamilyEligibility(barcode, serviceDate);
    }

    /**
     * Outcome of {@link #resolveEmployerScopeFilter(Long, String)}:
     * {@code blocked=true} means the caller must return its own empty result
     * (Page.empty()/0) without querying at all -- an EMPLOYER_ADMIN with the
     * VIEW_MEMBERS feature disabled, or with no employerId assigned, must
     * never fall through to an unfiltered query. {@code employerId} is the
     * filter to apply when not blocked; null means "no filter" (internal or
     * financial roles), never "blocked".
     */
    private record EmployerScopeFilter(boolean blocked, Long employerId) {
        static EmployerScopeFilter allowed(Long employerId) {
            return new EmployerScopeFilter(false, employerId);
        }

        static EmployerScopeFilter blockedResult() {
            return new EmployerScopeFilter(true, null);
        }
    }

    /**
     * Single source of the EMPLOYER_ADMIN employer-lock rule: an
     * EMPLOYER_ADMIN can only ever see their own employer's members, no
     * matter what employerId a request asks for. Before this method existed,
     * getAllMembers, searchMembers and countMembers each carried their own
     * copy of this logic -- countMembers' copy was explicitly commented
     * "COPIED from getAllMembers", which is exactly the failure mode this
     * extraction removes: a fourth caller (or an edit to one copy) could
     * silently drift from the other two and leak cross-employer data.
     *
     * SECURITY (2026-01-16, unchanged by this extraction):
     * - EMPLOYER_ADMIN: locked to their own employer; blocked entirely if the
     *   VIEW_MEMBERS feature is disabled for them or they have no employerId.
     * - Every other role: the requested employerId passes through unchanged
     *   (null means "no filter", which is intentional for internal/financial
     *   roles that are allowed to see across employers).
     *
     * @param requestedEmployerId the employerId the caller's request asked for
     * @param action              a short participle for the warning log
     *                            ("view", "search", "count") -- the only
     *                            thing that ever varied between the three
     *                            copies this replaces
     */
    private EmployerScopeFilter resolveEmployerScopeFilter(Long requestedEmployerId, String action) {
        User currentUser = authorizationService.getCurrentUser();
        if (currentUser == null || !authorizationService.isEmployerAdmin(currentUser)) {
            return EmployerScopeFilter.allowed(requestedEmployerId);
        }

        if (!authorizationService.canEmployerViewMembers(currentUser)) {
            log.warn("❌ EMPLOYER_ADMIN user {} attempted to {} members but feature VIEW_MEMBERS is disabled",
                    currentUser.getUsername(), action);
            return EmployerScopeFilter.blockedResult();
        }

        Long employerFilter = authorizationService.getEmployerFilterForUser(currentUser);
        if (employerFilter == null) {
            log.warn("⚠️ EMPLOYER_ADMIN user {} has no employerId assigned", currentUser.getUsername());
            return EmployerScopeFilter.blockedResult();
        }

        log.info("🔒 EMPLOYER_ADMIN filter applied: user={}, action={}, locked to employerId={}",
                currentUser.getUsername(), action, employerFilter);
        return EmployerScopeFilter.allowed(employerFilter);
    }

    /**
     * Get all members with pagination and optional filters.
     *
     * @param pageable   Pagination info
     * @param employerId Optional employer filter
     * @param status     Optional status filter
     * @param type       Optional member type filter (PRINCIPAL/DEPENDENT)
     * @return Page of members
     *
     *         SECURITY (2026-01-16):
     *         - EMPLOYER_ADMIN: Automatically filtered to their employer only
     *         - Internal/financial roles: No automatic filter when explicitly allowed by endpoint/service checks
     */
    @Transactional(readOnly = true)
    public Page<MemberViewDto> getAllMembers(
            Pageable pageable,
            Long employerId,
            String status,
            String type) {

        log.info("Fetching all members: page={}, size={}, employerId={}, status={}, type={}",
                pageable.getPageNumber(), pageable.getPageSize(), employerId, status, type);

        EmployerScopeFilter scope = resolveEmployerScopeFilter(employerId, "view");
        if (scope.blocked()) {
            return Page.empty();
        }
        final Long finalEmployerId = scope.employerId();

        Specification<Member> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (finalEmployerId != null) {
                predicates.add(cb.equal(root.get("employer").get("id"), finalEmployerId));
            }

            if (status != null && !status.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (type != null && !type.trim().isEmpty()) {
                if ("PRINCIPAL".equalsIgnoreCase(type)) {
                    predicates.add(cb.isNull(root.get("parent")));
                } else if ("DEPENDENT".equalsIgnoreCase(type)) {
                    predicates.add(cb.isNotNull(root.get("parent")));
                } else {
                    // Try to filter by specific relationship
                    try {
                        Member.Relationship rel = Member.Relationship.valueOf(type.toUpperCase());
                        predicates.add(cb.equal(root.get("relationship"), rel));
                        predicates.add(cb.isNotNull(root.get("parent")));
                    } catch (IllegalArgumentException e) {
                        // Invalid relationship type, ignore or fallback
                    }
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // Fix Pageable sort since 'type' is transient
        org.springframework.data.domain.Pageable safePageable = pageable;
        if (pageable.getSort().isSorted()) {
            java.util.List<org.springframework.data.domain.Sort.Order> safeOrders = new java.util.ArrayList<>();
            for (org.springframework.data.domain.Sort.Order order : pageable.getSort()) {
                if ("type".equalsIgnoreCase(order.getProperty())) {
                    safeOrders.add(new org.springframework.data.domain.Sort.Order(order.getDirection(), "parent.id"));
                } else {
                    safeOrders.add(order);
                }
            }
            safePageable = org.springframework.data.domain.PageRequest.of(
                    pageable.getPageNumber(),
                    pageable.getPageSize(),
                    org.springframework.data.domain.Sort.by(safeOrders));
        }

        Page<Member> membersPage = memberRepository.findAll(spec, safePageable);

        // ✅ FIX-M1.2: Batch fetch ALL dependents in one query to eliminate N+1
        List<Long> principalIds = membersPage.getContent().stream()
                .filter(Member::isPrincipal)
                .map(Member::getId)
                .collect(Collectors.toList());

        Map<Long, List<Member>> dependentsMap = new HashMap<>();
        if (!principalIds.isEmpty()) {
            List<Member> allDependents = memberRepository.findByParentIdIn(principalIds);
            dependentsMap = allDependents.stream()
                    .collect(Collectors.groupingBy(d -> d.getParent().getId()));
        }

        // Map to DTOs using Page.map() to preserve metadata
        final Map<Long, List<Member>> finalDependentsMap = dependentsMap;
        return membersPage.map(member -> {
            if (member.isPrincipal()) {
                List<Member> dependents = finalDependentsMap.getOrDefault(member.getId(), List.of());
                return mapper.toViewDto(member, dependents);
            }
            return mapper.toViewDto(member);
        });
    }

    /**
     * Count members with optional filters.
     * Matches the logic of getAllMembers (Phase 2 Requirement)
     * 
     * @param employerId Optional employer filter
     * @param status     Optional status filter
     * @param type       Optional member type filter (PRINCIPAL/DEPENDENT)
     * @return Count of matching members
     */
    @Transactional(readOnly = true)
    public long countMembers(Long employerId, String status, String type) {

        EmployerScopeFilter scope = resolveEmployerScopeFilter(employerId, "count");
        if (scope.blocked()) {
            return 0;
        }
        final Long finalEmployerId = scope.employerId();

        Specification<Member> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (finalEmployerId != null) {
                predicates.add(cb.equal(root.get("employer").get("id"), finalEmployerId));
            }

            if (status != null && !status.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (type != null && !type.trim().isEmpty()) {
                if ("PRINCIPAL".equalsIgnoreCase(type)) {
                    predicates.add(cb.isNull(root.get("parent")));
                } else if ("DEPENDENT".equalsIgnoreCase(type)) {
                    predicates.add(cb.isNotNull(root.get("parent")));
                } else {
                    try {
                        Member.Relationship rel = Member.Relationship.valueOf(type.toUpperCase());
                        predicates.add(cb.equal(root.get("relationship"), rel));
                        predicates.add(cb.isNotNull(root.get("parent")));
                    } catch (IllegalArgumentException e) {
                        // Ignore
                    }
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return memberRepository.count(spec);
    }

    /**
     * Advanced search for members.
     * 
     * @param nameAr          Arabic name filter
     * @param nameEn          English name filter
     * @param nationalNumber  National number filter
     * @param barcode         Barcode filter
     * @param cardNumber      Card number filter
     * @param employerId      Employer filter
     * @param benefitPolicyId Benefit policy filter
     * @param status          Status filter
     * @param type            Member type filter
     * @param pageable        Pagination info
     * @return Page of search results
     * 
     *         SECURITY (2026-01-16):
     *         - EMPLOYER_ADMIN: Automatically filtered to their employer only
     *         - Internal/financial roles: No automatic filter when explicitly allowed by endpoint/service checks
     */
    @Transactional(readOnly = true)
    public Page<MemberViewDto> searchMembers(
            String nameAr,
            String nameEn,
            String nationalNumber,
            String barcode,
            String cardNumber,
            Long employerId,
            Long benefitPolicyId,
            String status,
            String type,
            boolean deleted,
            Pageable pageable) {

        log.info("Searching members: nameAr={}, nationalNumber={}, barcode={}, cardNumber={}",
                nameAr, nationalNumber, barcode, cardNumber);

        boolean hasNameSearch = hasText(nameAr) || hasText(nameEn);
        boolean hasExactIdentifierSearch = hasText(nationalNumber) || hasText(barcode) || hasText(cardNumber);
        if (hasNameSearch && !hasExactIdentifierSearch
                && isShortTextSearch(nameAr) && isShortTextSearch(nameEn)) {
            log.info("Skipping member name search shorter than {} characters", MIN_MEMBER_TEXT_SEARCH_LENGTH);
            return Page.empty(pageable);
        }

        EmployerScopeFilter scope = resolveEmployerScopeFilter(employerId, "search");
        if (scope.blocked()) {
            return Page.empty();
        }
        final Long finalEmployerId = scope.employerId();

        Specification<Member> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (nameAr != null && !nameAr.trim().isEmpty()) {
                String searchAr = "%" + nameAr.toLowerCase() + "%";
                if (nameEn != null && !nameEn.trim().isEmpty() && !nameEn.equalsIgnoreCase(nameAr)) {
                    // If both are provided and different, combine with OR to search fullName
                    String searchEn = "%" + nameEn.toLowerCase() + "%";
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("fullName")), searchAr),
                            cb.like(cb.lower(root.get("fullName")), searchEn),
                            cb.like(cb.lower(root.get("cardNumber")), searchAr),
                            cb.like(cb.lower(root.get("cardNumber")), searchEn),
                            cb.like(cb.lower(root.get("nationalNumber")), searchAr),
                            cb.like(root.get("barcode"), searchAr)));
                } else {
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("fullName")), searchAr),
                            cb.like(cb.lower(root.get("cardNumber")), searchAr),
                            cb.like(cb.lower(root.get("nationalNumber")), searchAr),
                            cb.like(root.get("barcode"), searchAr)));
                }
            } else if (nameEn != null && !nameEn.trim().isEmpty()) {
                String searchEn = "%" + nameEn.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), searchEn),
                        cb.like(cb.lower(root.get("cardNumber")), searchEn),
                        cb.like(cb.lower(root.get("nationalNumber")), searchEn),
                        cb.like(root.get("barcode"), searchEn)));
            }

            if (nationalNumber != null && !nationalNumber.trim().isEmpty()) {
                predicates.add(cb.like(root.get("nationalNumber"), "%" + nationalNumber + "%"));
            }

            if (barcode != null && !barcode.trim().isEmpty()) {
                predicates.add(cb.like(root.get("barcode"), "%" + barcode + "%"));
            }

            if (cardNumber != null && !cardNumber.trim().isEmpty()) {
                predicates.add(cb.like(root.get("cardNumber"), "%" + cardNumber + "%"));
            }

            if (finalEmployerId != null) {
                predicates.add(cb.equal(root.get("employer").get("id"), finalEmployerId));
            }

            if (benefitPolicyId != null) {
                predicates.add(cb.equal(root.get("benefitPolicy").get("id"), benefitPolicyId));
            }

            if (status != null && !status.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (type != null && !type.trim().isEmpty()) {
                if ("PRINCIPAL".equalsIgnoreCase(type)) {
                    predicates.add(cb.isNull(root.get("parent")));
                } else if ("DEPENDENT".equalsIgnoreCase(type)) {
                    predicates.add(cb.isNotNull(root.get("parent")));
                } else {
                    try {
                        Member.Relationship rel = Member.Relationship.valueOf(type.toUpperCase());
                        predicates.add(cb.equal(root.get("relationship"), rel));
                        predicates.add(cb.isNotNull(root.get("parent")));
                    } catch (IllegalArgumentException e) {
                        // Ignore
                    }
                }
            }

            // active / soft-delete filter
            if (deleted) {
                predicates.add(cb.equal(root.get("active"), false));
            } else {
                predicates.add(cb.or(cb.isNull(root.get("active")), cb.equal(root.get("active"), true)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // Fix Pageable sort since 'type' is transient
        org.springframework.data.domain.Pageable safePageable = pageable;
        if (pageable.getSort().isSorted()) {
            java.util.List<org.springframework.data.domain.Sort.Order> safeOrders = new java.util.ArrayList<>();
            for (org.springframework.data.domain.Sort.Order order : pageable.getSort()) {
                if ("type".equalsIgnoreCase(order.getProperty())) {
                    safeOrders.add(new org.springframework.data.domain.Sort.Order(order.getDirection(), "parent.id"));
                } else {
                    safeOrders.add(order);
                }
            }
            safePageable = org.springframework.data.domain.PageRequest.of(
                    pageable.getPageNumber(),
                    pageable.getPageSize(),
                    org.springframework.data.domain.Sort.by(safeOrders));
        }

        Page<Member> membersPage = memberRepository.findAll(spec, safePageable);

        // ✅ FIX-M1.3: Batch fetch ALL dependents in one query to eliminate N+1
        List<Long> principalIds = membersPage.getContent().stream()
                .filter(Member::isPrincipal)
                .map(Member::getId)
                .collect(Collectors.toList());

        Map<Long, List<Member>> dependentsMap = new HashMap<>();
        if (!principalIds.isEmpty()) {
            List<Member> allDependents = memberRepository.findByParentIdIn(principalIds);
            dependentsMap = allDependents.stream()
                    .collect(Collectors.groupingBy(d -> d.getParent().getId()));
        }

        // Map to DTOs using Page.map() to preserve metadata
        final Map<Long, List<Member>> finalDependentsMap = dependentsMap;
        return membersPage.map(member -> {
            if (member.isPrincipal()) {
                List<Member> dependents = finalDependentsMap.getOrDefault(member.getId(), List.of());
                return mapper.toViewDto(member, dependents);
            }
            return mapper.toViewDto(member);
        });
    }

    /**
     * Get all dependents of a principal.
     * 
     * @param principalId Principal member ID
     * @return List of dependents
     */
    @Transactional(readOnly = true)
    public List<MemberViewDto> getDependents(Long principalId) {
        Member principal = memberRepository.findById(principalId)
                .orElseThrow(() -> new ResourceNotFoundException("Principal member not found: " + principalId));

        if (!authorizationService.canAccessMember(authorizationService.getCurrentUser(), principalId)) {
            throw new AccessDeniedException("Access denied to this member");
        }

        if (principal.isDependent()) {
            throw new BusinessRuleException("Member ID " + principalId + " is a Dependent, not a Principal");
        }

        List<Member> dependents = memberRepository.findByParentId(principalId);

        return dependents.stream()
                .map(mapper::toViewDto)
                .collect(Collectors.toList());
    }

    /**
     * Count dependents of a principal.
     * 
     * @param principalId Principal member ID
     * @return Count of dependents
     */
    @Transactional(readOnly = true)
    public long countDependents(Long principalId) {
        Member principal = memberRepository.findById(principalId)
                .orElseThrow(() -> new ResourceNotFoundException("Principal member not found: " + principalId));

        if (!authorizationService.canAccessMember(authorizationService.getCurrentUser(), principalId)) {
            throw new AccessDeniedException("Access denied to this member");
        }

        if (principal.isDependent()) {
            throw new BusinessRuleException("Member ID " + principalId + " is a Dependent, not a Principal");
        }

        return memberRepository.countByParentId(principalId);
    }

    // ==================== PHOTO MANAGEMENT ====================

    /**
     * Update member's profile photo path.
     * 
     * @param memberId  Member ID
     * @param photoPath Photo storage path (or null to clear)
     * @return Updated member view DTO
     */
    @Transactional
    public MemberViewDto updateMemberPhoto(Long memberId, String photoPath) {
        log.info("📸 Updating photo for member: memberId={}, path={}", memberId, photoPath);

        Member member = requirePhotoAccess(memberId);

        member.setProfilePhotoPath(photoPath);

        // Also update photoUrl for compatibility
        if (photoPath != null) {
            member.setPhotoUrl("/api/v1/unified-members/" + memberId + "/photo");
        } else {
            member.setPhotoUrl(null);
        }

        Member saved = memberRepository.save(member);

        log.info("✅ Photo updated: memberId={}", memberId);

        return mapper.toViewDto(saved);
    }

    /**
     * Get member's photo path.
     * 
     * @param memberId Member ID
     * @return Photo storage path (or null)
     */
    @Transactional(readOnly = true)
    public String getMemberPhotoPath(Long memberId) {
        Member member = requirePhotoAccess(memberId);

        return member.getProfilePhotoPath();
    }

    /**
     * Verify member-photo resource access before callers upload or touch storage.
     */
    @Transactional(readOnly = true)
    public void assertCanAccessMemberPhoto(Long memberId) {
        requirePhotoAccess(memberId);
    }

    private Member requirePhotoAccess(Long memberId) {
        User currentUser = authorizationService.requireCurrentUser();
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + memberId));

        boolean allowed = authorizationService.isInternalStaff(currentUser)
                || authorizationService.canAccessMember(currentUser, memberId);

        if (!allowed && authorizationService.isProvider(currentUser)
                && currentUser.getProviderId() != null
                && member.getEmployer() != null) {
            allowed = providerService.getAllowedEmployerIds(currentUser.getProviderId())
                    .contains(member.getEmployer().getId());
        }

        if (!allowed) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Access to member photo denied");
        }
        return member;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean isShortTextSearch(String value) {
        return value == null || value.trim().isEmpty()
                || value.trim().length() < MIN_MEMBER_TEXT_SEARCH_LENGTH;
    }

    // ==================== RESTORE & HARD DELETE ====================

    /**
     * Restore a terminated/suspended member to ACTIVE status.
     * 
     * @param memberId Member ID
     * @return Restored member view DTO
     */
    @Transactional
    public MemberViewDto restoreMember(Long memberId) {
        log.info("♻️ Restoring member: memberId={}", memberId);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + memberId));

        if (!authorizationService.canAccessMember(authorizationService.getCurrentUser(), memberId)) {
            throw new AccessDeniedException("Access denied to this member");
        }

        if (member.getStatus() == Member.MemberStatus.ACTIVE) {
            throw new BusinessRuleException("Member is already active: " + memberId);
        }

        ensureBenefitPolicyForActivation(member);

        member.setStatus(Member.MemberStatus.ACTIVE);
        member.setActive(true);

        Member saved = memberRepository.save(member);

        log.info("✅ Member restored to ACTIVE: memberId={}", memberId);

        return mapper.toViewDto(saved);
    }

    /**
     * Permanently delete a member (hard delete).
     * Warning: This cannot be undone!
     *
     * Production rule:
     * Hard delete is allowed only for members with no financial/medical footprint.
     * Claims, visits, pre-authorizations, eligibility checks and benefit-bucket
     * consumptions are audit evidence and must never be physically removed through
     * the member screen.
     * 
     * @param memberId Member ID
     */
    @Transactional
    public void hardDeleteMember(Long memberId) {
        log.warn("⚠️ HARD DELETE member: memberId={}", memberId);

        User currentUser = authorizationService.getCurrentUser();
        if (currentUser == null || !"SUPER_ADMIN".equalsIgnoreCase(currentUser.getUserType())) {
            throw new AccessDeniedException("Only SUPER_ADMIN can permanently delete members");
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + memberId));

        // Collect all IDs to delete (principal + its dependents)
        List<Long> allIds = new java.util.ArrayList<>();
        allIds.add(memberId);
        if (!member.isDependent()) {
            List<Member> dependents = memberRepository.findByParentId(memberId);
            dependents.forEach(d -> allIds.add(d.getId()));
        }

        String idList = allIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        log.warn("⚠️ Cascade hard delete for member IDs: {}", idList);

        long claimsCount = jdbcTemplate
                .queryForObject("SELECT COUNT(*) FROM claims WHERE member_id IN (" + idList + ")", Long.class);
        long preAuthCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM preauthorization_requests WHERE member_id IN (" + idList + ")", Long.class);
        long visitsCount = jdbcTemplate
                .queryForObject("SELECT COUNT(*) FROM visits WHERE member_id IN (" + idList + ")", Long.class);
        long eligibilityChecksCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM eligibility_checks WHERE member_id IN (" + idList + ")", Long.class);
        long bucketConsumptionsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM benefit_bucket_consumptions WHERE member_id IN (" + idList + ")", Long.class);

        if (claimsCount > 0 || preAuthCount > 0 || visitsCount > 0
                || eligibilityChecksCount > 0 || bucketConsumptionsCount > 0) {
            String details = String.format(
                    "مطالبات: %d، موافقات مسبقة: %d، زيارات: %d، فحوص أهلية: %d، استهلاك سقوف: %d",
                    claimsCount, preAuthCount, visitsCount, eligibilityChecksCount, bucketConsumptionsCount);
            throw new BusinessRuleException(
                    "لا يمكن حذف المستفيد نهائياً لوجود أثر مالي أو طبي مرتبط به (" + details + "). "
                            + "استخدم الإيقاف/الأرشفة للحفاظ على سلامة السجل المالي والتدقيقي.");
        }

        jdbcTemplate.update("DELETE FROM member_policy_assignments WHERE member_id IN (" + idList + ")");
        jdbcTemplate.update("DELETE FROM member_deductibles WHERE member_id IN (" + idList + ")");

        // Delete dependents first (self-FK parent_id SET NULL is OK, but easier to
        // delete directly)
        if (!member.isDependent()) {
            memberRepository.deleteByParentId(memberId);
        }
        memberRepository.delete(member);

        log.info("✅ Member hard deleted: memberId={}", memberId);
    }
}
