package com.waad.tba.modules.provider.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.common.file.FileStorageService;
import com.waad.tba.common.file.FileUploadResult;
import com.waad.tba.modules.provider.dto.ProviderClaimRequest;
import com.waad.tba.modules.provider.dto.ProviderClaimResponse;
import com.waad.tba.modules.claim.service.ClaimService;
import com.waad.tba.modules.claim.dto.ClaimCreateDto;
import com.waad.tba.modules.claim.dto.ClaimViewDto;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Provider Claims Service.
 * 
 * Handles claim submission from healthcare providers through Provider Portal.
 * 
 * Key Features:
 * - Validate member eligibility
 * - Check annual limit before submission
 * - Check service-level limits
 * - Submit claim to existing ClaimService
 * - Return detailed response with warnings
 * 
 * @since Phase 1 - Provider Portal - Claims Submission
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderClaimsService {

    private final ClaimService claimService;
    private final ClaimRepository claimRepository;
    private final MemberRepository memberRepository;
    private final BenefitPolicyRuleRepository benefitPolicyRuleRepository;
    private final BenefitPolicyCoverageService benefitPolicyCoverageService;
    private final FileStorageService fileStorageService; // ✅ PHASE 1: File upload integration
    private final ObjectMapper objectMapper; // ✅ PHASE 1: JSON parsing for multipart

    /**
     * Submit Claim from Provider Portal.
     * 
     * Flow:
     * 1. Validate member exists and is active
     * 2. Check annual limit
     * 3. Check service-level limits (if applicable)
     * 4. Create claim via ClaimService
     * 5. Return detailed response with warnings
     * 
     * @param request          Provider claim request
     * @param providerUsername Username of provider submitting claim
     * @return Provider claim response with validation details
     */
    @Transactional
    public ProviderClaimResponse submitClaim(ProviderClaimRequest request, String providerUsername) {

        log.info("🏥 Provider claim submission: provider={}, memberId={}, amount={}, type={}",
                providerUsername, request.getMemberId(), request.getClaimedAmount(), request.getClaimType());

        // Step 1: Validate member
        Member member = validateMember(request.getMemberId());

        // Step 2: Check annual limit
        AnnualLimitCheck annualLimitCheck = checkAnnualLimit(member, request.getClaimedAmount());

        // Step 3: Check service-level limits
        // Note: serviceCode is derived from serviceCategoryId for now (deprecated flow)
        String serviceCode = request.getServiceCategoryId() != null ? String.valueOf(request.getServiceCategoryId())
                : null;
        ServiceLimitCheck serviceLimitCheck = checkServiceLimits(
                member,
                request.getServiceCategoryId(),
                request.getClaimedAmount(),
                serviceCode);

        // Step 4: Determine if can submit
        boolean canSubmit = !annualLimitCheck.exceeded && !serviceLimitCheck.exceeded;

        if (!canSubmit) {
            return buildErrorResponse(request, member, annualLimitCheck, serviceLimitCheck);
        }

        // Step 5: Create claim via existing ClaimService
        ClaimViewDto createdClaim = createClaim(request, member, providerUsername);

        // Step 6: Build success response
        return buildSuccessResponse(createdClaim, member, annualLimitCheck, serviceLimitCheck, request);
    }

    /**
     * Validate member exists and is eligible.
     */
    private Member validateMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with ID: " + memberId));

        if (Boolean.FALSE.equals(member.getActive())) {
            throw new BusinessRuleException("Member is not active - cannot submit claim");
        }

        if (member.getBenefitPolicy() == null) {
            throw new BusinessRuleException("Member has no benefit policy assigned");
        }

        // Note: employer validation removed (deprecated field)

        log.info("✅ Member validated: id={}, name={}, policy={}",
                member.getId(), member.getFullName(), member.getBenefitPolicy().getId());

        return member;
    }

    /**
     * Check annual limit for member.
     * 
     * FIXED (Audit Fix): Now uses BenefitPolicyCoverageService for real annual
     * limit tracking.
     */
    private AnnualLimitCheck checkAnnualLimit(Member member, BigDecimal claimedAmount) {
        if (member.getBenefitPolicy() == null) {
            throw new BusinessRuleException("Member has no benefit policy assigned");
        }

        BigDecimal annualLimit = member.getBenefitPolicy().getAnnualLimit();
        if (annualLimit == null || annualLimit.compareTo(BigDecimal.ZERO) <= 0) {
            // Unlimited policy
            return new AnnualLimitCheck(BigDecimal.ZERO, BigDecimal.ZERO, claimedAmount, BigDecimal.ZERO,
                    BigDecimal.ZERO, 0, 0, false, new ArrayList<>());
        }

        BigDecimal remainingLimit = benefitPolicyCoverageService.getRemainingCoverage(member, LocalDate.now());
        if (remainingLimit == null) {
            remainingLimit = annualLimit;
        }

        BigDecimal usedAmount = annualLimit.subtract(remainingLimit);
        BigDecimal remainingBefore = remainingLimit;
        BigDecimal usedAfter = usedAmount.add(claimedAmount);
        BigDecimal remainingAfter = remainingLimit.subtract(claimedAmount);

        boolean exceeded = claimedAmount.compareTo(remainingLimit) > 0;

        double usagePercentageBefore = usedAmount.divide(annualLimit, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue();
        double usagePercentageAfter = usedAfter.divide(annualLimit, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue();

        List<String> warnings = new ArrayList<>();
        if (exceeded) {
            warnings.add(String.format("❌ تجاوز الحد السنوي المتبقي (المتبقي: %.2f د.ل)", remainingLimit));
        } else if (usagePercentageAfter >= 90.0) {
            warnings.add("⚠️ اقتربت من استنفاذ الحد السنوي (تم استهلاك أكثر من 90%)");
        }

        // Warning if approaching limit (>= 80%)
        if (usagePercentageAfter >= 80.0 && !exceeded) {
            warnings.add(String.format(
                    "⚠️ بعد هذه المطالبة، سيصل استهلاك الحد السنوي إلى %.0f%%",
                    usagePercentageAfter));
        }

        // Warning if exceeded
        if (exceeded) {
            warnings.add(String.format(
                    "❌ المبلغ المطلوب (%.2f د.ل) يتجاوز الحد المتبقي (%.2f د.ل)",
                    claimedAmount, remainingBefore.max(BigDecimal.ZERO)));
        }

        log.info("💰 Annual limit check: limit={}, used={}, claimed={}, remaining={}, exceeded={}",
                annualLimit, usedAmount, claimedAmount, remainingAfter, exceeded);

        return new AnnualLimitCheck(
                annualLimit,
                usedAmount,
                usedAfter,
                remainingBefore,
                remainingAfter,
                usagePercentageBefore,
                usagePercentageAfter,
                exceeded,
                warnings);
    }

    /**
     * Check service-level limits (amountLimit, timesLimit).
     */
    private ServiceLimitCheck checkServiceLimits(Member member, Long serviceCategoryId, BigDecimal claimedAmount,
            String serviceCode) {
        if (serviceCategoryId == null) {
            return new ServiceLimitCheck(false, null, new ArrayList<>());
        }

        BenefitPolicyRule rule = benefitPolicyRuleRepository
                .findById(serviceCategoryId)
                .orElse(null);

        if (rule == null) {
            log.warn("⚠️ Service category not found: id={}", serviceCategoryId);
            return new ServiceLimitCheck(false, null, new ArrayList<>());
        }

        List<String> warnings = new ArrayList<>();
        boolean exceeded = false;

        // Use service category ID as service name (or default)
        String serviceName = "Service #" + serviceCategoryId;

        // Check amount limit
        if (rule.getAmountLimit() != null && claimedAmount.compareTo(rule.getAmountLimit()) > 0) {
            warnings.add(String.format(
                    "⚠️ المبلغ المطلوب (%.2f د.ل) يتجاوز حد الخدمة (%.2f د.ل) لـ %s",
                    claimedAmount, rule.getAmountLimit(), serviceName));
            exceeded = true;
        }

        // ✅ PHASE 1: Check times limit (count actual usage)
        int timesUsed = 0;
        int timesRemaining = 0;

        // Fix after migration - serviceCategoryId is deprecated, use serviceCode
        if (rule.getTimesLimit() != null && serviceCode != null) {
            timesUsed = calculateTimesUsed(member.getId(), serviceCode);
            timesRemaining = rule.getTimesLimit() - timesUsed;

            log.info("🔢 Times limit check: service={}, limit={}, used={}, remaining={}",
                    serviceName, rule.getTimesLimit(), timesUsed, timesRemaining);

            if (timesUsed >= rule.getTimesLimit()) {
                warnings.add(String.format(
                        "❌ تم استنفاذ العدد المسموح من خدمة %s (%d مرة في السنة)",
                        serviceName, rule.getTimesLimit()));
                exceeded = true;
            } else if (timesRemaining <= 2) {
                warnings.add(String.format(
                        "⚠️ اقتربت من الحد الأقصى لخدمة %s (متبقي %d مرة من %d)",
                        serviceName, timesRemaining, rule.getTimesLimit()));
            }
        }

        // Build limit info with actual usage
        ProviderClaimResponse.ServiceLimitInfo limitInfo = ProviderClaimResponse.ServiceLimitInfo.builder()
                .serviceName(serviceName)
                .amountLimit(rule.getAmountLimit())
                .timesLimit(rule.getTimesLimit())
                .timesUsed(timesUsed)
                .timesRemaining(timesRemaining)
                .exceedsLimit(exceeded)
                .build();

        log.info("🔍 Service limit check: service={}, amountLimit={}, exceeded={}",
                serviceName, rule.getAmountLimit(), exceeded);

        return new ServiceLimitCheck(exceeded, limitInfo, warnings);
    }

    /**
     * ✅ PHASE 1: Calculate times used for a service category.
     * Counts approved claims for the current calendar year.
     * 
     * Uses pessimistic counting (includes PENDING + UNDER_REVIEW + APPROVED)
     * to prevent race conditions when multiple claims submitted simultaneously.
     * 
     * @param memberId    Member ID
     * @param serviceCode Service code (String) - matches Claim.serviceCode
     * @return Number of times service was used (approved + pending)
     */
    private int calculateTimesUsed(Long memberId, String serviceCode) {
        LocalDate yearStart = LocalDate.of(LocalDate.now().getYear(), 1, 1);
        LocalDate yearEnd = LocalDate.of(LocalDate.now().getYear(), 12, 31);

        long count = claimRepository.countPendingAndApprovedClaimsByMemberAndServiceInPeriod(
                memberId, serviceCode, yearStart, yearEnd);

        return (int) count;
    }

    /**
     * Create claim via existing ClaimService.
     * 
     * VISIT-CENTRIC ARCHITECTURE (2026-01-15):
     * visitId is REQUIRED - passed from ProviderClaimRequest to ClaimCreateDto.
     */
    private ClaimViewDto createClaim(ProviderClaimRequest request, Member member, String providerUsername) {
        // Build ClaimCreateDto with canonical fields
        // VISIT-CENTRIC: visitId is mandatory, lines with medicalServiceId required
        ClaimCreateDto claimDto = ClaimCreateDto.builder()
                .visitId(request.getVisitId()) // REQUIRED - Visit-Centric Architecture
                .memberId(request.getMemberId())
                .providerId(request.getProviderId()) // Provider auto-fill from session
                .doctorName(providerUsername) // Provider portal user
                .diagnosisCode(request.getDiagnosisCode())
                .diagnosisDescription(request.getDiagnosisDescription() != null
                        ? request.getDiagnosisDescription()
                        : request.getDiagnosis())
                .serviceDate(request.getServiceDate())
                .lines(request.getLines()) // ClaimLineDto with medicalServiceId
                .preAuthorizationId(request.getPreAuthorizationId())
                .build();

        log.info("📝 Creating claim via ClaimService: member={}, visitId={}, lines={}",
                member.getFullName(), request.getVisitId(),
                request.getLines() != null ? request.getLines().size() : 0);

        ClaimViewDto createdClaim = claimService.createClaim(claimDto);

        log.info("✅ Claim created: id={}, visitId={}, status={}",
                createdClaim.getId(), request.getVisitId(), createdClaim.getStatus());

        return createdClaim;
    }

    /**
     * Build success response.
     */
    private ProviderClaimResponse buildSuccessResponse(
            ClaimViewDto claim,
            Member member,
            AnnualLimitCheck annualCheck,
            ServiceLimitCheck serviceCheck,
            ProviderClaimRequest request) {

        boolean hasWarnings = !annualCheck.warnings.isEmpty() || !serviceCheck.warnings.isEmpty();

        List<String> allWarnings = new ArrayList<>();
        allWarnings.addAll(annualCheck.warnings);
        allWarnings.addAll(serviceCheck.warnings);

        String nationalNumber = member.getNationalNumber() != null ? member.getNationalNumber() : "";
        String memberInfo = member.getFullName() + (nationalNumber.isEmpty() ? "" : " (" + nationalNumber + ")");

        return ProviderClaimResponse.builder()
                .success(true)
                .message(String.format("✅ المطالبة قُدمت بنجاح - المعرف: %d", claim.getId()))
                .statusCode(hasWarnings ? "WARNING" : "SUCCESS")
                .claimId(claim.getId())
                .claimReferenceNumber("CLM-" + claim.getId()) // Generate reference from ID
                .claimStatus(claim.getStatus().toString())
                .submissionTimestamp(LocalDateTime.now())
                .memberFullName(memberInfo)
                .memberBarcode(member.getBarcode())
                .claimedAmount(request.getClaimedAmount())
                .annualLimit(annualCheck.annualLimit)
                .usedAmountBefore(annualCheck.usedAmountBefore)
                .usedAmountAfter(annualCheck.usedAmountAfter)
                .remainingLimit(annualCheck.remainingAfter)
                .usagePercentage(annualCheck.usagePercentageAfter)
                .warnings(allWarnings)
                .errors(new ArrayList<>())
                .exceededLimit(false)
                .requiresPreApproval(annualCheck.usagePercentageAfter >= 90.0)
                .serviceLimitInfo(serviceCheck.limitInfo)
                .attachmentsUploaded(0) // Updated when files uploaded
                .nextSteps("المطالبة قيد المراجعة - سيتم الرد خلال 10 أيام عمل")
                .build();
    }

    /**
     * Build error response (exceeded limits).
     */
    private ProviderClaimResponse buildErrorResponse(
            ProviderClaimRequest request,
            Member member,
            AnnualLimitCheck annualCheck,
            ServiceLimitCheck serviceCheck) {

        List<String> errors = new ArrayList<>();
        errors.addAll(annualCheck.warnings);
        errors.addAll(serviceCheck.warnings);

        return ProviderClaimResponse.builder()
                .success(false)
                .message("❌ لا يمكن تقديم المطالبة - تم تجاوز الحد المسموح")
                .statusCode("ERROR")
                .memberFullName(member.getFullName())
                .memberBarcode(member.getBarcode())
                .claimedAmount(request.getClaimedAmount())
                .annualLimit(annualCheck.annualLimit)
                .usedAmountBefore(annualCheck.usedAmountBefore)
                .remainingLimit(annualCheck.remainingBefore)
                .usagePercentage(annualCheck.usagePercentageBefore)
                .warnings(new ArrayList<>())
                .errors(errors)
                .exceededLimit(annualCheck.exceeded)
                .requiresPreApproval(true)
                .serviceLimitInfo(serviceCheck.limitInfo)
                .nextSteps("يرجى التواصل مع شركة التأمين للحصول على موافقة مسبقة")
                .build();
    }

    // ==================== HELPER CLASSES ====================

    private record AnnualLimitCheck(
            BigDecimal annualLimit,
            BigDecimal usedAmountBefore,
            BigDecimal usedAmountAfter,
            BigDecimal remainingBefore,
            BigDecimal remainingAfter,
            double usagePercentageBefore,
            double usagePercentageAfter,
            boolean exceeded,
            List<String> warnings) {
    }

    private record ServiceLimitCheck(
            boolean exceeded,
            ProviderClaimResponse.ServiceLimitInfo limitInfo,
            List<String> warnings) {
    }

    // ==================== ✅ PHASE 1: FILE UPLOAD INTEGRATION ====================

    /**
     * ✅ PHASE 1: Submit Claim with File Attachments.
     * 
     * Handles multipart file upload with atomic transaction:
     * 1. Parse JSON claim data from @RequestPart
     * 2. Validate files (type, size, count)
     * 3. Submit claim (same validation as submitClaim)
     * 4. Upload files to storage
     * 5. Associate files with claim
     * 
     * Transaction Rollback:
     * - If any file upload fails, entire claim is rolled back
     * - Ensures data consistency
     * 
     * File Specifications:
     * - Allowed types: PDF, JPEG, PNG
     * - Max file size: 5 MB per file
     * - Max total size: 20 MB
     * - Max files: 10
     * 
     * @param claimJson        JSON string of ProviderClaimRequest
     * @param files            Array of multipart files (nullable - claims can have
     *                         no attachments)
     * @param providerUsername Username of provider submitting claim
     * @return ProviderClaimResponse with claim ID and upload status
     */
    @Transactional // ✅ Atomic: Rollback claim if file upload fails
    public ProviderClaimResponse submitClaimWithAttachments(
            String claimJson,
            MultipartFile[] files,
            String providerUsername) {

        log.info("🏥 Claim submission with attachments: provider={}, files={}",
                providerUsername, files != null ? files.length : 0);

        try {
            // Step 1: Parse JSON claim data
            ProviderClaimRequest request = objectMapper.readValue(claimJson, ProviderClaimRequest.class);

            // Step 2: Validate files
            if (files != null && files.length > 0) {
                validateFiles(files);
            }

            // Step 3: Submit claim (same validation as submitClaim)
            ProviderClaimResponse response = submitClaim(request, providerUsername);

            if (!response.getSuccess()) {
                // Claim validation failed (annual limit, service limit, etc.)
                return response;
            }

            // Step 4: Upload files to storage (if any)
            if (files != null && files.length > 0) {
                uploadClaimAttachments(response.getClaimId(), files);

                // Add success message for file upload
                response.setMessage(response.getMessage() +
                        String.format(" | تم رفع %d ملف بنجاح", files.length));
            }

            return response;

        } catch (Exception e) {
            log.error("❌ Error in submitClaimWithAttachments", e);
            throw new BusinessRuleException("خطأ في رفع المرفقات: " + e.getMessage());
        }
    }

    /**
     * Validate file upload constraints.
     * 
     * Rules:
     * - Max 10 files
     * - Max 5 MB per file
     * - Max 20 MB total
     * - Allowed MIME types: PDF, JPEG, PNG
     * 
     * @param files Array of multipart files
     * @throws BusinessRuleException if validation fails
     */
    private void validateFiles(MultipartFile[] files) {
        // Max 10 files
        if (files.length > 10) {
            throw new BusinessRuleException("❌ الحد الأقصى: 10 ملفات (حاليًا: " + files.length + ")");
        }

        // Allowed MIME types
        List<String> allowedMimeTypes = Arrays.asList(
                "application/pdf",
                "image/jpeg",
                "image/png");

        long totalSize = 0;

        for (MultipartFile file : files) {
            // Check MIME type
            String mimeType = file.getContentType();
            if (mimeType == null || !allowedMimeTypes.contains(mimeType.toLowerCase())) {
                throw new BusinessRuleException(
                        "❌ نوع الملف غير مسموح: " + file.getOriginalFilename() +
                                " (المسموح: PDF, JPEG, PNG فقط)");
            }

            // Check individual file size (5 MB = 5 * 1024 * 1024 bytes)
            long fileSizeMB = file.getSize();
            if (fileSizeMB > 5 * 1024 * 1024) {
                throw new BusinessRuleException(
                        "❌ حجم الملف كبير جدًا: " + file.getOriginalFilename() +
                                " (" + (fileSizeMB / 1024 / 1024) + " MB) | الحد الأقصى: 5 MB");
            }

            totalSize += fileSizeMB;
        }

        // Check total size (20 MB)
        if (totalSize > 20 * 1024 * 1024) {
            throw new BusinessRuleException(
                    "❌ الحجم الإجمالي للملفات كبير جدًا: " +
                            (totalSize / 1024 / 1024) + " MB | الحد الأقصى: 20 MB");
        }

        log.info("✅ File validation passed: {} files, total size: {} MB",
                files.length, totalSize / 1024 / 1024);
    }

    /**
     * Upload claim attachments to file storage.
     * 
     * Files are stored in: claims/{claimId}/{filename}
     * 
     * @param claimId Claim ID (for folder organization)
     * @param files   Array of multipart files
     * @throws RuntimeException if any upload fails (triggers transaction rollback)
     */
    private void uploadClaimAttachments(Long claimId, MultipartFile[] files) {
        String folder = "claims/" + claimId;

        log.info("📤 Uploading {} file(s) for claim {}", files.length, claimId);

        for (MultipartFile file : files) {
            try {
                FileUploadResult result = fileStorageService.upload(file, folder);

                log.info("✅ Uploaded: {} → {} ({} bytes)",
                        file.getOriginalFilename(),
                        result.getFileKey(),
                        file.getSize()); // Use file.getSize() instead

                // PHASE 2: Store file metadata in ClaimAttachment table
                // - claim_id, file_key, original_filename, mime_type, file_size, uploaded_at

            } catch (Exception e) {
                log.error("❌ File upload failed: {} - {}", file.getOriginalFilename(), e.getMessage());
                throw new BusinessRuleException(
                        "فشل رفع الملف: " + file.getOriginalFilename() + " - " + e.getMessage());
            }
        }
    }
}