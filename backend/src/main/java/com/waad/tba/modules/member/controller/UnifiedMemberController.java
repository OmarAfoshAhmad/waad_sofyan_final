package com.waad.tba.modules.member.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.common.dto.PaginationResponse;
import com.waad.tba.modules.member.dto.DependentMemberDto;
import com.waad.tba.modules.member.dto.FamilyEligibilityResponseDto;
import com.waad.tba.modules.member.dto.MemberCreateDto;
import com.waad.tba.modules.member.dto.MemberFinancialSummaryDto;
import com.waad.tba.modules.member.dto.MemberUpdateDto;
import com.waad.tba.modules.member.dto.MemberViewDto;
import com.waad.tba.modules.member.service.MemberFinancialSummaryService;
import com.waad.tba.modules.member.service.UnifiedMemberService;
import com.waad.tba.modules.member.service.MemberExcelExportService;
import com.waad.tba.common.file.FileStorageService;
import com.waad.tba.common.file.FileUploadResult;
import com.waad.tba.services.pdf.HtmlToPdfService;
import com.waad.tba.services.pdf.PdfTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UnifiedMemberController - Unified REST API for managing Principal Members and
 * Dependents
 * 
 * <p>
 * <b>Architectural Design:</b>
 * </p>
 * <ul>
 * <li><b>Unified Model:</b> Single Member entity with self-referencing
 * parent-child relationship</li>
 * <li><b>Principal Members:</b> Independent members (parent_id = NULL) with
 * unique Barcode and Card Number</li>
 * <li><b>Dependent Members:</b> Children of Principals (parent_id references
 * Principal) without Barcode</li>
 * <li><b>Barcode Format:</b> WAHA-YYYY-NNNNNN (Principal only, e.g.,
 * WAHA-2026-000001)</li>
 * <li><b>Card Number Format:</b> Principal (NNNNNN), Dependent (NNNNNN-NN
 * suffix)</li>
 * <li><b>Family Eligibility:</b> Scan Principal's Barcode to retrieve entire
 * family for selection</li>
 * <li><b>CASCADE Deletion:</b> Deleting Principal automatically deletes all
 * Dependents</li>
 * </ul>
 * 
 * <p>
 * <b>Business Rules:</b>
 * </p>
 * <ul>
 * <li>Only Principal members can have Barcode (auto-generated)</li>
 * <li>Dependents inherit family association through parent_id</li>
 * <li>Card Numbers: Principal base (e.g., 000123), Dependent suffix (e.g.,
 * 000123-01, 000123-02)</li>
 * <li>Relationship enum required for Dependents (SPOUSE, CHILD, PARENT,
 * etc.)</li>
 * <li>Single-level hierarchy: Dependents cannot have their own Dependents
 * (depth = 1)</li>
 * <li>Barcode is immutable once assigned (cannot be changed)</li>
 * <li>Family eligibility check returns Principal + all Dependents for
 * selection</li>
 * </ul>
 * 
 * <p>
 * <b>Replaced Legacy Design:</b>
 * </p>
 * This controller replaces the anti-pattern of separate Member and FamilyMember
 * tables/controllers.
 * FamilyMemberController is deprecated and should not be used for new
 * development.
 * 
 * @see UnifiedMemberService
 * @see MemberCreateDto
 * @see MemberViewDto
 * @see FamilyEligibilityResponseDto
 * @author TBA-WAAD Development Team
 * @version 2.0 (Unified Architecture)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/unified-members")
@RequiredArgsConstructor
@Tag(name = "Unified Members API", description = "Unified REST API for managing Principal Members and their Dependents. "
        +
        "Replaces the legacy separate Member/FamilyMember controllers. " +
        "Supports: Principal creation with inline Dependents, Dependent management, " +
        "Family eligibility checks via Barcode, CASCADE deletion, and unified CRUD operations.")
@SuppressWarnings("deprecation")
public class UnifiedMemberController {

    private final UnifiedMemberService unifiedMemberService;
    private final MemberFinancialSummaryService financialSummaryService;
    private final PdfTemplateService pdfTemplateService;
    private final HtmlToPdfService htmlToPdfService;
    private final FileStorageService fileStorageService;
    private final MemberExcelExportService excelExportService;

    // ==================== CREATE OPERATIONS ====================

