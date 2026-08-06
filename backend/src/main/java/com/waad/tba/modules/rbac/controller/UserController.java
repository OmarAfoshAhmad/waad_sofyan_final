package com.waad.tba.modules.rbac.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import com.waad.tba.common.excel.dto.ExcelImportResult;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.rbac.dto.UserCreateDto;
import com.waad.tba.modules.rbac.dto.UserResponseDto;
import com.waad.tba.modules.rbac.dto.UserUpdateDto;
import com.waad.tba.modules.rbac.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import com.waad.tba.common.validation.PasswordPolicy;
import lombok.RequiredArgsConstructor;

/**
 * User Management Controller
 * 
 * RBAC HARDENING (2026-01-13):
 * - User management requires SUPER_ADMIN
 * - Role hierarchy enforced in service layer
 * - SUPER_ADMIN protection on delete/update operations
 * 
 * @version 2.0 - RBAC Hardening
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "RBAC - Users", description = "APIs for managing users and their roles/permissions")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class UserController {

        private static final int DEFAULT_PAGE_SIZE = 10;
        private static final int MAX_PAGE_SIZE = 100;
        private static final int LEGACY_LIST_SIZE_LIMIT = 500;

        private final UserService userService;
        private final com.waad.tba.modules.rbac.service.ProviderUserExcelImportService providerUserExcelImportService;

        @GetMapping
        @Operation(summary = "List all users", description = "Returns all users.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Users retrieved successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not Found", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal Server Error", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class)))
        })
        public ResponseEntity<ApiResponse<List<UserResponseDto>>> getAllUsers() {
                List<UserResponseDto> users = userService.findAllPaginated(PageRequest.of(0, LEGACY_LIST_SIZE_LIMIT)).getContent();
                return ResponseEntity.ok(ApiResponse.success(users));
        }

        @GetMapping("/{id:\\d+}")
        @Operation(summary = "Get user by ID", description = "Returns a user by ID.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User retrieved successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized request", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class)))
        })
        public ResponseEntity<ApiResponse<UserResponseDto>> getUserById(
                        @Parameter(name = "id", description = "User ID", required = true) @PathVariable("id") Long id) {
                UserResponseDto user = userService.findById(id);
                return ResponseEntity.ok(ApiResponse.success(user));
        }

        @PostMapping
        @Operation(summary = "Create user", description = "Creates a new user. Only SUPER_ADMIN can manage users.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "User created successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request payload", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized request", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class)))
        })
        public ResponseEntity<ApiResponse<UserResponseDto>> createUser(
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "User creation payload") @Valid @RequestBody UserCreateDto dto) {
                UserResponseDto createdUser = userService.create(dto);
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success("User created successfully", createdUser));
        }

        @PutMapping("/{id:\\d+}")
        @Operation(summary = "Update user", description = "Updates an existing user by ID. Role hierarchy enforced.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User updated successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request payload", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized request", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class)))
        })
        public ResponseEntity<ApiResponse<UserResponseDto>> updateUser(
                        @Parameter(name = "id", description = "User ID", required = true) @PathVariable("id") Long id,
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "User update payload") @Valid @RequestBody UserUpdateDto dto) {
                UserResponseDto updatedUser = userService.update(id, dto);
                return ResponseEntity.ok(ApiResponse.success("User updated successfully", updatedUser));
        }

        @DeleteMapping("/{id:\\d+}")
        @Operation(summary = "Delete user", description = "Deletes a user by ID. SUPER_ADMIN users can only be deleted by SUPER_ADMIN.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User deleted successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized request", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class)))
        })
        public ResponseEntity<ApiResponse<Void>> deleteUser(
                        @Parameter(name = "id", description = "User ID", required = true) @PathVariable("id") Long id) {
                userService.delete(id);
                return ResponseEntity.ok(ApiResponse.success("User deleted successfully", null));
        }

        @GetMapping("/search")
        @Operation(summary = "Search users", description = "Search users by query string.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Users retrieved successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized request", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class)))
        })
        public ResponseEntity<ApiResponse<List<UserResponseDto>>> searchUsers(
                        @Parameter(name = "query", description = "Search query", required = true) @RequestParam(name = "query") String query) {
                List<UserResponseDto> users = userService.searchPaginated(query, PageRequest.of(0, MAX_PAGE_SIZE)).getContent();
                return ResponseEntity.ok(ApiResponse.success(users));
        }

        @GetMapping("/search/paginate")
        @Operation(summary = "Search users with pagination", description = "Search users by query string with a capped page size.")
        public ResponseEntity<ApiResponse<Page<UserResponseDto>>> searchUsersPaginated(
                        @Parameter(name = "query", description = "Search query") @RequestParam(name = "query", required = false) String query,
                        @Parameter(name = "role", description = "Role filter") @RequestParam(name = "role", required = false) String role,
                        @Parameter(name = "active", description = "Active status filter") @RequestParam(name = "active", required = false) Boolean active,
                        @Parameter(name = "providerLink", description = "Provider link filter: LINKED or UNLINKED") @RequestParam(name = "providerLink", required = false) String providerLink,
                        @Parameter(name = "page", description = "Page number (0-based)") @RequestParam(name = "page", defaultValue = "0") int page,
                        @Parameter(name = "size", description = "Page size") @RequestParam(name = "size", defaultValue = "10") int size) {
                Page<UserResponseDto> users = userService.searchPaginated(query, role, active, providerLink, safePageRequest(page, size));
                return ResponseEntity.ok(ApiResponse.success(users));
        }

        @GetMapping("/paginate")
        @Operation(summary = "Paginate users", description = "Returns a page of users.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Users page retrieved successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized request", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class)))
        })
        public ResponseEntity<ApiResponse<Page<UserResponseDto>>> getUsersPaginated(
                        @Parameter(name = "page", description = "Page number (0-based)") @RequestParam(name = "page", defaultValue = "0") int page,
                        @Parameter(name = "size", description = "Page size") @RequestParam(name = "size", defaultValue = "10") int size) {
                Page<UserResponseDto> users = userService.findAllPaginated(safePageRequest(page, size));
                return ResponseEntity.ok(ApiResponse.success(users));
        }

        @PatchMapping("/{id:\\d+}/toggle-status")
        @Operation(summary = "Toggle user status", description = "Activates or deactivates a user. SUPER_ADMIN users cannot be deactivated.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User status toggled successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Cannot deactivate SUPER_ADMIN", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized request", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class)))
        })
        public ResponseEntity<ApiResponse<UserResponseDto>> toggleUserStatus(
                        @Parameter(name = "id", description = "User ID", required = true) @PathVariable("id") Long id) {
                UserResponseDto user = userService.toggleStatus(id);
                String message = Boolean.TRUE.equals(user.getActive()) ? "تم تفعيل المستخدم بنجاح" : "تم تعطيل المستخدم بنجاح";
                return ResponseEntity.ok(ApiResponse.success(message, user));
        }

        @PutMapping("/{id:\\d+}/reset-password")
        @Operation(summary = "Reset user password", description = "Resets a user password and revokes all active sessions.")
        public ResponseEntity<ApiResponse<Void>> resetPassword(
                        @PathVariable("id") Long id,
                        @Valid @RequestBody AdminPasswordResetRequest payload) {
                userService.resetPassword(id, payload.newPassword());
                return ResponseEntity.ok(ApiResponse.success("Password reset successfully", null));
        }

        public record AdminPasswordResetRequest(
                        @NotBlank(message = "New password is required")
                        @PasswordPolicy
                        String newPassword) {
        }

        @GetMapping("/unassigned-providers")
        @Operation(summary = "Get unassigned provider users", description = "Returns users not assigned to any provider (providerId is null).")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Unassigned users retrieved successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized request", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class)))
        })
        public ResponseEntity<ApiResponse<List<UserResponseDto>>> getUnassignedProviders() {
                List<UserResponseDto> users = userService.findUnassignedProviders();
                return ResponseEntity.ok(ApiResponse.success(users));
        }

        @GetMapping("/provider/{providerId}")
        @Operation(summary = "Get users by provider", description = "Returns users assigned to a specific provider.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Users retrieved successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized request", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class)))
        })
        public ResponseEntity<ApiResponse<List<UserResponseDto>>> getUsersByProvider(
                        @Parameter(name = "providerId", description = "Provider ID", required = true) @PathVariable("providerId") Long providerId) {
                List<UserResponseDto> users = userService.findByProviderId(providerId);
                return ResponseEntity.ok(ApiResponse.success(users));
        }

        @GetMapping("/import/providers/template")
        @Operation(summary = "Download provider users import template", description = "Downloads an Excel template for importing provider users.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Template generated successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized request", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class)))
        })
        public ResponseEntity<byte[]> downloadProviderUsersTemplate() throws IOException {
                byte[] excelData = providerUserExcelImportService.generateTemplate();
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                headers.setContentDispositionFormData("attachment", "Provider_Users_Import_Template.xlsx");
                headers.setContentLength(excelData.length);
                return ResponseEntity.ok().headers(headers).body(excelData);
        }

        @PostMapping(value = "/import/providers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @Operation(summary = "Import provider users from Excel", description = "Imports provider users from an Excel file.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Import completed successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request payload", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized request", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class)))
        })
        public ResponseEntity<ApiResponse<ExcelImportResult>> importProviderUsers(
                        @Parameter(name = "file", description = "Excel file containing provider users", required = true) @RequestParam("file") MultipartFile file) {
                ExcelImportResult result = providerUserExcelImportService.importUsers(file);
                return ResponseEntity.ok(ApiResponse.success("تم استيراد المستخدمين بنجاح", result));
        }

        private PageRequest safePageRequest(int page, int size) {
                int safePage = Math.max(page, 0);
                int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
                return PageRequest.of(safePage, safeSize);
        }
}

