package com.waad.tba.modules.claim.controller;

import com.waad.tba.modules.claim.dto.ClaimBatchResponse;
import com.waad.tba.modules.claim.entity.ClaimBatch;
import com.waad.tba.modules.claim.service.ClaimBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API for mandatory monthly claim batches.
 * 
 * Endpoints:
 *   GET  /api/v1/claim-batches/current  → Read-only: returns existing batch (null if none)
 *   POST /api/v1/claim-batches/current  → Creates batch if absent, responds 409 if closed/expired
 *   GET  /api/v1/claim-batches          → Search by employer and period
 */
@RestController
@RequestMapping("/api/v1/claim-batches")
@RequiredArgsConstructor
@Slf4j
public class ClaimBatchController {

    private final ClaimBatchService claimBatchService;

    /**
     * READ-ONLY: Returns existing batch for provider+employer+period.
     * Returns 404 if no batch has been opened yet.
     * Does NOT create a new batch (safe GET).
     */
    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'INSURANCE_ADMIN', 'EMPLOYER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
    public ResponseEntity<ClaimBatchResponse> getCurrentBatch(
            @RequestParam Long providerId,
            @RequestParam Long employerId,
            @RequestParam int year,
            @RequestParam int month) {
        
        ClaimBatch batch = claimBatchService.getExistingBatch(providerId, employerId, year, month);
        if (batch == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ClaimBatchResponse.from(batch));
    }

    /**
     * CREATE: Opens a new monthly batch (or returns existing one if already open).
     * Validates: not future, not older than 3 months.
     */
    @PostMapping("/current")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'INSURANCE_ADMIN', 'EMPLOYER_ADMIN', 'PROVIDER_STAFF')")
    public ResponseEntity<ClaimBatchResponse> openOrGetBatch(
            @RequestParam Long providerId,
            @RequestParam Long employerId,
            @RequestParam int year,
            @RequestParam int month) {
        
        log.info("📂 Opening batch for provider={}, employer={}, period={}/{}", providerId, employerId, month, year);
        ClaimBatch existing = claimBatchService.getExistingBatch(providerId, employerId, year, month);
        if (existing != null) {
            return ResponseEntity.ok(ClaimBatchResponse.from(existing));
        }
        
        // If not found, explicitly create and return 201
        ClaimBatch batch = claimBatchService.createBatch(providerId, employerId, year, month);
        return ResponseEntity.status(HttpStatus.CREATED).body(ClaimBatchResponse.from(batch));
    }

    /**
     * Search batches by employer and period.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'INSURANCE_ADMIN', 'EMPLOYER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
    public ResponseEntity<List<ClaimBatchResponse>> getBatches(
            @RequestParam(required = false) Long employerId,
            @RequestParam int year,
            @RequestParam int month) {
        
        List<ClaimBatch> batches = claimBatchService.findBatches(employerId, year, month);
        return ResponseEntity.ok(batches.stream().map(ClaimBatchResponse::from).toList());
    }
}