    /**
     * Create a new Principal Member with optional inline Dependents
     * 
     * <p>
     * <b>Features:</b>
     * </p>
     * <ul>
     * <li>Auto-generates Barcode (WAHA-YYYY-NNNNNN format)</li>
     * <li>Auto-generates Card Number (NNNNNN format)</li>
     * <li>Supports inline creation of Dependents (0 to N dependents)</li>
     * <li>Each Dependent gets Card Number with suffix (e.g., 000123-01)</li>
     * <li>Transaction-safe: All or nothing (rollback on error)</li>
     * </ul>
     * 
     * <p>
     * <b>Request Body:</b>
     * </p>
     * 
     * <pre>
     * {
     *   "nameAr": "أحمد محمد",
     *   "nameEn": "Ahmed Mohammed",
     *   "civilId": "28012345678",  // OPTIONAL
     *   "birthDate": "1990-05-15",
     *   "gender": "MALE",
     *   "organizationId": 1,
     *   "benefitPolicyId": 10,
     *   "dependents": [
     *     {
     *       "nameAr": "فاطمة أحمد",
     *       "nameEn": "Fatima Ahmed",
     *       "civilId": "30012345679",  // OPTIONAL
     *       "birthDate": "1995-03-20",
     *       "gender": "FEMALE",
     *       "relationship": "SPOUSE"
     *     },
     *     {
     *       "nameAr": "محمد أحمد",
     *       "nameEn": "Mohammed Ahmed",
     *       "birthDate": "2015-08-10",
     *       "gender": "MALE",
     *       "relationship": "CHILD"
     *     }
     *   ]
     * }
     * </pre>
     * 
     * <p>
     * <b>Response Example:</b>
     * </p>
     * 
     * <pre>
     * {
     *   "id": 100,
     *   "type": "PRINCIPAL",
     *   "barcode": "WAHA-2026-000123",
     *   "cardNumber": "000123",
     *   "nameAr": "أحمد محمد",
     *   "nameEn": "Ahmed Mohammed",
     *   "civilId": "28012345678",
     *   "birthDate": "1990-05-15",
     *   "gender": "MALE",
     *   "status": "PENDING",
     *   "dependents": [
     *     {
     *       "id": 101,
     *       "cardNumber": "000123-01",
     *       "nameAr": "فاطمة أحمد",
     *       "relationship": "SPOUSE",
     *       "status": "PENDING"
     *     },
     *     {
     *       "id": 102,
     *       "cardNumber": "000123-02",
     *       "nameAr": "محمد أحمد",
     *       "relationship": "CHILD",
     *       "status": "PENDING"
     *     }
     *   ]
     * }
     * </pre>
     * 
     * @param dto Member creation DTO containing Principal data and optional
     *            Dependents list
     * @return ResponseEntity with created MemberViewDto (includes Principal +
     *         Dependents)
     * @throws ValidationException if DTO validation fails
     * @throws BusinessException   if business rules violated (e.g., duplicate Civil
     *                             ID if provided)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'EMPLOYER_ADMIN')")
    @Operation(summary = "Create Principal Member with inline Dependents", description = "Creates a new Principal Member with auto-generated Barcode (WAHA-YYYY-NNNNNN) and Card Number (NNNNNN). "
            +
            "Supports inline creation of 0 to N Dependents. Each Dependent receives a Card Number with suffix (e.g., 000123-01). "
            +
            "Transaction-safe: all members created atomically or rolled back on error. " +
            "Civil ID is OPTIONAL for both Principal and Dependents.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = MemberCreateDto.class), examples = @ExampleObject(name = "Principal with Dependents", value = """
                    {
                      "nameAr": "أحمد محمد",
                      "nameEn": "Ahmed Mohammed",
                      "civilId": "28012345678",
                      "birthDate": "1990-05-15",
                      "gender": "MALE",
                      "organizationId": 1,
                      "benefitPolicyId": 10,
                      "dependents": [
                        {
                          "nameAr": "فاطمة أحمد",
                          "nameEn": "Fatima Ahmed",
                          "civilId": "30012345679",
                          "birthDate": "1995-03-20",
                          "gender": "FEMALE",
                          "relationship": "SPOUSE"
                        }
                      ]
                    }
                    """))))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Principal Member and Dependents created successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error: Invalid input data or business rule violation", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden: Insufficient permissions", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse<MemberViewDto>> createMember(
            @Valid @RequestBody MemberCreateDto dto) {

        log.info("Creating new Principal Member: fullName={}", dto.getFullName());

        MemberViewDto created = unifiedMemberService.createMember(dto);

        log.info("Principal Member created successfully: id={}, barcode={}, cardNumber={}, dependents={}",
                created.getId(), created.getBarcode(), created.getCardNumber(),
                created.getDependents() != null ? created.getDependents().size() : 0);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("تم إنشاء العضو بنجاح", created));
    }

    /**
     * Add a new Dependent to an existing Principal Member
     * 
     * <p>
     * <b>Features:</b>
     * </p>
     * <ul>
     * <li>Adds Dependent to specified Principal</li>
     * <li>Auto-generates Card Number with next available suffix</li>
     * <li>Validates Principal exists and is of type PRINCIPAL</li>
     * <li>Enforces single-level hierarchy (Dependents cannot be parents)</li>
     * </ul>
     * 
     * <p>
     * <b>Request Body Example:</b>
     * </p>
     * 
     * <pre>
     * {
     *   "nameAr": "سارة أحمد",
     *   "nameEn": "Sarah Ahmed",
     *   "birthDate": "2018-06-12",
     *   "gender": "FEMALE",
     *   "relationship": "CHILD"
     * }
     * </pre>
     * 
     * @param principalId ID of the Principal Member
     * @param dto         Dependent member creation DTO
     * @return ResponseEntity with created Dependent view
     * @throws NotFoundException if Principal not found
     * @throws BusinessException if parent is not a Principal or business rules
     *                           violated
     */
    @PostMapping("/{principalId}/dependents")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'EMPLOYER_ADMIN')")
    @Operation(summary = "Add Dependent to existing Principal", description = "Adds a new Dependent member to an existing Principal. "
            +
            "Auto-generates Card Number with suffix based on existing Dependents count (e.g., 000123-03). " +
            "Validates that parent is a Principal member (not another Dependent). " +
            "Enforces single-level hierarchy constraint.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = DependentMemberDto.class), examples = @ExampleObject(name = "New Dependent", value = """
                    {
                      "nameAr": "سارة أحمد",
                      "nameEn": "Sarah Ahmed",
                      "birthDate": "2018-06-12",
                      "gender": "FEMALE",
                      "relationship": "CHILD"
                    }
                    """))))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Dependent created successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MemberViewDto.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Principal member not found", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error or parent is not a Principal", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<MemberViewDto> addDependent(
            @Parameter(description = "ID of the Principal Member", required = true) @PathVariable("principalId") Long principalId,
            @Valid @RequestBody DependentMemberDto dto) {

        log.info("Adding Dependent to Principal: principalId={}, dependentName={}",
                principalId, dto.getFullName());

        MemberViewDto created = unifiedMemberService.addDependent(principalId, dto);

        log.info("Dependent added successfully: id={}, cardNumber={}, relationship={}",
                created.getId(), created.getCardNumber(), created.getRelationship());

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ==================== READ OPERATIONS ====================

    /**
     * Get Member by ID with optional Dependents inclusion
     * 
     * <p>
     * <b>Behavior:</b>
     * </p>
     * <ul>
     * <li>If ID is Principal: Returns Principal data with list of Dependents</li>
     * <li>If ID is Dependent: Returns Dependent data (no nested dependents)</li>
     * <li>Includes full member details and status information</li>
     * </ul>
     * 
     * @param id Member ID (can be Principal or Dependent)
     * @return ResponseEntity with MemberViewDto
     * @throws NotFoundException if Member not found
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'EMPLOYER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(summary = "Get Member by ID", description = "Retrieves a Member by ID. If the Member is a Principal, returns Principal data with list of Dependents. "
            +
            "If the Member is a Dependent, returns only the Dependent's data without nested children. " +
            "Includes complete member details: personal info, status, card/barcode, eligibility, etc.", parameters = {
                    @Parameter(name = "id", description = "Member ID (Principal or Dependent)", required = true)
            })
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Member retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MemberViewDto.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Member not found", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<MemberViewDto> getMember(
            @PathVariable("id") Long id) {

        log.info("Retrieving Member: id={}", id);

        MemberViewDto member = unifiedMemberService.getMemberWithDependents(id);

        log.info("Member retrieved: id={}, type={}, cardNumber={}",
                member.getId(), member.getType(), member.getCardNumber());

        return ResponseEntity.ok(member);
    }

    @GetMapping("/count")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'EMPLOYER_ADMIN')")
    @Operation(summary = "Count members", description = "Returns the count of members matching the criteria.")
    public ResponseEntity<Long> countMembers(
            @RequestParam(name = "employerId", required = false) Long employerId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "type", required = false) String type) {
        return ResponseEntity.ok(unifiedMemberService.countMembers(employerId, status, type));
    }

    /**
     * Get all Members with pagination and optional filters
     * 
     * <p>
     * <b>Query Parameters:</b>
     * </p>
     * <ul>
     * <li>page: Page number (0-based, default 0)</li>
     * <li>size: Page size (default 20)</li>
     * <li>sort: Sort field (default: id)</li>
     * <li>direction: Sort direction (ASC/DESC, default DESC)</li>
     * <li>employerId: Filter by Employer (optional)</li>
     * <li>status: Filter by Member Status (optional)</li>
     * <li>type: Filter by Member Type (PRINCIPAL/DEPENDENT, optional)</li>
     * </ul>
     * 
     * @param page       Page number (0-based)
     * @param size       Page size
     * @param sort       Sort field
     * @param direction  Sort direction
     * @param employerId Employer filter
     * @param status     Status filter
     * @param type       Member type filter
     * @return ResponseEntity with paginated Member list
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'EMPLOYER_ADMIN', 'MEDICAL_REVIEWER')")
    @Operation(summary = "Get all Members with pagination", description = "Retrieves paginated list of all Members (Principals and Dependents). "
            +
            "Supports filtering by Organization, Status, and Member Type. " +
            "Supports sorting by various fields. " +
            "Each Principal includes its Dependents count but not full Dependent details (use GET /{id} for full family).", parameters = {
                    @Parameter(name = "page", description = "Page number (0-based)", example = "0"),
                    @Parameter(name = "size", description = "Page size", example = "20"),
                    @Parameter(name = "sort", description = "Sort field", example = "id"),
                    @Parameter(name = "direction", description = "Sort direction (ASC/DESC)", example = "DESC"),
                    @Parameter(name = "employerId", description = "Filter by Employer ID"),
                    @Parameter(name = "status", description = "Filter by Member Status"),
                    @Parameter(name = "type", description = "Filter by Member Type (PRINCIPAL/DEPENDENT)")
            })
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Members retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaginationResponse.class)))
    })
    public ResponseEntity<Page<MemberViewDto>> getAllMembers(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sort", defaultValue = "id") String sort,
            @RequestParam(name = "direction", defaultValue = "DESC") String direction,
            @RequestParam(name = "employerId", required = false) Long employerId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "type", required = false) String type) {

        log.info("Retrieving all Members: page={}, size={}, employerId={}, status={}, type={}",
                page, size, employerId, status, type);

        Sort.Direction sortDirection = Sort.Direction.fromString(direction != null ? direction : "DESC");
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));

        Page<MemberViewDto> members = unifiedMemberService.getAllMembers(
                pageable, employerId, status, type);

        log.info("Members retrieved: totalElements={}, totalPages={}",
                members.getTotalElements(), members.getTotalPages());

        return ResponseEntity.ok(members);
    }

    /**
     * Advanced search for Members
     * 
     * <p>
     * <b>Search Criteria:</b>
     * </p>
     * <ul>
     * <li>nameAr/nameEn: Partial name match (case-insensitive)</li>
     * <li>civilId: Exact or partial Civil ID match</li>
     * <li>barcode: Exact or partial Barcode match</li>
     * <li>cardNumber: Exact or partial Card Number match</li>
     * <li>employerId: Employer filter</li>
     * <li>benefitPolicyId: Benefit Policy filter</li>
     * <li>status: Status filter</li>
     * <li>type: Member type filter</li>
     * </ul>
     * 
     * @param nameAr          Arabic name filter
     * @param nameEn          English name filter
     * @param civilId         Civil ID filter
     * @param barcode         Barcode filter
     * @param cardNumber      Card Number filter
     * @param employerId      Employer filter
     * @param benefitPolicyId Benefit Policy filter
     * @param status          Status filter
     * @param type            Member type filter
     * @param page            Page number
     * @param size            Page size
     * @return ResponseEntity with search results
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'EMPLOYER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(summary = "Advanced Member search", description = "Searches Members using multiple criteria. Supports partial matching for names, Civil ID, Barcode, and Card Number. "
            +
            "Combines filters with AND logic. Returns paginated results. " +
            "Useful for finding specific members or filtering by complex criteria.", parameters = {
                    @Parameter(name = "fullName", description = "Full name (searches both Arabic and English names)"),
                    @Parameter(name = "nameAr", description = "Arabic name (partial match)"),
                    @Parameter(name = "nameEn", description = "English name (partial match)"),
                    @Parameter(name = "civilId", description = "Civil ID (partial match)"),
                    @Parameter(name = "barcode", description = "Barcode (partial match)"),
                    @Parameter(name = "cardNumber", description = "Card Number (partial match)"),
                    @Parameter(name = "employerId", description = "Employer ID"),
                    @Parameter(name = "benefitPolicyId", description = "Benefit Policy ID"),
                    @Parameter(name = "status", description = "Member Status"),
                    @Parameter(name = "type", description = "Member Type (PRINCIPAL/DEPENDENT)"),
                    @Parameter(name = "page", description = "Page number", example = "0"),
                    @Parameter(name = "size", description = "Page size", example = "20")
            })
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Search completed successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaginationResponse.class)))
    })
    public ResponseEntity<Page<MemberViewDto>> searchMembers(
            @RequestParam(name = "fullName", required = false) String fullName,
            @RequestParam(name = "nameAr", required = false) String nameAr,
            @RequestParam(name = "nameEn", required = false) String nameEn,
            @RequestParam(name = "civilId", required = false) String civilId,
            @RequestParam(name = "barcode", required = false) String barcode,
            @RequestParam(name = "cardNumber", required = false) String cardNumber,
            @RequestParam(name = "employerId", required = false) Long employerId,
            @RequestParam(name = "benefitPolicyId", required = false) Long benefitPolicyId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "deleted", required = false, defaultValue = "false") boolean deleted,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {

        log.info("Searching Members: fullName={}, nameAr={}, civilId={}, barcode={}, cardNumber={}, deleted={}",
                fullName, nameAr, civilId, barcode, cardNumber, deleted);

        // If fullName is provided, use it for both nameAr and nameEn
        String searchNameAr = (fullName != null && !fullName.trim().isEmpty()) ? fullName : nameAr;
        String searchNameEn = (fullName != null && !fullName.trim().isEmpty()) ? fullName : nameEn;

        Pageable pageable = PageRequest.of(page, size);

        Page<MemberViewDto> results = unifiedMemberService.searchMembers(
                searchNameAr, searchNameEn, civilId, barcode, cardNumber,
                employerId, benefitPolicyId, status, type, deleted, pageable);

        log.info("Search completed: found {} results", results.getTotalElements());

        return ResponseEntity.ok(results);
    }

    /**
     * Check Family Eligibility using Principal's Barcode
     * 
     * <p>
     * <b>Use Case:</b>
     * </p>
     * Provider scans Principal's Barcode at point of service. System returns entire
     * family
     * (Principal + all Dependents) for the provider to select which member is
     * receiving service.
     * 
     * <p>
     * <b>Response Example:</b>
     * </p>
     * 
     * <pre>
     * {
     *   "principal": {
     *     "id": 100,
     *     "cardNumber": "000123",
     *     "nameAr": "أحمد محمد",
     *     "status": "ACTIVE",
     *     "isEligible": true
     *   },
     *   "dependents": [
     *     {
     *       "id": 101,
     *       "cardNumber": "000123-01",
     *       "nameAr": "فاطمة أحمد",
     *       "relationship": "SPOUSE",
     *       "status": "ACTIVE",
     *       "isEligible": true
     *     },
     *     {
     *       "id": 102,
     *       "cardNumber": "000123-02",
     *       "nameAr": "محمد أحمد",
     *       "relationship": "CHILD",
     *       "status": "ACTIVE",
     *       "isEligible": true
     *     }
     *   ],
     *   "totalMembers": 3,
     *   "eligibleMembers": 3
     * }
     * </pre>
     * 
     * @param barcode Principal's Barcode (WAHA-YYYY-NNNNNN format)
     * @return ResponseEntity with FamilyEligibilityResponseDto containing Principal
     *         and Dependents
     * @throws NotFoundException if Barcode not found
     * @throws BusinessException if Barcode belongs to Dependent (invalid)
     */
    @GetMapping("/eligibility/{barcode}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'EMPLOYER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(summary = "Check Family Eligibility by Barcode", description = "Scans Principal's Barcode and returns entire family (Principal + Dependents) for member selection at point of service. "
            +
            "This is the PRIMARY eligibility check method in the unified architecture. " +
            "Only Principal members have Barcodes (Dependents do not). " +
            "Returns eligibility status for each family member based on 7-condition eligibility rules. " +
            "Used by Providers to verify which family members can receive services.", parameters = {
                    @Parameter(name = "barcode", description = "Principal's Barcode in WAHA-YYYY-NNNNNN format (e.g., WAHA-2026-000123)", required = true, example = "WAHA-2026-000123")
            })
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Family eligibility retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = FamilyEligibilityResponseDto.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Barcode not found or invalid", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Barcode format invalid or belongs to Dependent (Dependents do not have Barcodes)", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<FamilyEligibilityResponseDto> checkEligibility(
            @PathVariable("barcode") String barcode) {

        log.info("Checking family eligibility: barcode={}", barcode);

        FamilyEligibilityResponseDto response = unifiedMemberService.checkEligibility(barcode);

        log.info("Eligibility check completed: barcode={}, totalMembers={}, eligibleMembers={}",
                barcode, response.getTotalFamilyMembers(), response.getEligibleMembersCount());

        return ResponseEntity.ok(response);
    }

    /**
     * Generate PDF Report for Beneficiaries (Insured Members)
     * 
     * <p>
     * <b>Features:</b>
     * </p>
     * <ul>
     * <li>Generates PDF list of members based on filters</li>
     * <li>Uses Thymeleaf template 'pdf/beneficiaries-report'</li>
     * <li>Supports filtering by Organization, Status, Member Type, etc.</li>
     * </ul>
     * 
     * @deprecated PDF export disabled. Excel is the official reporting format.
     *             Use /excel/report endpoint instead.
     */
    // @GetMapping("/pdf/report") // DISABLED - Use Excel export instead
    // @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'EMPLOYER_ADMIN')")
    @Deprecated(since = "2026-01", forRemoval = false)
    @Operation(summary = "[DISABLED] Download Beneficiaries PDF Report", description = "PDF export disabled. Use Excel export instead. Excel is the official reporting format.")
    public ResponseEntity<byte[]> downloadBeneficiariesPdf(
            @RequestParam(name = "nameAr", required = false) String nameAr,
            @RequestParam(name = "nameEn", required = false) String nameEn,
            @RequestParam(name = "civilId", required = false) String civilId,
            @RequestParam(name = "barcode", required = false) String barcode,
            @RequestParam(name = "cardNumber", required = false) String cardNumber,
            @RequestParam(name = "organizationId", required = false) Long organizationId,
            @RequestParam(name = "benefitPolicyId", required = false) Long benefitPolicyId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "type", required = false) String type) throws IOException {

        log.info("Generating PDF report for members: orgId={}, status={}, type={}", organizationId, status, type);

        // 1. Fetch Data (Reuse search logic but get larger page or all)
        // Note: For reporting, we might want a limit, e.g., 1000 records
        Pageable pageable = PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "id"));

        Page<MemberViewDto> membersPage = unifiedMemberService.searchMembers(
                nameAr, nameEn, civilId, barcode, cardNumber,
                organizationId, benefitPolicyId, status, type, false, pageable);

        List<MemberViewDto> members = membersPage.getContent();

        // 2. Prepare Template Data
        Map<String, Object> data = new HashMap<>();
        String reportDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        data.put("reportDate", reportDate);
        data.put("members", members);

        // Calculate Summary
        data.put("totalMembers", membersPage.getTotalElements());
        data.put("activeMembers",
                members.stream().filter(m -> (m.getStatus() != null && "ACTIVE".equals(m.getStatus().name())) ||
                        (m.getCardStatus() != null && "ACTIVE".equals(m.getCardStatus().name()))).count());

        long familiesCount = members.stream().filter(m -> m.getType() != null && "PRINCIPAL".equals(m.getType()))
                .count();
        data.put("familiesCount", familiesCount);

        // Describe filters
        StringBuilder filterDesc = new StringBuilder();
        if (organizationId != null)
            filterDesc.append("Company ID: ").append(organizationId).append(", ");
        if (status != null)
            filterDesc.append("Status: ").append(status).append(", ");
        if (type != null)
            filterDesc.append("Type: ").append(type).append(", ");
        if (filterDesc.length() > 0)
            data.put("filterDescription", filterDesc.toString());
        else
            data.put("filterDescription", "الكل");

        // 3. Process Template
        String html = pdfTemplateService.processTemplate("pdf/beneficiaries-report", data);

        // 4. Convert to PDF
        byte[] pdfBytes = htmlToPdfService.convertHtmlToPdf(html);

        // 5. Response
        String filename = "beneficiaries-report-" + reportDate + ".pdf";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(pdfBytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    /**
     * Generate PDF Membership Card/Details for Single Member
     * 
     * @deprecated PDF export disabled. Excel is the official reporting format.
     */
    // @GetMapping("/{id}/pdf") // DISABLED - PDF export not active
    // @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'EMPLOYER_ADMIN',
    // 'PROVIDER_STAFF')")
    @Deprecated(since = "2026-01", forRemoval = false)
    public ResponseEntity<byte[]> downloadMemberPdf(@PathVariable("id") Long id) throws IOException {
        // Logic to print single member details... reusing beneficiaries report for now
        // for single item,
        // OR we could make a specific 'member-card.html' later.
        // For now, let's just use the list report filtered by this ID or similar,
        // BUT user asked for "Preview PDF" button for single member in previous turn.
        // Let's implement a simple single page report.

        MemberViewDto member = unifiedMemberService.getMemberWithDependents(id);

        Map<String, Object> data = new HashMap<>();
        data.put("reportDate", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        data.put("members", List.of(member)); // Wrap single member in list
        data.put("totalMembers", 1);
        boolean isActive = (member.getStatus() != null && "ACTIVE".equals(member.getStatus().name())) ||
                (member.getCardStatus() != null && "ACTIVE".equals(member.getCardStatus().name()));
        data.put("activeMembers", isActive ? 1 : 0);

        boolean isPrincipal = member.getType() != null && "PRINCIPAL".equals(member.getType());
        data.put("familiesCount", isPrincipal ? 1 : 0);
        data.put("filterDescription", "تفاصيل منتفع فردي: " + member.getFullName());

        String html = pdfTemplateService.processTemplate("pdf/beneficiaries-report", data);
        byte[] pdfBytes = htmlToPdfService.convertHtmlToPdf(html);

        String filename = "member-" + member.getCardNumber() + ".pdf";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline", filename); // Inline for preview
        headers.setContentLength(pdfBytes.length);

        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }

    // ==================== UPDATE OPERATIONS ====================

    /**
     * Update Member (Principal or Dependent)
     * 
     * <p>
     * <b>Updatable Fields:</b>
     * </p>
     * <ul>
     * <li>Personal information (names, birth date, gender, Civil ID)</li>
     * <li>Contact information (phone, email, address)</li>
     * <li>Organization/Benefit Policy (for Principals)</li>
     * <li>Relationship (for Dependents)</li>
     * <li>Custom attributes</li>
     * </ul>
     * 
     * <p>
     * <b>Immutable Fields:</b>
     * </p>
     * <ul>
     * <li>Barcode (cannot be changed once assigned)</li>
     * <li>Card Number (cannot be changed)</li>
     * <li>Member Type (PRINCIPAL/DEPENDENT, cannot be changed)</li>
     * <li>Parent ID (cannot change family association)</li>
     * </ul>
     * 
     * @param id  Member ID
     * @param dto Update DTO with modified fields
     * @return ResponseEntity with updated MemberViewDto
     * @throws NotFoundException   if Member not found
     * @throws ValidationException if validation fails
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'EMPLOYER_ADMIN')")
    @Operation(summary = "Update Member data", description = "Updates an existing Member (Principal or Dependent). " +
            "Supports updating personal information, contact details, and custom attributes. " +
            "IMMUTABLE FIELDS: Barcode, Card Number, Member Type, Parent ID (cannot be changed). " +
            "Validation enforced for all business rules. " +
            "For Dependents: Can update Relationship. " +
            "For Principals: Can update Organization/Benefit Policy.", parameters = {
                    @Parameter(name = "id", description = "Member ID to update", required = true)
            }, requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = MemberUpdateDto.class))))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Member updated successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MemberViewDto.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Member not found", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error or attempt to modify immutable field", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<MemberViewDto> updateMember(
            @PathVariable("id") Long id,
            @Valid @RequestBody MemberUpdateDto dto) {

        log.info("Updating Member: id={}", id);

        MemberViewDto updated = unifiedMemberService.updateMember(id, dto);

        log.info("Member updated successfully: id={}, cardNumber={}",
                updated.getId(), updated.getCardNumber());

        return ResponseEntity.ok(updated);
    }

    /**
     * Activate or deactivate a member.
     *
     * @param id     Member ID
     * @param active true = activate, false = deactivate
     * @return Updated member view DTO
     */
    @PatchMapping("/{id}/active")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'EMPLOYER_ADMIN')")
    @Operation(summary = "Activate or deactivate a member", description = "Toggles the active flag of a member without changing any other data. "
            +
            "Inactive members are still stored but excluded from eligibility checks.", parameters = {
                    @Parameter(name = "id", description = "Member ID", required = true)
            })
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Member active status updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Member not found")
    })
    public ResponseEntity<ApiResponse<MemberViewDto>> setActive(
            @PathVariable("id") Long id,
            @RequestParam(name = "active") boolean active) {

        log.info("Setting active={} for member ID={}", active, id);
        MemberViewDto updated = unifiedMemberService.toggleActive(id, active);
        String message = active ? "تم تفعيل العضو بنجاح" : "تم إيقاف العضو بنجاح";
        return ResponseEntity.ok(ApiResponse.success(message, updated));
    }

    // ==================== DELETE OPERATIONS ====================

    /**
     * Delete Member (CASCADE for Principals)
     * 
     * <p>
     * <b>Deletion Behavior:</b>
     * </p>
     * <ul>
     * <li><b>Principal Deletion:</b> CASCADE deletes all Dependents (entire family
     * removed)</li>
     * <li><b>Dependent Deletion:</b> Removes only the specific Dependent (Principal
     * and siblings remain)</li>
     * <li><b>Soft Delete:</b> Member is marked as TERMINATED (not physically
     * deleted from database)</li>
     * <li><b>Audit Trail:</b> Deletion timestamp and user recorded for
     * compliance</li>
     * </ul>
     * 
     * <p>
     * <b>Warning:</b>
     * </p>
     * Deleting a Principal will permanently terminate the entire family. This
     * action cannot be undone.
     * Use with caution. Consider SUSPENDING members instead if temporary
     * deactivation is needed.
     * 
     * @param id Member ID to delete
     * @return ResponseEntity with 204 No Content on success
     * @throws NotFoundException if Member not found
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'EMPLOYER_ADMIN')")
    @Operation(summary = "Delete Member (CASCADE for Principals)", description = "Deletes a Member. BEHAVIOR VARIES BY TYPE: "
            +
            "PRINCIPAL deletion: CASCADE deletes ALL Dependents (entire family removed). " +
            "DEPENDENT deletion: Removes only that Dependent (Principal and other Dependents remain). " +
            "Deletion is SOFT DELETE (member marked TERMINATED, not physically removed). " +
            "Audit trail maintained for compliance. " +
            "WARNING: Principal deletion is irreversible and affects entire family. " +
            "Consider SUSPENDING members for temporary deactivation instead.", parameters = {
                    @Parameter(name = "id", description = "Member ID to delete", required = true)
            })
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Member deleted successfully (CASCADE applied if Principal)", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Member not found", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden: Insufficient permissions (requires ADMIN or EMPLOYER role)", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse<Void>> deleteMember(
            @PathVariable("id") Long id) {

        log.info("Deleting Member: id={}", id);

        try {
            unifiedMemberService.deleteMember(id);
            log.info("Member deleted successfully: id={}", id);
            return ResponseEntity.ok(ApiResponse.success("تم حذف المستفيد بنجاح", null));
        } catch (IllegalStateException e) {
            log.warn("Delete blocked for member id={}: {}", id, e.getMessage());
            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== UTILITY OPERATIONS ====================

    /**
     * Get all Dependents of a specific Principal
     * 
     * @param principalId Principal Member ID
     * @return ResponseEntity with list of Dependent views
     * @throws NotFoundException if Principal not found
     * @throws BusinessException if member is not a Principal
     */
    @GetMapping("/{principalId}/dependents")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'EMPLOYER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(summary = "Get all Dependents of a Principal", description = "Retrieves all Dependents associated with a specific Principal member. "
            +
            "Returns empty list if Principal has no Dependents. " +
            "Throws error if ID belongs to a Dependent (only Principals can have Dependents).", parameters = {
                    @Parameter(name = "principalId", description = "Principal Member ID", required = true)
            })
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Dependents retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = List.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Principal not found", content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Member is not a Principal", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<List<MemberViewDto>> getDependents(
            @PathVariable("principalId") Long principalId) {

        log.info("Retrieving Dependents for Principal: principalId={}", principalId);

        List<MemberViewDto> dependents = unifiedMemberService.getDependents(principalId);

        log.info("Dependents retrieved: principalId={}, count={}",
                principalId, dependents.size());

        return ResponseEntity.ok(dependents);
    }

    /**
     * Count total Dependents for a Principal
     * 
     * @param principalId Principal Member ID
     * @return ResponseEntity with count
     */
    @GetMapping("/{principalId}/dependents/count")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'EMPLOYER_ADMIN')")
    @Operation(summary = "Count Dependents of a Principal", description = "Returns the total count of Dependents for a specific Principal member. "
            +
            "Useful for validation and UI display without fetching full Dependent details.", parameters = {
                    @Parameter(name = "principalId", description = "Principal Member ID", required = true)
            })
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Count retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Long.class)))
    })
    public ResponseEntity<Long> countDependents(
            @PathVariable("principalId") Long principalId) {

        log.info("Counting Dependents for Principal: principalId={}", principalId);

        long count = unifiedMemberService.countDependents(principalId);

        log.info("Dependents count: principalId={}, count={}", principalId, count);

        return ResponseEntity.ok(count);
    }

    // ==================== REMAINING LIMIT (PROVIDER PORTAL) ====================

    /**
     * Get member's remaining coverage limit
     * 
     * <p>
     * <b>PROVIDER PORTAL ENDPOINT (2026-01-16)</b>
     * </p>
     * 
     * <p>
     * Simple endpoint for Provider Portal to show remaining limit during claim
     * creation.
     * </p>
     * 
     * @param memberId Member ID
     * @return Remaining limit data
     */
    @GetMapping("/{memberId}/remaining-limit")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'EMPLOYER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(summary = "Get Member Remaining Limit", description = "Returns the remaining coverage limit for a member. Used in Provider Portal during claim creation.")
    public ResponseEntity<java.util.Map<String, Object>> getRemainingLimit(
            @PathVariable("memberId") Long memberId) {

        log.info("📊 Retrieving remaining limit for member: memberId={}", memberId);

        MemberFinancialSummaryDto summary = financialSummaryService.getFinancialSummary(memberId);

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("memberId", memberId);
        result.put("memberName", summary.getFullName());
        result.put("annualLimit", summary.getAnnualLimit());
        result.put("usedAmount", summary.getTotalApproved());
        result.put("remainingLimit", summary.getRemainingCoverage());
        result.put("usagePercentage", summary.getUtilizationPercent());
        result.put("policyName", summary.getPolicyName());
        result.put("policyActive", summary.getPolicyActive());

        log.info("✅ Remaining limit retrieved: memberId={}, remaining={}",
                memberId, summary.getRemainingCoverage());

        return ResponseEntity.ok(result);
    }

    // ==================== FINANCIAL SUMMARY (PHASE 1) ====================

    /**
     * Get comprehensive financial summary for a member
     * 
     * <p>
     * <b>PHASE 1: Critical Financial Endpoint</b>
     * </p>
     * 
     * <p>
     * <b>Returns:</b>
     * </p>
     * <ul>
     * <li>Policy information (name, limits, dates)</li>
     * <li>Utilization metrics (claimed, approved, remaining)</li>
     * <li>Claim statistics (counts by status)</li>
     * <li>Financial alerts (nearing limit, expiring policy)</li>
     * </ul>
     * 
     * <p>
     * <b>Response Example:</b>
     * </p>
     * 
     * <pre>
     * {
     *   "memberId": 123,
     *   "fullName": "أحمد محمد",
     *   "policyName": "Gold Plan",
     *   "annualLimit": 50000.00,
     *   "totalClaimed": 15000.00,
     *   "totalApproved": 12000.00,
     *   "remainingCoverage": 38000.00,
     *   "utilizationPercent": 24.00,
     *   "claimsCount": 5,
     *   "approvedClaimsCount": 3,
     *   "pendingClaimsCount": 1,
     *   "rejectedClaimsCount": 1,
     *   "lastClaimDate": "2026-01-05",
     *   "warningMessage": null,
     *   "nearingLimit": false,
     *   "policyExpiringSoon": false
     * }
     * </pre>
     * 
     * @param memberId Member ID (Principal or Dependent)
     * @return Comprehensive financial summary
     */
    @GetMapping("/{memberId}/financial-summary")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'EMPLOYER_ADMIN')")
    @Operation(summary = "Get Member Financial Summary", description = "Returns comprehensive financial overview including policy info, utilization metrics, "
            +
            "claim statistics, and alerts. **PHASE 1 Critical Endpoint** for financial visibility.", parameters = {
                    @Parameter(name = "memberId", description = "Member ID (Principal or Dependent)", required = true)
            })
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Financial summary retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MemberFinancialSummaryDto.class), examples = @ExampleObject(name = "Financial Summary", value = """
                    {
                      "memberId": 123,
                      "fullName": "أحمد محمد علي",
                      "cardNumber": "000123",
                      "barcode": "WAHA-2026-000123",
                      "isDependent": false,
                      "policyId": 1,
                      "policyName": "Gold Plan",
                      "annualLimit": 50000.00,
                      "policyStartDate": "2026-01-01",
                      "policyEndDate": "2026-12-31",
                      "policyActive": true,
                      "totalClaimed": 15000.00,
                      "totalApproved": 12000.00,
                      "totalPaid": 10000.00,
                      "remainingCoverage": 38000.00,
                      "utilizationPercent": 24.00,
                      "claimsCount": 5,
                      "pendingClaimsCount": 1,
                      "approvedClaimsCount": 3,
                      "rejectedClaimsCount": 1,
                      "lastClaimDate": "2026-01-05",
                      "totalPatientCoPay": 3000.00,
                      "totalDeductibleApplied": 500.00,
                      "warningMessage": null,
                      "nearingLimit": false,
                      "policyExpiringSoon": false
                    }
                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Member not found")
    })
    public ResponseEntity<MemberFinancialSummaryDto> getFinancialSummary(
            @PathVariable("memberId") Long memberId) {

        log.info("📊 Retrieving financial summary for member: memberId={}", memberId);

        MemberFinancialSummaryDto summary = financialSummaryService.getFinancialSummary(memberId);

        log.info("✅ Financial summary retrieved: memberId={}, utilization={}%",
                memberId, summary.getUtilizationPercent());

        return ResponseEntity.ok(summary);
    }

    // ==================== PHOTO MANAGEMENT ====================

    /**
     * Upload member profile photo
     * 
     * @param id   Member ID
     * @param file Image file (JPEG, PNG)
     * @return Updated member with photo URL
     */
    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'EMPLOYER_ADMIN')")
    @Operation(summary = "Upload Member Photo", description = "Upload profile photo for a member. Accepts JPEG or PNG images.")
    public ResponseEntity<ApiResponse<MemberViewDto>> uploadPhoto(
            @PathVariable("id") Long id,
            @RequestParam("file") MultipartFile file) {

        log.info("📸 Photo upload request: memberId={}, filename={}, size={}",
                id, file.getOriginalFilename(), file.getSize());

        try {
            // Validate file
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("الملف فارغ"));
            }

            String contentType = file.getContentType();
            if (contentType == null ||
                    (!contentType.equals("image/jpeg") &&
                            !contentType.equals("image/png") &&
                            !contentType.equals("image/jpg"))) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("يجب رفع صورة بصيغة JPEG أو PNG"));
            }

            // Upload to storage
            FileUploadResult uploadResult = fileStorageService.upload(file, "members/photos");

            // Update member with photo path
            MemberViewDto updated = unifiedMemberService.updateMemberPhoto(id, uploadResult.getFilePath());

            log.info("✅ Photo uploaded successfully: memberId={}, path={}", id, uploadResult.getFilePath());

            return ResponseEntity.ok(ApiResponse.success("تم رفع الصورة بنجاح", updated));

        } catch (Exception e) {
            log.error("❌ Photo upload failed: memberId={}, error={}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("فشل رفع الصورة: " + e.getMessage()));
        }
    }

    /**
     * Get member profile photo
     * 
     * @param id Member ID
     * @return Photo binary content
     */
    @GetMapping(value = "/{id}/photo", produces = { MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE })
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'EMPLOYER_ADMIN', 'PROVIDER', 'PROVIDER_STAFF')")
    @Operation(summary = "Get Member Photo", description = "Retrieve member profile photo as image binary")
    public ResponseEntity<byte[]> getPhoto(@PathVariable("id") Long id) {
        log.debug("📸 Photo request: memberId={}", id);

        try {
            String photoPath = unifiedMemberService.getMemberPhotoPath(id);

            if (photoPath == null || photoPath.isBlank()) {
                return ResponseEntity.notFound().build();
            }

            byte[] photoData = fileStorageService.download(photoPath);

            // Determine content type from path
            String contentType = photoPath.endsWith(".png") ? MediaType.IMAGE_PNG_VALUE : MediaType.IMAGE_JPEG_VALUE;

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(photoData);

        } catch (Exception e) {
            log.error("❌ Photo retrieval failed: memberId={}, error={}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Delete member profile photo
     * 
     * @param id Member ID
     * @return Success response
     */
    @DeleteMapping("/{id}/photo")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'EMPLOYER_ADMIN')")
    @Operation(summary = "Delete Member Photo", description = "Remove profile photo from a member")
    public ResponseEntity<ApiResponse<Void>> deletePhoto(@PathVariable("id") Long id) {
        log.info("🗑️ Photo delete request: memberId={}", id);

        try {
            String photoPath = unifiedMemberService.getMemberPhotoPath(id);

            if (photoPath != null && !photoPath.isBlank()) {
                fileStorageService.delete(photoPath);
            }

            unifiedMemberService.updateMemberPhoto(id, null);

            log.info("✅ Photo deleted: memberId={}", id);

            return ResponseEntity.ok(ApiResponse.success("تم حذف الصورة بنجاح", null));

        } catch (Exception e) {
            log.error("❌ Photo deletion failed: memberId={}, error={}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("فشل حذف الصورة: " + e.getMessage()));
        }
    }

    // ==================== RESTORE & HARD DELETE ====================

    /**
     * Restore a soft-deleted member
     * 
     * @param id Member ID
     * @return Restored member
     */
    @PutMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'EMPLOYER_ADMIN')")
    @Operation(summary = "Restore Deleted Member", description = "Restore a soft-deleted member (unset deleted flag)")
    public ResponseEntity<ApiResponse<MemberViewDto>> restoreMember(@PathVariable("id") Long id) {
        log.info("♻️ Restore request: memberId={}", id);

        try {
            MemberViewDto restored = unifiedMemberService.restoreMember(id);

            log.info("✅ Member restored: memberId={}", id);

            return ResponseEntity.ok(ApiResponse.success("تم استعادة العضو بنجاح", restored));

        } catch (Exception e) {
            log.error("❌ Restore failed: memberId={}, error={}", id, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("فشل استعادة العضو: " + e.getMessage()));
        }
    }

    /**
     * Permanently delete a member (hard delete)
     * 
     * @param id Member ID
     * @return Success response
     */
    @DeleteMapping("/{id}/hard")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Hard Delete Member", description = "Permanently delete a member from the database (SUPER_ADMIN only)")
    public ResponseEntity<ApiResponse<Void>> hardDeleteMember(@PathVariable("id") Long id) {
        log.warn("⚠️ HARD DELETE request: memberId={}", id);

        try {
            unifiedMemberService.hardDeleteMember(id);

            log.info("✅ Member hard deleted: memberId={}", id);

            return ResponseEntity.ok(ApiResponse.success("تم حذف العضو نهائياً", null));

        } catch (Exception e) {
            log.error("❌ Hard delete failed: memberId={}, error={}", id, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("فشل الحذف النهائي: " + e.getMessage()));
        }
    }

    // ==================== EXCEL EXPORT ====================

    /**
     * Export members to Excel file
     * 
     * @param searchQuery     Search query (optional)
     * @param employerId      Employer ID filter (optional)
     * @param benefitPolicyId Benefit Policy ID filter (optional)
     * @param includeDeleted  Include deleted members (optional)
     * @return Excel file as byte array
     */
    @GetMapping("/export/excel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'EMPLOYER_ADMIN')")
    @Operation(summary = "Export Members to Excel", description = "Export members list to Excel file with optional filters")
    public ResponseEntity<byte[]> exportMembersToExcel(
            @RequestParam(name = "searchQuery", required = false) String searchQuery,
            @RequestParam(name = "employerId", required = false) Long employerId,
            @RequestParam(name = "benefitPolicyId", required = false) Long benefitPolicyId,
            @RequestParam(name = "includeDeleted", required = false, defaultValue = "false") Boolean includeDeleted) {

        log.info("📊 Excel export request: query={}, employer={}, policy={}, deleted={}",
                searchQuery, employerId, benefitPolicyId, includeDeleted);

        try {
            byte[] excelData = excelExportService.exportToExcel(
                    searchQuery, employerId, benefitPolicyId, includeDeleted);

            String filename = "Members_Export_" +
                    LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) +
                    ".xlsx";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(excelData.length);

            log.info("✅ Excel export completed: {} bytes", excelData.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(excelData);

        } catch (Exception e) {
            log.error("❌ Excel export failed: error={}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

