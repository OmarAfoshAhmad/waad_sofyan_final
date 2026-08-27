package com.waad.tba.modules.preauthorization.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.preauthorization.dto.PreAuthLineDecisionDto;
import com.waad.tba.modules.preauthorization.dto.PreAuthReviewDto;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization.PreAuthStatus;
import com.waad.tba.modules.preauthorization.entity.PreAuthorizationLine;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationLineRepository;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.preauthorization.service.PreAuthReviewService;
import com.waad.tba.modules.preauthorization.service.PreAuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * واجهة برمجية للمراجع — إدارة الموافقات المسبقة على مستوى سطر الخدمة.
 *
 * المسارات:
 *   GET    /api/v1/reviewer/preauths/inbox                  — صندوق وارد المراجع
 *   POST   /api/v1/reviewer/preauths/{id}/start-review      — بدء المراجعة
 *   POST   /api/v1/reviewer/preauths/{id}/lines/{lid}/decision — قرار على سطر
 *   GET    /api/v1/reviewer/preauths/{id}/lines              — قائمة سطور الموافقة
 *   POST   /api/v1/reviewer/preauths/{id}/finalize           — إنهاء المراجعة
 */
@RestController
@RequestMapping("/api/v1/reviewer/preauths")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Reviewer - PreAuth Review", description = "واجهات المراجع للموافقات المسبقة")
public class PreAuthReviewController {

    private final PreAuthReviewService reviewService;
    private final PreAuthorizationLineRepository lineRepo;
    private final PreAuthorizationRepository preAuthRepo;

    private final PreAuthorizationService preAuthService;
    private final com.waad.tba.modules.preauthorization.api.PreAuthorizationApiMapper apiMapper;

    // ── صندوق الوارد ─────────────────────────────────────────────────────────
    @GetMapping("/inbox")
    @PreAuthorize("@permissionGuard.has('PREAUTH_REVIEW')")
    @Operation(summary = "صندوق وارد المراجع", description = "يُرجع الموافقات المسبقة الواصلة للمراجع مع تصفية اختيارية")
    public ResponseEntity<ApiResponse<com.waad.tba.modules.preauthorization.api.response.PreAuthorizationListResponse>> getInbox(
            @RequestParam(required = false) String filterStatus,
            @RequestParam(required = false) Boolean hasVariance,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(name = "sortDir", defaultValue = "ASC") String sortDir) {
        
        org.springframework.data.domain.Sort.Direction direction = org.springframework.data.domain.Sort.Direction.fromString(sortDir);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(Math.max(0, page - 1), size, org.springframework.data.domain.Sort.by(direction, sortBy));
        
        org.springframework.data.domain.Page<com.waad.tba.modules.preauthorization.dto.PreAuthorizationResponseDto> pageResult =
                preAuthService.getInboxByStatuses(resolveInboxStatuses(filterStatus), pageable);
        
        return ResponseEntity.ok(ApiResponse.success(apiMapper.toListResponse(pageResult)));
    }

