package com.waad.tba.modules.visit.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.common.dto.PaginationResponse;
import com.waad.tba.modules.visit.dto.VisitCreateDto;
import com.waad.tba.modules.visit.dto.VisitResponseDto;
import com.waad.tba.modules.visit.service.VisitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/visits")
@RequiredArgsConstructor
@Tag(name = "Visits", description = "APIs for managing visit records")
@PreAuthorize("isAuthenticated()")
public class VisitController {

        private final VisitService service;

        /**
         * @deprecated Use GET /api/visits?page=&size=&search= instead (paginated
         *             search)
         */
        @Deprecated
        @GetMapping("/all")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF', 'DATA_ENTRY')")
        @Operation(summary = "List all visits", description = "Returns a list of all visit records.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Visits retrieved successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not Found", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal Server Error", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class)))
        })
        public ResponseEntity<ApiResponse<List<VisitResponseDto>>> getAll() {
                List<VisitResponseDto> list = service.findAll();
                return ResponseEntity.ok(ApiResponse.success(list));
        }

        @GetMapping("/{id:\\d+}")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF', 'DATA_ENTRY')")
        @Operation(summary = "Get visit by ID", description = "Returns a visit record by its ID.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Visit retrieved successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Visit not found"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized request", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class)))
        })
        public ResponseEntity<ApiResponse<VisitResponseDto>> getById(
                        @Parameter(name = "id", description = "Visit ID", required = true) @PathVariable("id") Long id) {
                VisitResponseDto dto = service.findById(id);
                return ResponseEntity.ok(ApiResponse.success(dto));
        }

        @PostMapping
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF', 'DATA_ENTRY')")
        @Operation(summary = "Create visit", description = "Creates a new visit record. If providerId is specified, the provider must have an active contract with the member's company.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Visit created successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request payload or provider has no active contract", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized request", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class)))
        })
        public ResponseEntity<ApiResponse<VisitResponseDto>> create(
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Visit creation payload") @Valid @RequestBody VisitCreateDto dto) {
                VisitResponseDto created = service.create(dto);
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success("Visit created successfully", created));
        }

        @PutMapping("/{id:\\d+}")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF', 'DATA_ENTRY')")
        @Operation(summary = "Update visit", description = "Updates an existing visit record by ID.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Visit updated successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Visit not found"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request payload", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized request", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class)))
        })
        public ResponseEntity<ApiResponse<VisitResponseDto>> update(
                        @Parameter(name = "id", description = "Visit ID", required = true) @PathVariable("id") Long id,
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Visit update payload") @Valid @RequestBody VisitCreateDto dto) {
                VisitResponseDto updated = service.update(id, dto);
                return ResponseEntity.ok(ApiResponse.success("Visit updated successfully", updated));
        }

        @DeleteMapping("/{id:\\d+}")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF', 'DATA_ENTRY')")
        @Operation(summary = "Delete visit", description = "Deletes a visit record by ID.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Visit deleted successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Visit not found"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized request", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class)))
        })
        public ResponseEntity<ApiResponse<Void>> delete(
                        @Parameter(name = "id", description = "Visit ID", required = true) @PathVariable("id") Long id) {
                service.delete(id);
                return ResponseEntity.ok(ApiResponse.success("Visit deleted successfully", null));
        }

        /**
         * @deprecated Use GET /api/visits?page=&size=&search= instead (paginated
         *             search)
         */
        @Deprecated
        @GetMapping("/search")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF', 'DATA_ENTRY')")
        @Operation(summary = "Search visits", description = "Search visits by query string.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Visits retrieved successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized request", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.waad.tba.common.error.ApiError.class)))
        })
        public ResponseEntity<ApiResponse<List<VisitResponseDto>>> search(
                        @Parameter(name = "query", description = "Search query", required = true) @RequestParam(name = "query") String query) {
                List<VisitResponseDto> list = service.search(query);
                return ResponseEntity.ok(ApiResponse.success(list));
        }

        @GetMapping
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF', 'DATA_ENTRY')")
        @Operation(summary = "Paginate visits", description = "Returns a page of visits with pagination parameters")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Visits page retrieved successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized request"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
        })
        public ResponseEntity<ApiResponse<PaginationResponse<VisitResponseDto>>> paginate(
                        @Parameter(name = "employerId", description = "Employer ID for filtering (null = show all for admin)") @RequestParam(name = "employerId", required = false) Long employerId,
                        @Parameter(name = "page", description = "Page number (1-based)") @RequestParam(name = "page", defaultValue = "1") int page,
                        @Parameter(name = "size", description = "Page size") @RequestParam(name = "size", defaultValue = "10") int size,
                        @Parameter(name = "search", description = "Search query") @RequestParam(name = "search", required = false) String search,
                        @Parameter(name = "sortBy", description = "Sort by field") @RequestParam(name = "sortBy", defaultValue = "createdAt") String sortBy,
                        @Parameter(name = "sortDir", description = "Sort direction") @RequestParam(name = "sortDir", defaultValue = "desc") String sortDir) {
                org.springframework.data.domain.Pageable pageable = PageRequest.of(Math.max(0, page - 1), size,
                                org.springframework.data.domain.Sort.by(
                                                org.springframework.data.domain.Sort.Direction.fromString(sortDir),
                                                sortBy));
                Page<VisitResponseDto> pageResult = service.findAllPaginated(employerId, pageable, search);
                PaginationResponse<VisitResponseDto> response = PaginationResponse.<VisitResponseDto>builder()
                                .items(pageResult.getContent())
                                .total(pageResult.getTotalElements())
                                .page(page)
                                .size(size)
                                .build();
                return ResponseEntity.ok(ApiResponse.success(response));
        }

        @GetMapping("/count")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF', 'DATA_ENTRY')")
        @Operation(summary = "Count visits", description = "Returns total number of visits (filtered by employer if provided)")
        public ResponseEntity<ApiResponse<Long>> count(
                        @Parameter(name = "employerId", description = "Employer ID for filtering") @RequestParam(name = "employerId", required = false) Long employerId) {
                long total = service.count(employerId);
                return ResponseEntity.ok(ApiResponse.success(total));
        }
}

