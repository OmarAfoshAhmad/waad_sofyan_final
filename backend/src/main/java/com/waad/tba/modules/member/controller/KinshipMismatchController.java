package com.waad.tba.modules.member.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.member.dto.KinshipMismatchDto;
import com.waad.tba.modules.member.dto.KinshipMismatchFixRequest;
import com.waad.tba.modules.member.service.KinshipMismatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/system-settings/kinship-mismatches")
@RequiredArgsConstructor
public class KinshipMismatchController {

    private final KinshipMismatchService kinshipMismatchService;

    // Lists members across EVERY employer, so it is a system operation and
    // not something an employer administrator may run.
    @GetMapping
    @PreAuthorize("@permissionGuard.has('SYSTEM_SETTINGS_VIEW')")
    public ResponseEntity<ApiResponse<List<KinshipMismatchDto>>> getMismatches() {
        List<KinshipMismatchDto> mismatches = kinshipMismatchService.findMismatches();
        return ResponseEntity.ok(ApiResponse.success(mismatches));
    }

    @PostMapping("/{id}/fix")
    @PreAuthorize("@permissionGuard.has('DANGER_ZONE_EXECUTE')")
    public ResponseEntity<ApiResponse<Void>> fixMismatch(@PathVariable Long id, @jakarta.validation.Valid @RequestBody KinshipMismatchFixRequest request) {
        kinshipMismatchService.fixMismatch(id, request);
        return ResponseEntity.ok(ApiResponse.success("تم إصلاح بيانات القرابة بنجاح", null));
    }

    @PostMapping("/{id}/ignore")
    @PreAuthorize("@permissionGuard.has('DANGER_ZONE_EXECUTE')")
    public ResponseEntity<ApiResponse<Void>> ignoreMismatch(@PathVariable Long id) {
        kinshipMismatchService.ignoreMismatch(id);
        return ResponseEntity.ok(ApiResponse.success("تم تأكيد صحة البيانات بنجاح", null));
    }

    @PostMapping("/bulk-fix")
    @PreAuthorize("@permissionGuard.has('DANGER_ZONE_EXECUTE')")
    public ResponseEntity<ApiResponse<Void>> fixMismatchesBulk(@jakarta.validation.Valid @RequestBody com.waad.tba.modules.member.dto.KinshipMismatchBulkFixRequest request) {
        kinshipMismatchService.fixMismatchesBulk(request);
        return ResponseEntity.ok(ApiResponse.success("تم الإصلاح الجماعي بنجاح", null));
    }

    // Takes an arbitrary list of member ids with no ownership check inside,
    // so nothing but this gate scopes it. It reaches any member in any
    // employer.
    @PostMapping("/bulk-ignore")
    @PreAuthorize("@permissionGuard.has('DANGER_ZONE_EXECUTE')")
    public ResponseEntity<ApiResponse<Void>> ignoreMismatchesBulk(@RequestBody List<Long> memberIds) {
        kinshipMismatchService.ignoreMismatchesBulk(memberIds);
        return ResponseEntity.ok(ApiResponse.success("تم تجاهل الأخطاء جماعياً بنجاح", null));
    }
}