    private List<PreAuthStatus> resolveInboxStatuses(String filterStatus) {
        if (filterStatus == null || filterStatus.isBlank()
                || "ALL".equalsIgnoreCase(filterStatus)
                || "OPEN".equalsIgnoreCase(filterStatus)) {
            return List.of(
                    PreAuthStatus.SUBMITTED,
                    PreAuthStatus.RESUBMITTED,
                    PreAuthStatus.PENDING,
                    PreAuthStatus.UNDER_REVIEW,
                    PreAuthStatus.INFO_REQUESTED,
                    PreAuthStatus.NEEDS_CORRECTION);
        }

        try {
            return List.of(PreAuthStatus.valueOf(filterStatus.trim().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            log.warn("Ignoring unsupported pre-authorization inbox filterStatus={}", filterStatus);
            return List.of(
                    PreAuthStatus.SUBMITTED,
                    PreAuthStatus.RESUBMITTED,
                    PreAuthStatus.PENDING,
                    PreAuthStatus.UNDER_REVIEW,
                    PreAuthStatus.INFO_REQUESTED,
                    PreAuthStatus.NEEDS_CORRECTION);
        }
    }

    // ── بدء المراجعة ─────────────────────────────────────────────────────────
    @PostMapping("/{id}/start-review")
    @PreAuthorize("@preAuthAccessGuard.canReview(#id)")
    @Operation(summary = "بدء مراجعة موافقة مسبقة", description = "يُحوّل الوضع من PENDING إلى UNDER_REVIEW")
    public ResponseEntity<ApiResponse<PreAuthorization>> startReview(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        PreAuthorization result = reviewService.startReview(id, user.getUsername());
        return ResponseEntity.ok(ApiResponse.success("تم بدء المراجعة بنجاح", result));
    }

    // ── قائمة سطور الموافقة ───────────────────────────────────────────────────
    @GetMapping("/{id}/lines")
    @PreAuthorize("@preAuthAccessGuard.canReview(#id)")
    @Operation(summary = "جلب سطور خدمات الموافقة", description = "يُرجع جميع سطور الخدمات مع حالة القرار لكل سطر")
    public ResponseEntity<ApiResponse<List<PreAuthorizationLine>>> getLines(
            @PathVariable Long id) {
        List<PreAuthorizationLine> lines = lineRepo.findByPreAuthorizationId(id);
        return ResponseEntity.ok(ApiResponse.success(lines));
    }

    // ── قرار على سطر خدمة محدد ───────────────────────────────────────────────
    @PostMapping("/{id}/lines/{lineId}/decision")
    @PreAuthorize("@preAuthAccessGuard.canReview(#id)")
    @Operation(
            summary = "قرار على سطر خدمة",
            description = "الموافقة / الموافقة الجزئية / الرفض على سطر خدمة بعينه. " +
                    "contractPrice يبقى محفوظاً ولا يُمس. " +
                    "varianceAmount يُحسب تلقائياً (contractPrice - approvedAmount).")
    public ResponseEntity<ApiResponse<PreAuthorizationLine>> makeLineDecision(
            @PathVariable Long id,
            @PathVariable Long lineId,
            @Valid @RequestBody PreAuthLineDecisionDto dto,
            @AuthenticationPrincipal UserDetails user) {
        PreAuthorizationLine result = reviewService.makeLineDecision(id, lineId, dto, user.getUsername());
        return ResponseEntity.ok(ApiResponse.success("تم حفظ قرار السطر بنجاح", result));
    }

    // ── إنهاء المراجعة ────────────────────────────────────────────────────────
    @PostMapping("/{id}/finalize")
    @PreAuthorize("@preAuthAccessGuard.canApprove(#id)")
    @Operation(
            summary = "إنهاء المراجعة النهائية",
            description = "يحسب الإجمالي المعتمد من جميع السطور ويُحدث وضع الموافقة. " +
                    "يرفض الطلب إذا كانت هناك سطور بحالة PENDING لم يُتخذ قرار بشأنها.")
    public ResponseEntity<ApiResponse<PreAuthorization>> finalizeReview(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        PreAuthorization result = reviewService.finalizeReview(id, user.getUsername());
        return ResponseEntity.ok(ApiResponse.success(
                "تمت المراجعة النهائية: " + result.getStatus().getArabicLabel(), result));
    }

    // ── رفض كامل (shortcut) ──────────────────────────────────────────────────
    @PostMapping("/{id}/reject")
    @PreAuthorize("@preAuthAccessGuard.canApprove(#id)")
    @Operation(summary = "رفض الموافقة المسبقة كلياً", description = "يرفض جميع السطور دفعةً واحدة")
    public ResponseEntity<ApiResponse<String>> rejectAll(
            @PathVariable Long id,
            @RequestParam String reason,
            @AuthenticationPrincipal UserDetails user) {
        List<PreAuthorizationLine> lines = lineRepo.findByPreAuthorizationId(id);
        PreAuthLineDecisionDto rejectDto = PreAuthLineDecisionDto.builder()
                .decisionStatus(PreAuthorizationLine.LineDecisionStatus.REJECTED)
                .decisionNotes(reason)
                .approvedAmount(BigDecimal.ZERO)
                .build();
        lines.forEach(line -> reviewService.makeLineDecision(id, line.getId(), rejectDto, user.getUsername()));
        reviewService.finalizeReview(id, user.getUsername());
        return ResponseEntity.ok(ApiResponse.success("تم رفض الموافقة المسبقة بالكامل"));
    }

    // ── طلب معلومات إضافية / إعادة للتعديل ───────────────────────────────────────────────────
    @PostMapping("/{id}/request-info")
    @PreAuthorize("@preAuthAccessGuard.canReview(#id)")
    @Operation(summary = "إعادة للتعديل (طلب معلومات إضافية من المزود)")
    public ResponseEntity<ApiResponse<String>> requestInfo(
            @PathVariable Long id,
            @RequestParam String notes,
            @AuthenticationPrincipal UserDetails user) {
        
        PreAuthReviewDto reviewDto = new PreAuthReviewDto();
        reviewDto.setStatus(PreAuthStatus.NEEDS_CORRECTION);
        reviewDto.setReviewerComment(notes);
        
        preAuthService.reviewPreAuth(id, reviewDto, user.getUsername());
        
        return ResponseEntity.ok(ApiResponse.success("تم إعادة الطلب للمزود للتعديل بنجاح"));
    }
}
