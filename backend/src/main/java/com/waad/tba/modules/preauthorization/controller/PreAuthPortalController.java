package com.waad.tba.modules.preauthorization.controller;

import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.waad.tba.common.exception.BusinessRuleException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Provider-facing API for the Pre-Authorization Portal.
 * Handles draft creation, submission, and attachment uploads.
 */
@RestController
@RequestMapping("/api/v1/provider/preauths")
@RequiredArgsConstructor
@Slf4j
public class PreAuthPortalController {

    private final PreAuthorizationRepository preAuthorizationRepository;

    @PostMapping
    public ResponseEntity<String> createDraft() {
        // Create DRAFT pre-authorization
        return ResponseEntity.ok("Draft Created (Mock)");
    }

    @GetMapping
    public ResponseEntity<List<PreAuthorization>> getProviderPreAuths() {
        // Fetch all pre-auths for the logged in provider
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PreAuthorization> getPreAuth(@PathVariable Long id) {
        // Fetch details
        return ResponseEntity.ok(new PreAuthorization());
    }

    @PutMapping("/{id}/draft")
    public ResponseEntity<String> updateDraft(@PathVariable Long id, @RequestBody Object updateDto) {
        // Update clinical data and lines
        // Calls PreAuthPricingValidator for each line to determine status
        return ResponseEntity.ok("Draft Updated (Mock)");
    }

    @PostMapping("/bulk")
    @Transactional
    public ResponseEntity<?> submitBulkRequest(@RequestBody Map<String, Object> payload) {
        log.info("[PORTAL] Received bulk pre-auth request from UI: {}", payload);
        
        // Extract data
        @SuppressWarnings("unchecked")
        Map<String, Object> memberData = (Map<String, Object>) payload.get("member");
        @SuppressWarnings("unchecked")
        Map<String, Object> clinical = (Map<String, Object>) payload.get("clinical");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) payload.get("lines");

        // Identity is never invented. Each of these previously fell back to
        // entity 1 -- so a request naming no member, no provider and no
        // classification was persisted against whichever rows happen to hold
        // id 1, and would later resolve the WRONG benefit rule, the wrong
        // buckets and the wrong contract. Once approval places holds, that
        // takes limit from a member who never asked for the service.
        if (memberData == null || memberData.get("id") == null) {
            throw new BusinessRuleException("لا يمكن إنشاء طلب موافقة مسبقة دون تحديد المستفيد.");
        }
        Long memberId = Long.valueOf(memberData.get("id").toString());

        if (payload.get("providerId") == null) {
            throw new BusinessRuleException("لا يمكن إنشاء طلب موافقة مسبقة دون تحديد مقدم الخدمة.");
        }
        Long providerId = Long.valueOf(payload.get("providerId").toString());

        if (lines == null || lines.isEmpty()) {
            throw new BusinessRuleException("لا يمكن إنشاء طلب موافقة مسبقة دون بنود.");
        }

        List<PreAuthorization> savedRequests = new ArrayList<>();

        try {
            for (Map<String, Object> line : lines) {
                String code = (String) line.get("code");
                String name = (String) line.get("name");
                String manualPriceStr = (String) line.get("manualPrice");
                String contractPriceStr = line.get("contractPrice") != null ? line.get("contractPrice").toString() : "0";
                
                BigDecimal requestedPrice = (manualPriceStr != null && !manualPriceStr.isEmpty()) 
                        ? new BigDecimal(manualPriceStr) 
                        : new BigDecimal(contractPriceStr);
                
                BigDecimal contractPrice = new BigDecimal(contractPriceStr);

                String overrideReason = (String) line.get("overrideReason");
                String diagnosis = clinical != null ? (String) clinical.get("diagnosis") : "Diagnosis";
                String notes = clinical != null ? (String) clinical.get("chiefComplaint") : "";

                PreAuthorization preAuth = PreAuthorization.builder()
                        .preAuthNumber("PA-MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                        .memberId(memberId)
                        .providerId(providerId)
                        .serviceCode(code != null ? code : "UNLISTED")
                        .serviceName(name != null ? name : "Unknown Service")
                        // The medical classification decides which benefit rule
                        // applies, and therefore which buckets a hold lands on.
                        // There is no safe default: not id 1, not the first
                        // category, and not "no classification" (which the
                        // limit resolver would read as no ceiling at all).
                        .serviceCategoryId(requiredCategoryId(line))
                        .serviceCategoryName((String) line.get("categoryName"))
                        .status(PreAuthorization.PreAuthStatus.PENDING)
                        .priority(PreAuthorization.Priority.URGENT)
                        .requestDate(LocalDate.now())
                        // Not today by default: the policy, the assignment and
                        // the contract terms are all resolved ON this date, so
                        // substituting today prices a future service with
                        // today's configuration.
                        .expectedServiceDate(requiredExpectedServiceDate(payload))
                        .expiryDate(LocalDate.now().plusDays(5))
                        .contractPrice(contractPrice)
                        .requestedTotalAmount(requestedPrice)
                        .contractTotalAmount(contractPrice)
                        .manualTotalAmount(requestedPrice)
                        .approvedAmount(BigDecimal.ZERO)
                        .approvedTotalAmount(BigDecimal.ZERO)
                        .clinicalNotes(overrideReason)
                        .diagnosisDescription(diagnosis)
                        .notes(notes)
                        .active(true)
                        .serviceType("MEDICAL")
                        .currency("LYD")
                        .diagnosisCode("Z00.0")
                        .createdAt(LocalDateTime.now())
                        .createdBy("PROVIDER_UI")
                        .build();

                savedRequests.add(preAuthorizationRepository.saveAndFlush(preAuth));
            }
            return ResponseEntity.ok(java.util.Map.of(
                "message", "Success",
                "count", savedRequests.size()
            ));
        } catch (Exception e) {
            log.error("[PORTAL] Error saving bulk pre-auth: ", e);
            throw e;
        }
    }

    @PostMapping("/{id}/attachments")
    public ResponseEntity<String> uploadAttachment(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String attachmentType,
            @RequestParam(value = "lineId", required = false) Long lineId) {
        // Save file to disk/S3 and create PreAuthorizationAttachment entity
        return ResponseEntity.ok("Attachment Uploaded (Mock)");
    }

    /**
     * The line's medical classification. Absent, the benefit rule cannot be
     * resolved -- and an unresolved rule means no bucket applies, which the
     * limit resolver reports as "unlimited". Approving against no ceiling is
     * the worst of the available failures, so this fails closed instead.
     */
    private Long requiredCategoryId(Map<String, Object> line) {
        Object categoryId = line.get("medicalCategoryId") != null
                ? line.get("medicalCategoryId") : line.get("categoryId");
        if (categoryId == null) {
            throw new BusinessRuleException(
                    "لا يمكن قبول بند بلا تصنيف طبي؛ التصنيف يحدد قاعدة المنفعة والأوعية المنطبقة.");
        }
        return Long.valueOf(categoryId.toString());
    }

    private LocalDate requiredExpectedServiceDate(Map<String, Object> payload) {
        Object value = payload.get("expectedServiceDate");
        if (value == null) {
            throw new BusinessRuleException(
                    "لا يمكن إنشاء طلب موافقة مسبقة دون تاريخ الخدمة المتوقع.");
        }
        return LocalDate.parse(value.toString());
    }
}
