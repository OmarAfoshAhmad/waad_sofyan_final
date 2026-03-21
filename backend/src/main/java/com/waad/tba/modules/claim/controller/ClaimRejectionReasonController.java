package com.waad.tba.modules.claim.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.claim.entity.ClaimRejectionReason;
import com.waad.tba.modules.claim.service.ClaimRejectionReasonService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/claim-rejection-reasons")
@RequiredArgsConstructor
@Tag(name = "Claim Rejection Reasons")
public class ClaimRejectionReasonController {

    private final ClaimRejectionReasonService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAll() {
        List<Map<String, Object>> result = service.getAll().stream()
                .map(r -> Map.of("id", (Object) r.getId(), "reasonText", r.getReasonText()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @RequestBody Map<String, String> body) {
        String text = body.get("reasonText");
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("reasonText مطلوب"));
        }
        ClaimRejectionReason reason = service.findOrCreate(text);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("id", (Object) reason.getId(), "reasonText", reason.getReasonText())));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String text = body.get("reasonText");
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("reasonText مطلوب"));
        }
        ClaimRejectionReason reason = service.update(id, text);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("id", (Object) reason.getId(), "reasonText", reason.getReasonText())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
