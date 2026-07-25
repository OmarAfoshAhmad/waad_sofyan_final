package com.waad.tba.modules.admin.system;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.common.error.ApiError;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/system")
@RequiredArgsConstructor
@Tag(name = "System Administration", description = "Administrative endpoints for test data management and environment initialization")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("isAuthenticated()")
public class SystemAdminController {

    private final SystemAdminService service;

    @DeleteMapping("/reset")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Reset test data", description = "Deletes domain data: claims, visits, members, employers. Users are preserved. Blocked outside dev/test profiles.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Test data cleared successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = "{\n  \"status\": \"error\",\n  \"code\": \"INTERNAL_ERROR\",\n  \"message\": \"Unexpected failure during reset\",\n  \"timestamp\": \"2025-11-20T10:00:00Z\",\n  \"path\": \"/api/admin/system/reset\"\n}")))
    })
    public ResponseEntity<ApiResponse<Void>> reset() {
        return ResponseEntity.ok(service.resetTestData());
    }

    @PostMapping("/init-defaults")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Initialize defaults", description = "Ensures admin user exists with SUPER_ADMIN role.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Defaults initialized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = "{\n  \"status\": \"error\",\n  \"code\": \"INTERNAL_ERROR\",\n  \"message\": \"Initialization failure\",\n  \"timestamp\": \"2025-11-20T10:00:00Z\",\n  \"path\": \"/api/admin/system/init-defaults\"\n}")))
    })
    public ResponseEntity<ApiResponse<Void>> initDefaults() {
        return ResponseEntity.ok(service.initDefaults());
    }

    @PostMapping("/seed-test-data")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Insert sample test data", description = "Not yet implemented against the current schema — currently a no-op that returns success without inserting data. Blocked outside dev/test profiles.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Sample data inserted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(value = "{\n  \"status\": \"error\",\n  \"code\": \"INTERNAL_ERROR\",\n  \"message\": \"Seeding failure\",\n  \"timestamp\": \"2025-11-20T10:00:00Z\",\n  \"path\": \"/api/admin/system/seed-test-data\"\n}")))
    })
    public ResponseEntity<ApiResponse<Void>> seedTestData() {
        return ResponseEntity.ok(service.seedSampleData());
    }
}
