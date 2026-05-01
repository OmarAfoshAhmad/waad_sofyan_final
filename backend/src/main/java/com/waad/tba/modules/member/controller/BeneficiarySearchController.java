package com.waad.tba.modules.member.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.member.dto.MemberViewDto;
import com.waad.tba.modules.member.service.BeneficiarySearchService;
import com.waad.tba.modules.member.service.search.BeneficiarySearchType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping({ "/api/beneficiaries", "/api/v1/beneficiaries" })
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Beneficiary Search", description = "Deterministic beneficiary search using explicit search mode")
@PreAuthorize("isAuthenticated()")
public class BeneficiarySearchController {

    private final BeneficiarySearchService beneficiarySearchService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'EMPLOYER_ADMIN', 'PROVIDER_STAFF', 'MEDICAL_REVIEWER')")
    @Operation(summary = "Search beneficiaries using explicit mode", description = "Deterministic search endpoint. Requires type and value, no guessing logic.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Search completed", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request parameters")
    })
    public ResponseEntity<ApiResponse<List<MemberViewDto>>> search(
            @Parameter(description = "Search type", required = true, example = "BY_NAME") @RequestParam(name = "type") BeneficiarySearchType type,
            @Parameter(description = "Search value", required = true, example = "Ahmed") @RequestParam(name = "value") String value,
            @Parameter(description = "Optional employer scope") @RequestParam(name = "employerId", required = false) Long employerId,
            @Parameter(description = "Optional member status filter (default ACTIVE)", example = "ACTIVE") @RequestParam(name = "status", required = false) String status,
            @Parameter(description = "Maximum results (default 20, max 50)", example = "20") @RequestParam(name = "size", required = false) Integer size) {

        if (type == null || value == null || value.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "type and value are required");
        }

        log.info("Beneficiary search request - type={}, value={}, employerId={}, status={}, size={}",
                type, value, employerId, status, size);

        List<MemberViewDto> data = beneficiarySearchService.search(type, value, employerId, status, size);

        return ResponseEntity.ok(ApiResponse.<List<MemberViewDto>>builder()
                .status("success")
                .message(String.format("Found %d beneficiaries", data.size()))
                .data(data)
                .timestamp(LocalDateTime.now())
                .build());
    }
}
