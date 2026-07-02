package com.waad.tba.modules.preauthorization.controller;

import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Reviewer-facing API for the Pre-Authorization Portal.
 * Handles inbox, fetching details, and decision making (Approve, Reject, Request Info).
 */
@RestController
@RequestMapping("/api/v1/reviewer/preauths")
public class PreAuthReviewController {

    @GetMapping("/inbox")
    public ResponseEntity<List<PreAuthorization>> getInbox(
            @RequestParam(required = false) String filterStatus,
            @RequestParam(required = false) Boolean hasVariance) {
        // Fetch queue based on filters (e.g. pending, unlisted, variance)
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PreAuthorization> getRequestDetails(@PathVariable Long id) {
        return ResponseEntity.ok(new PreAuthorization());
    }

    @PostMapping("/{id}/start-review")
    public ResponseEntity<String> startReview(@PathVariable Long id) {
        // Change status from PENDING -> UNDER_REVIEW
        // Assign to current reviewer
        return ResponseEntity.ok("Review Started (Mock)");
    }

    @PostMapping("/{id}/lines/{lineId}/decision")
    public ResponseEntity<String> makeLineDecision(
            @PathVariable Long id,
            @PathVariable Long lineId,
            @RequestBody Object decisionDto) {
        // Update decisionStatus for a single line (APPROVED, REJECTED, INFO_REQUESTED)
        // Set approvedAmount and decisionNotes
        return ResponseEntity.ok("Line Decision Saved (Mock)");
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<String> approveRequest(@PathVariable Long id) {
        // Approve all lines or process partial approval based on individual line decisions
        // Transition PreAuth Status -> APPROVED or PARTIALLY_APPROVED
        // Generate WAAD-PA-YYYY-XXXXX Auth Number
        // Create Reserve
        return ResponseEntity.ok("Request Approved (Mock)");
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<String> rejectRequest(@PathVariable Long id, @RequestBody Object rejectionDto) {
        // Reject all lines
        // Transition PreAuth Status -> REJECTED
        return ResponseEntity.ok("Request Rejected (Mock)");
    }

    @PostMapping("/{id}/request-info")
    public ResponseEntity<String> requestInfo(@PathVariable Long id, @RequestBody Object infoDto) {
        // Transition PreAuth Status -> INFO_REQUESTED
        return ResponseEntity.ok("Info Requested (Mock)");
    }
}
