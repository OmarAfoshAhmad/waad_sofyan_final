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
import com.waad.tba.modules.member.entity.EmployerAssignmentSource;
import com.waad.tba.modules.member.entity.PolicyAssignmentSource;
import com.waad.tba.modules.member.entity.StatusSource;
import com.waad.tba.modules.member.mapper.UnifiedMemberMapper;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.member.security.AuthorizedMemberScope;
import com.waad.tba.modules.member.security.MemberOperation;
import com.waad.tba.modules.member.security.MemberCommandAccessPolicy;
import com.waad.tba.modules.member.security.MemberQueryAccessPolicy;
import com.waad.tba.modules.member.security.MemberScopeFilter;
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
    private final MemberFinancialSummaryService financialSummaryService;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;
    private final FamilyEligibilityService familyEligibilityService;
    private final MemberStatusTransitionService statusTransitionService;
    private final MemberPolicyResolver memberPolicyResolver;
    private final MemberEmployerResolver memberEmployerResolver;
    private final MemberQueryAccessPolicy queryAccessPolicy;
    private final MemberCommandAccessPolicy commandAccessPolicy;

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
        commandAccessPolicy.require(MemberOperation.CREATE_MEMBER, dto.getEmployerId());
        Long scopedEmployerId = dto.getEmployerId();
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
        Member.MemberStatus initialStatus = Boolean.FALSE.equals(dto.getActive())
                ? Member.MemberStatus.TERMINATED
                : (dto.getStatus() != null ? dto.getStatus() : Member.MemberStatus.ACTIVE);
        statusTransitionService.initializeStatus(principal, initialStatus, StatusSource.MANUAL,
                "تهيئة حالة المستفيد عند الإنشاء اليدوي");

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
        recordInitialEmployerAssignment(principal, "تعيين جهة العمل عند إنشاء المستفيد");
        recordInitialPolicyAssignment(principal, "تعيين وثيقة عند إنشاء المستفيد");
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
        commandAccessPolicy.require(MemberOperation.ADD_DEPENDENT, employerIdOf(principal));

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
        commandAccessPolicy.require(MemberOperation.ADD_DEPENDENT, employerIdOf(principal));

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

        statusTransitionService.initializeStatus(dependent,
                Boolean.FALSE.equals(dto.getActive()) ? Member.MemberStatus.TERMINATED : Member.MemberStatus.ACTIVE,
                StatusSource.MANUAL, "تهيئة حالة التابع عند الإنشاء اليدوي");

        // 4. Save
        dependent = memberRepository.save(dependent);
        recordInitialEmployerAssignment(dependent, "تعيين جهة العمل عند إنشاء التابع (موروثة من الموظف الرئيسي)");
        recordInitialPolicyAssignment(dependent, "تعيين وثيقة عند إنشاء التابع (موروثة من الموظف الرئيسي)");
        log.info("✅ Created DEPENDENT member ID={}, cardNumber={}, relationship={}",
                dependent.getId(), dependent.getCardNumber(), dependent.getRelationship());

        return dependent;
    }

    /**
     * Records the assignment row that makes a newly-created member's policy
     * resolvable BY DATE (MemberPolicyResolver), not just via the denormalized
     * members.benefit_policy_id pointer. Without this a new member would have
     * a pointer but no dated assignment, and every dated resolution would fall
     * through to the resolver's legacy-gap warning path.
     *
     * Start date is the member's own start date when known, else today --
     * never earlier, so a new member never appears to have been covered before
     * they existed.
     */
    private void recordInitialPolicyAssignment(Member member, String reason) {
        if (member.getBenefitPolicy() == null) {
            return;
        }
        User currentUser = authorizationService.getCurrentUser();
        memberPolicyResolver.assignPolicy(member, member.getBenefitPolicy(),
                member.getStartDate() != null ? member.getStartDate() : LocalDate.now(),
                reason, PolicyAssignmentSource.MANUAL,
                currentUser != null ? currentUser.getId() : null);
    }

    private void recordInitialEmployerAssignment(Member member, String reason) {
        if (member.getEmployer() == null) {
            throw new BusinessRuleException("جهة العمل إلزامية عند إنشاء المستفيد");
        }
        User currentUser = authorizationService.getCurrentUser();
        memberEmployerResolver.assignEmployer(member, member.getEmployer(),
                member.getStartDate() != null ? member.getStartDate() : LocalDate.now(),
                reason, EmployerAssignmentSource.MANUAL,
                currentUser != null ? currentUser.getId() : null);
    }

    /**
     * The generic update path may only change descriptive attributes. Anything
     * that alters a member's LIFECYCLE, MONEY or IDENTITY belongs to a
     * dedicated, reasoned, audited operation -- routing it through here would
     * bypass the status transition service, the policy assignment record, and
     * every audit trail attached to them.
     *
     * The rule is applied to CHANGES, not to mere presence: an edit form that
     * round-trips the member's current employerId or relationship unchanged is
     * harmless and must keep working, while changing either through this path
     * is refused. Silently dropping the field instead (as the mapper still does
     * for status/active) is worse than refusing -- the caller is told the save
     * succeeded and never learns their change was discarded.
     */
    private void rejectSensitiveFieldChanges(Member member, MemberUpdateDto dto) {
        List<String> violations = new ArrayList<>();

        if (dto.getStatus() != null && dto.getStatus() != member.getStatus()) {
            violations.add("حالة العضوية (status): استخدم PATCH /{id}/status أو POST /{id}/terminate");
        }
        if (dto.getActive() != null && !dto.getActive().equals(member.getActive())) {
            violations.add("حالة التفعيل (active): استخدم PATCH /{id}/active");
        }
        Long currentPolicyId = member.getBenefitPolicy() != null ? member.getBenefitPolicy().getId() : null;
        if (dto.getBenefitPolicyId() != null && !dto.getBenefitPolicyId().equals(currentPolicyId)) {
            violations.add("وثيقة المنافع (benefitPolicyId): تغيير الوثيقة عملية مستقلة تتطلب تاريخ سريان وسبب"
                    + " (غير متاحة عبر هذا المسار)");
        }
        Long currentEmployerId = member.getEmployer() != null ? member.getEmployer().getId() : null;
        if (dto.getEmployerId() != null && !dto.getEmployerId().equals(currentEmployerId)) {
            violations.add("جهة العمل (employerId): نقل جهة العمل عملية مستقلة تتطلب تاريخ سريان وسبب"
                    + " (غير متاحة عبر هذا المسار)");
        }
        if (dto.getRelationship() != null && dto.getRelationship() != member.getRelationship()) {
            violations.add("صلة القرابة (relationship): تغيير البناء الأسري عملية مستقلة (غير متاحة عبر هذا المسار)");
        }
        // Normalized comparison: a blank string means "not supplied" (not "clear
        // the card number"), and surrounding whitespace is a representation
        // difference, not an edit. Comparing raw strings here would reject a
        // request that changes nothing -- the exact false positive that makes a
        // guard like this get disabled instead of fixed.
        String submittedCard = normalizeForComparison(dto.getCardNumber());
        if (submittedCard != null && !submittedCard.equals(normalizeForComparison(member.getCardNumber()))) {
            violations.add("رقم البطاقة (cardNumber): هوية نظامية مرتبطة بالباركود، تغييرها عملية مستقلة مدقَّقة"
                    + " (غير متاحة عبر هذا المسار)");
        }

        if (!violations.isEmpty()) {
            throw new BusinessRuleException(
                    "لا يمكن تعديل الحقول الحساسة التالية عبر التعديل العام:\n- "
                            + String.join("\n- ", violations));
        }
    }

    /** null and blank both mean "not supplied"; whitespace is not an edit. */
    private static String normalizeForComparison(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Update a member (principal or dependent) -- DESCRIPTIVE fields only.
     * Lifecycle, money and identity changes are refused here; see
     * {@link #rejectSensitiveFieldChanges}.
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
        commandAccessPolicy.require(MemberOperation.EDIT_DEMOGRAPHICS, employerIdOf(member));

        rejectSensitiveFieldChanges(member, dto);

        // Update common fields
        mapper.updateEntityFromDto(member, dto);

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
     * Compatibility alias over {@link MemberStatusTransitionService}. Used to
     * write the {@code active} flag directly and independently of
     * {@code status} -- exactly the divergence that let a member end up
     * status=SUSPENDED with active=true, since nothing kept them in sync.
     * Now: active=true translates to "restore from SUSPENDED" (rejects a
     * TERMINATED member -- reinstating one is an exceptional action, not a
     * flag flip, see {@link #reinstateTerminatedMember}); active=false
     * translates to "suspend" and requires a reason, same as the dedicated
     * suspend path.
     *
     * @param id     Member ID
     * @param active true = restore from suspension, false = suspend
     * @param reason Required when active=false
     * @return Updated member view DTO
     */
    @Transactional
    public MemberViewDto toggleActive(Long id, boolean active, String reason) {
        log.info("🔄 Setting active={} for member ID={}", active, id);

        Member stored = requireStoredMember(id);
        commandAccessPolicy.require(active ? MemberOperation.REINSTATE : MemberOperation.CHANGE_STATUS,
                employerIdOf(stored));
        User currentUser = authorizationService.getCurrentUser();
        Long userId = currentUser != null ? currentUser.getId() : null;

        Member member = active
                ? statusTransitionService.restoreFromSuspended(id, reason, userId)
                : statusTransitionService.suspend(id, reason, userId);

        log.info("✅ Member ID={} active status set to {}", id, active);

        if (member.isPrincipal()) {
            List<Member> dependents = memberRepository.findByParentId(member.getId());
            return mapper.toViewDto(member, dependents);
        }
        return mapper.toViewDto(member);
    }

    /**
     * Explicitly transition a member's membership status (ACTIVE / SUSPENDED / PENDING / TERMINATED).
     * Delegates the actual status/active write, family cascade, and history recording to
     * {@link MemberStatusTransitionService} -- this method only resolves the specific target-status
     * transition to call and adapts the result to this controller's DTO shape.
     *
     * @param id        Member ID
     * @param newStatus Target status
     * @param reason    Reason (required for SUSPENDED/TERMINATED via the transition service)
     * @return Updated member view DTO
     */
    @Transactional
    public MemberViewDto changeStatus(Long id, Member.MemberStatus newStatus, String reason) {
        if (newStatus == null) {
            throw new BusinessRuleException("يجب تحديد الحالة الجديدة");
        }

        User currentUser = authorizationService.getCurrentUser();
        Member stored = requireStoredMember(id);
        MemberOperation operation = switch (newStatus) {
            case ACTIVE -> MemberOperation.REINSTATE;
            case TERMINATED -> MemberOperation.TERMINATE;
            case SUSPENDED, PENDING -> MemberOperation.CHANGE_STATUS;
            case DUPLICATE_MERGED -> throw new BusinessRuleException(
                    "حالة الدمج تُنشأ حصراً من عملية معالجة المكررات");
        };
        commandAccessPolicy.require(operation, employerIdOf(stored));
        Long userId = currentUser != null ? currentUser.getId() : null;

        Member member = switch (newStatus) {
            case ACTIVE -> statusTransitionService.restoreFromSuspended(id, reason, userId);
            case SUSPENDED -> statusTransitionService.suspend(id, reason, userId);
            case TERMINATED -> statusTransitionService.terminateMembership(id, reason, userId, StatusSource.MANUAL);
            case PENDING -> statusTransitionService.transitionTo(id, Member.MemberStatus.PENDING, reason,
                    StatusSource.MANUAL, java.util.UUID.randomUUID().toString(), userId);
            case DUPLICATE_MERGED -> throw new BusinessRuleException(
                    "حالة الدمج تُنشأ حصراً من عملية معالجة المكررات");
        };

        log.info("✅ Member ID={} status changed to: {}", id, newStatus);

        if (member.isPrincipal()) {
            List<Member> dependents = memberRepository.findByParentId(member.getId());
            return mapper.toViewDto(member, dependents);
        }
        return mapper.toViewDto(member);
    }

    /**
     * TERMINATED -> ACTIVE. Exceptional action: requires SUPER_ADMIN and a
     * mandatory reason, unlike the ordinary {@link #toggleActive}/restore
     * path (which explicitly refuses to touch a TERMINATED member).
     */
    @Transactional
    public MemberViewDto reinstateTerminatedMember(Long id, String reason) {
        Member stored = requireStoredMember(id);
        commandAccessPolicy.require(MemberOperation.REINSTATE, employerIdOf(stored));
        User currentUser = authorizationService.getCurrentUser();
        boolean isSuperAdmin = currentUser != null && "SUPER_ADMIN".equalsIgnoreCase(currentUser.getUserType());
        Member member = statusTransitionService.reinstateTerminated(id, reason,
                currentUser != null ? currentUser.getId() : null, isSuperAdmin);
        if (member.isPrincipal()) {
            List<Member> dependents = memberRepository.findByParentId(member.getId());
            return mapper.toViewDto(member, dependents);
        }
        return mapper.toViewDto(member);
    }

    /**
     * Restores exactly the dependents ONE specific family-cascade operation
     * affected (see {@link MemberStatusTransitionService#restoreFamily}) --
     * an explicit, opt-in action, never triggered automatically by restoring
     * the principal.
     */
    @Transactional
    public MemberStatusTransitionService.FamilyRestoreResult restoreFamily(String transitionId) {
        commandAccessPolicy.requireBulk(MemberOperation.REINSTATE,
                statusTransitionService.familyCascadeEmployerIds(transitionId));
        User currentUser = authorizationService.getCurrentUser();
        Long userId = currentUser != null ? currentUser.getId() : null;
        return statusTransitionService.restoreFamily(transitionId, userId);
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
        queryAccessPolicy.requireMember(MemberOperation.VIEW_DETAILS, employerIdOf(member));

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
        queryAccessPolicy.requireMember(MemberOperation.VIEW_DETAILS, employerIdOf(principal));

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
     * Ends a member's membership (principal or dependent) -- what used to be
     * called "delete" here, but nothing is deleted: this sets
     * status=TERMINATED (active becomes false as a consequence, never
     * independently) via {@link MemberStatusTransitionService}, which
     * preserves all financial/medical history, cascades to currently-
     * ACTIVE dependents only, and records the transition in the append-only
     * status history. Existing financial and medical records are retained.
     *
     * @param id     Member ID
     * @param reason Mandatory reason recorded on the transition
     */
    @Transactional
    public void terminateMembership(Long id, String reason) {
        Member stored = requireStoredMember(id);
        commandAccessPolicy.require(MemberOperation.TERMINATE, employerIdOf(stored));
        User currentUser = authorizationService.getCurrentUser();
        statusTransitionService.terminateMembership(id, reason,
                currentUser != null ? currentUser.getId() : null, StatusSource.MANUAL);
        log.info("✅ Membership terminated for member ID={}", id);
    }

    /**
     * @deprecated kept only for existing callers of the old name; delegates
     *             entirely to {@link #terminateMembership(Long, String)}.
     *             "Delete" was always the wrong word here -- this never
     *             removed a row, it ends membership. Use
     *             {@link #terminateMembership(Long, String)} directly in new
     *             code.
     */
    @Deprecated
    @Transactional
    public void deleteMember(Long id) {
        Member stored = requireStoredMember(id);
        commandAccessPolicy.require(MemberOperation.TERMINATE, employerIdOf(stored));
        User currentUser = authorizationService.getCurrentUser();
        statusTransitionService.terminateMembership(id, "LEGACY_TERMINATE_ENDPOINT",
                currentUser != null ? currentUser.getId() : null, StatusSource.SYSTEM);
    }

    /**
     * Compatibility bulk termination, deliberately atomic: every stored
     * member is authorised before the first transition. A failure on any
     * member rolls the whole transaction back, so the response can never
     * claim a partially completed selection as a successful bulk action.
     */
    @Transactional
    public void bulkTerminateMemberships(java.util.Collection<Long> memberIds) {
        bulkTerminateMemberships(memberIds, "LEGACY_BULK_TERMINATE_ENDPOINT");
    }

    /**
     * @param reason must be the caller's actual justification, not a
     *               placeholder -- this ends coverage for every id in the
     *               batch and is recorded once per member in the same
     *               append-only status history a single termination uses.
     */
    @Transactional
    public void bulkTerminateMemberships(java.util.Collection<Long> memberIds, String reason) {
        if (memberIds == null || memberIds.isEmpty()) {
            throw new BusinessRuleException("يجب تحديد مستفيد واحد على الأقل");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("سبب إنهاء العضوية إلزامي");
        }
        java.util.List<Member> members = memberIds.stream()
                .distinct()
                .map(this::requireStoredMember)
                .toList();
        commandAccessPolicy.requireBulk(MemberOperation.BULK_OPERATION,
                members.stream().map(this::employerIdOf).toList());

        User currentUser = authorizationService.getCurrentUser();
        Long userId = currentUser != null ? currentUser.getId() : null;
        for (Member member : members) {
            statusTransitionService.terminateMembership(member.getId(),
                    reason, userId, StatusSource.SYSTEM);
        }
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
     * Get all members with pagination and optional filters.
     *
     * @param pageable   Pagination info
     * @param employerId Optional employer filter
     * @param status     Optional status filter
     * @param type       Optional member type filter (PRINCIPAL/DEPENDENT)
     * @return Page of members
     *
     *         SECURITY: the caller's reach is decided by MemberQueryAccessPolicy
     *         and applied through MemberScopeFilter. A caller outside scope is
     *         refused (403), not served an empty page.
     */
    @Transactional(readOnly = true)
    public Page<MemberViewDto> getAllMembers(
            Pageable pageable,
            Long employerId,
            String status,
            String type) {

        log.info("Fetching all members: page={}, size={}, employerId={}, status={}, type={}",
                pageable.getPageNumber(), pageable.getPageSize(), employerId, status, type);

        final AuthorizedMemberScope scope = queryAccessPolicy.requireListing(
                MemberOperation.LIST, employerId);

        Specification<Member> spec = MemberFilter.listing(status, type).toSpecification(scope);

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
        final com.waad.tba.modules.member.mapper.UnifiedMemberMapper.ReadContext readContext =
                new com.waad.tba.modules.member.mapper.UnifiedMemberMapper.ReadContext(scope.maskSensitiveFields());
        return membersPage.map(member -> {
            if (member.isPrincipal()) {
                List<Member> dependents = finalDependentsMap.getOrDefault(member.getId(), List.of());
                return mapper.toViewDto(member, dependents, readContext);
            }
            return mapper.toViewDto(member, List.of(), readContext);
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

        // The same authorisation as getAllMembers, deliberately: a count is a
        // listing whose rows were summed. Answering it under a looser rule
        // would leak the size of a tenant's roster to someone barred from
        // reading it.
        final AuthorizedMemberScope scope = queryAccessPolicy.requireListing(
                MemberOperation.LIST, employerId);

        Specification<Member> spec = MemberFilter.listing(status, type).toSpecification(scope);

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
     *         SECURITY: the caller's reach is decided by MemberQueryAccessPolicy
     *         and applied through MemberScopeFilter. A caller outside scope is
     *         refused (403), not served an empty page.
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

        // Before the short-query shortcut, not after: authorisation decides
        // whether this caller may search at all, and a refusal that arrives as
        // an empty page is indistinguishable from a search that found nothing.
        final AuthorizedMemberScope scope = queryAccessPolicy.requireListing(
                MemberOperation.SEARCH, employerId);

        boolean hasNameSearch = hasText(nameAr) || hasText(nameEn);
        boolean hasExactIdentifierSearch = hasText(nationalNumber) || hasText(barcode) || hasText(cardNumber);
        if (hasNameSearch && !hasExactIdentifierSearch
                && isShortTextSearch(nameAr) && isShortTextSearch(nameEn)) {
            log.info("Skipping member name search shorter than {} characters", MIN_MEMBER_TEXT_SEARCH_LENGTH);
            return Page.empty(pageable);
        }

        Specification<Member> spec = new MemberFilter(nameAr, nameEn, nationalNumber, barcode, cardNumber,
                benefitPolicyId, status, type,
                deleted ? MemberFilter.DeletedMode.DELETED_ONLY : MemberFilter.DeletedMode.ACTIVE_ONLY)
                .toSpecification(scope);

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
        final com.waad.tba.modules.member.mapper.UnifiedMemberMapper.ReadContext readContext =
                new com.waad.tba.modules.member.mapper.UnifiedMemberMapper.ReadContext(scope.maskSensitiveFields());
        return membersPage.map(member -> {
            if (member.isPrincipal()) {
                List<Member> dependents = finalDependentsMap.getOrDefault(member.getId(), List.of());
                return mapper.toViewDto(member, dependents, readContext);
            }
            return mapper.toViewDto(member, List.of(), readContext);
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
        queryAccessPolicy.requireMember(MemberOperation.VIEW_DETAILS, employerIdOf(principal));

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
        queryAccessPolicy.requireMember(MemberOperation.VIEW_DETAILS, employerIdOf(principal));

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

        Member member = requirePhotoWriteAccess(memberId);

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
        Member member = requirePhotoReadAccess(memberId);

        return member.getProfilePhotoPath();
    }

    /**
     * Verify member-photo resource access before callers upload or touch storage.
     */
    @Transactional(readOnly = true)
    public void assertCanAccessMemberPhoto(Long memberId) {
        requirePhotoWriteAccess(memberId);
    }

    private Member requirePhotoReadAccess(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + memberId));
        queryAccessPolicy.requireMember(MemberOperation.VIEW_DETAILS, employerIdOf(member));
        return member;
    }

    private Member requirePhotoWriteAccess(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + memberId));
        commandAccessPolicy.require(MemberOperation.EDIT_DEMOGRAPHICS, employerIdOf(member));
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
     * Restore a SUSPENDED (or PENDING) member to ACTIVE. Refuses a
     * TERMINATED member -- reinstating one is a separate, elevated-privilege
     * action, see {@link #reinstateTerminatedMember}.
     *
     * @param memberId Member ID
     * @return Restored member view DTO
     */
    @Transactional
    public MemberViewDto restoreMember(Long memberId, String reason) {
        log.info("♻️ Restoring member: memberId={}", memberId);

        Member stored = requireStoredMember(memberId);
        commandAccessPolicy.require(MemberOperation.REINSTATE, employerIdOf(stored));
        User currentUser = authorizationService.getCurrentUser();

        Member saved = statusTransitionService.restoreFromSuspended(memberId, reason,
                currentUser != null ? currentUser.getId() : null);

        log.info("✅ Member restored to ACTIVE: memberId={}", memberId);

        return mapper.toViewDto(saved);
    }

    /**
     * Permanently delete a member (hard delete). Warning: this cannot be
     * undone! Delegates entirely to {@link MemberStatusTransitionService#hardDelete},
     * which blocks the operation if any financial/medical/audit footprint
     * exists and writes an independent (non-FK'd) audit record before
     * deleting.
     *
     * @param memberId Member ID
     * @param reason   Mandatory reason for the permanent deletion
     */
    @Transactional
    public void hardDeleteMember(Long memberId, String reason) {
        log.warn("⚠️ HARD DELETE member: memberId={}", memberId);

        Member stored = requireStoredMember(memberId);
        commandAccessPolicy.require(MemberOperation.HARD_DELETE, employerIdOf(stored));
        User currentUser = authorizationService.getCurrentUser();
        boolean isSuperAdmin = currentUser != null && "SUPER_ADMIN".equalsIgnoreCase(currentUser.getUserType());

        statusTransitionService.hardDelete(memberId, reason,
                currentUser != null ? currentUser.getId() : null,
                currentUser != null ? currentUser.getUsername() : null,
                isSuperAdmin);

        log.info("✅ Member hard deleted: memberId={}", memberId);
    }

    private Member requireStoredMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + memberId));
    }

    private Long employerIdOf(Member member) {
        return member.getEmployer() == null ? null : member.getEmployer().getId();
    }
}
