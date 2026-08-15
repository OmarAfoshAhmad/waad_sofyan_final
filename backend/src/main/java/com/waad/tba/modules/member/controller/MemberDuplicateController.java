package com.waad.tba.modules.member.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.member.dto.MemberDuplicateGroupDto;
import com.waad.tba.modules.member.dto.MemberDuplicateMergeRequestDto;
import com.waad.tba.modules.member.service.MemberDuplicateService;

import org.springframework.security.access.prepost.PreAuthorize;
import com.waad.tba.modules.member.service.MemberKinshipAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/system-settings/member-duplicates")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "System Settings - Member Duplicates")
public class MemberDuplicateController {

    private final MemberDuplicateService duplicateService;
    private final MemberKinshipAdminService kinshipAdminService;

    /**
     * Clears the kinship verification flag across EVERY member with a
     * relationship.
     *
     * It used to be a GET with no authorization of any kind. A GET is
     * fetchable by a link, a prefetch, a crawler or a browser's address bar,
     * so a system-wide write sat behind a URL anyone authenticated could
     * trigger by accident -- and it recorded no reason and no actor, leaving
     * nothing to explain afterwards why every family's verification had been
     * discarded.
     *
     * Now: POST, super-admin only, a mandatory reason, and an audit line.
     */
    @PostMapping("/reset-kinship")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<String> resetKinship(@RequestBody KinshipResetRequest request) {
        int updated = kinshipAdminService.resetVerification(request == null ? null : request.reason());
        return ApiResponse.success("Reset " + updated + " members.", "Reset successful", "تم بنجاح");
    }

    /** Carries the mandatory reason; a system-wide write must say why. */
    public record KinshipResetRequest(String reason) {}

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<MemberDuplicateGroupDto>>> getDuplicates() {
        log.info("REST request to get member duplicates");
        List<MemberDuplicateGroupDto> duplicates = duplicateService.findDuplicates();
        return ResponseEntity.ok(ApiResponse.success("Duplicates retrieved successfully", duplicates));
    }

    @PostMapping("/merge")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> mergeDuplicates(@jakarta.validation.Valid @RequestBody MemberDuplicateMergeRequestDto request) {
        log.info("REST request to merge duplicates for primary member {}", request.getPrimaryMemberId());
        duplicateService.mergeDuplicates(request.getPrimaryMemberId(), request.getDuplicateMemberIds(),
                request.getReason(), request.getExpectedVersions());
        return ResponseEntity.ok(ApiResponse.success("تم دمج السجلات المكررة بنجاح", null));
    }
}
