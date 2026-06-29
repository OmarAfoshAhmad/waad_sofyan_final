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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.JdbcTemplate;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/system-settings/member-duplicates")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "System Settings - Member Duplicates")
public class MemberDuplicateController {

    private final MemberDuplicateService duplicateService;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/reset-kinship")
    public ApiResponse<String> resetKinship() {
        int updated = jdbcTemplate.update("UPDATE members SET kinship_verified = false WHERE relationship IS NOT NULL");
        return ApiResponse.success("Reset " + updated + " members.", "Reset successful", "تم بنجاح");
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MemberDuplicateGroupDto>>> getDuplicates() {
        log.info("REST request to get member duplicates");
        List<MemberDuplicateGroupDto> duplicates = duplicateService.findDuplicates();
        return ResponseEntity.ok(ApiResponse.success("Duplicates retrieved successfully", duplicates));
    }

    @PostMapping("/merge")
    public ResponseEntity<ApiResponse<Void>> mergeDuplicates(@RequestBody MemberDuplicateMergeRequestDto request) {
        log.info("REST request to merge duplicates for primary member {}", request.getPrimaryMemberId());
        duplicateService.mergeDuplicates(request.getPrimaryMemberId(), request.getDuplicateMemberIds());
        return ResponseEntity.ok(ApiResponse.success("تم دمج السجلات المكررة بنجاح", null));
    }
}
