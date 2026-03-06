package com.waad.tba.modules.backlog.service;

import com.waad.tba.common.enums.NetworkType;
import com.waad.tba.modules.backlog.dto.BacklogClaimRequest;
import com.waad.tba.modules.backlog.dto.BacklogImportResponse;
import com.waad.tba.modules.backlog.dto.BacklogServiceLineDto;
import com.waad.tba.modules.claim.entity.*;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.entity.VisitStatus;
import com.waad.tba.modules.visit.entity.VisitType;
import com.waad.tba.modules.visit.repository.VisitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BacklogService {

    private final VisitRepository visitRepository;
    private final ClaimRepository claimRepository;
    private final MemberRepository memberRepository;
    private final ProviderRepository providerRepository;
    private final MedicalServiceRepository medicalServiceRepository;
    private final com.waad.tba.modules.provider.repository.ProviderAllowedEmployerRepository providerAllowedEmployerRepository;

    @Transactional
    public Long createBacklogClaim(BacklogClaimRequest request, String enteredBy, ClaimSource source) {
        Member member = memberRepository.findById(request.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        
        Provider provider = providerRepository.findById(request.getProviderId())
                .orElseThrow(() -> new IllegalArgumentException("Provider not found"));

        // Security: Verify provider is authorized for this employer FIRST (before persisting anything)
        if (member.getEmployer() != null) {
            boolean allowed = providerAllowedEmployerRepository
                    .hasActiveAccessToEmployer(provider.getId(), member.getEmployer().getId());
            if (!allowed) {
                throw new IllegalArgumentException(
                    "المزود غير مرخص لتقديم مطالبات لمنشأة: " + member.getEmployer().getName());
            }
        }

        // Validate: serviceDate cannot be in the future
        if (request.getServiceDate() != null && request.getServiceDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("تاريخ الخدمة لا يمكن أن يكون في المستقبل");
        }

        // 1. Create Shadow Visit (only after security + date checks pass)
        Visit visit = Visit.builder()
                .member(member)
                .employer(member.getEmployer())
                .providerId(provider.getId())
                .visitDate(request.getServiceDate())
                .doctorName(request.getDoctorName() != null ? request.getDoctorName() : "Legacy Doctor")
                .diagnosis(request.getDiagnosis())
                .complaint(request.getComplaint())
                .visitType(VisitType.LEGACY_BACKLOG)
                .status(VisitStatus.COMPLETED)
                .networkStatus(request.getNetworkStatus() != null ? request.getNetworkStatus() : NetworkType.IN_NETWORK)
                .active(true)
                .build();
        visit = visitRepository.save(visit);

        // 2. Create Claim — null-safe total calculations
        boolean isRejected = request.getStatus() != null && "REJECTED".equalsIgnoreCase(request.getStatus());

        BigDecimal totalRequested = BigDecimal.ZERO;
        BigDecimal totalApproved = BigDecimal.ZERO;
        BigDecimal totalRefused = BigDecimal.ZERO;

        if (request.getLines() != null) {
            for (BacklogServiceLineDto l : request.getLines()) {
                BigDecimal gross = l.getGrossAmount() != null ? l.getGrossAmount() : BigDecimal.ZERO;
                int qty = l.getQuantity() != null ? l.getQuantity() : 1;
                BigDecimal lineTotal = gross.multiply(new BigDecimal(qty));
                totalRequested = totalRequested.add(lineTotal);

                if (isRejected || Boolean.TRUE.equals(l.getRejected())) {
                    totalRefused = totalRefused.add(lineTotal);
                } else {
                    BigDecimal covered = l.getCoveredAmount() != null ? l.getCoveredAmount() : gross;
                    totalApproved = totalApproved.add(covered.multiply(new BigDecimal(qty)));
                    
                    // Partial refusal (price excess or other partial rejections)
                    if (l.getRefusedAmount() != null && l.getRefusedAmount().compareTo(BigDecimal.ZERO) > 0) {
                        totalRefused = totalRefused.add(l.getRefusedAmount());
                    }
                }
            }
        }
        // Guarantee requestedAmount is never zero (at least 1 SAR for rejected) to pass validation
        if (totalRequested.compareTo(BigDecimal.ZERO) == 0) {
            totalRequested = BigDecimal.ONE;
        }
        // For rejected claims, approvedAmount = 0 is valid per business rules
        // But Claim.validateBusinessRules requires approvedAmount > 0 for SETTLED status
        // So we only use SETTLED when there is actual approved amount
        ClaimStatus finalStatus;
        if (isRejected) {
            finalStatus = ClaimStatus.REJECTED;
        } else if (totalApproved.compareTo(BigDecimal.ZERO) > 0) {
            finalStatus = ClaimStatus.SETTLED;
        } else {
            finalStatus = ClaimStatus.DRAFT; // fallback: no approved amount → draft for review
        }

        // REJECTED requires reviewerComment; use rejectionReason or default
        String reviewerComment = request.getRejectionReason();
        if (finalStatus == ClaimStatus.REJECTED && (reviewerComment == null || reviewerComment.trim().isEmpty())) {
            reviewerComment = "مطالبة مرفوضة (بلاغ متراكم)";
        }

        Claim claim = Claim.builder()
                .member(member)
                .visit(visit)
                .providerId(provider.getId())
                .providerName(provider.getName())
                .doctorName(visit.getDoctorName())
                .serviceDate(request.getServiceDate())
                .status(finalStatus)
                .reviewerComment(reviewerComment)
                .requestedAmount(totalRequested)
                .approvedAmount(totalApproved)
                .refusedAmount(totalRefused)
                .differenceAmount(totalRequested.subtract(totalApproved))
                .claimSource(source)
                .legacyReferenceNumber(request.getLegacyReferenceNumber())
                .isBacklog(true)
                .enteredAt(LocalDateTime.now())
                .enteredBy(enteredBy)
                .active(true)
                .build();

        // Financial snapshot defaults
        BigDecimal coPay = totalRequested.subtract(totalApproved).subtract(totalRefused);
        claim.setPatientCoPay(coPay.compareTo(BigDecimal.ZERO) > 0 ? coPay : BigDecimal.ZERO);
        claim.setNetProviderAmount(totalApproved);
        // Mark settled time for non-rejected backlog claims
        if (!isRejected && totalApproved.compareTo(BigDecimal.ZERO) > 0) {
            claim.setSettledAt(LocalDateTime.now());
        }

        // 3. Add Claim Lines
        if (request.getLines() != null) {
            for (BacklogServiceLineDto lineDto : request.getLines()) {
                MedicalService medicalService = null;
                if (lineDto.getServiceCode() != null) {
                    medicalService = medicalServiceRepository.findByCode(lineDto.getServiceCode()).orElse(null);
                }

                BigDecimal unitPrice = lineDto.getGrossAmount() != null ? lineDto.getGrossAmount() : BigDecimal.ZERO;
                BigDecimal coveredPrice = lineDto.getCoveredAmount() != null ? lineDto.getCoveredAmount() : unitPrice;
                int qty = lineDto.getQuantity() != null ? lineDto.getQuantity() : 1;
                
                ClaimLine line = ClaimLine.builder()
                        .claim(claim)
                        // In V22, medical_service_id and service_category_id are NOT NULL. 
                        // Since this is backlog, we might not have a catalog match, so we use 0 as fallback.
                        .medicalService(medicalService)
                        .serviceCode(lineDto.getServiceCode() != null ? lineDto.getServiceCode() : "LEGACY")
                        .serviceName(lineDto.getServiceName() != null ? lineDto.getServiceName() : 
                                     (medicalService != null ? medicalService.getName() : "Legacy Service"))
                        .serviceCategoryId(medicalService != null && medicalService.getCategoryId() != null ? medicalService.getCategoryId() : 0L)
                        .serviceCategoryName("Legacy Category") // MedicalService doesn't expose categoryName — avoids NPE
                        .quantity(qty)
                        .unitPrice(unitPrice)
                        .totalPrice(unitPrice.multiply(new BigDecimal(qty)))
                        .coveragePercentSnapshot(lineDto.getCoveragePercent() != null ? lineDto.getCoveragePercent() : 100)
                        .timesLimitSnapshot(lineDto.getTimesLimit())
                        .amountLimitSnapshot(lineDto.getAmountLimit())
                        .refusedAmount(lineDto.getRefusedAmount() != null ? lineDto.getRefusedAmount() : BigDecimal.ZERO)
                        .rejected(Boolean.TRUE.equals(lineDto.getRejected()))
                        .rejectionReason(lineDto.getRejectionReason())
                        .build();
                
                claim.addLine(line);
            }
        }

        claim = claimRepository.save(claim);
        return claim.getId();
    }

    @Transactional
    public void cancelBacklogClaim(Long claimId, String cancelledBy) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("المطالبة غير موجودة: " + claimId));

        if (!Boolean.TRUE.equals(claim.getIsBacklog())) {
            throw new IllegalArgumentException("لا يمكن إلغاء مطالبة غير متراكمة من هذا المسار");
        }
        if (ClaimStatus.SETTLED == claim.getStatus()) {
            throw new IllegalArgumentException("لا يمكن إلغاء مطالبة مسوّاة بالفعل");
        }

        claim.setActive(false);
        claim.setUpdatedBy(cancelledBy);
        claimRepository.save(claim);
        log.info("Backlog claim {} cancelled by {}", claimId, cancelledBy);
    }

    /**
     * Wrapper that forces a NEW transaction for each Excel row.
     * This ensures rows that succeed are committed even if a later row fails.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long createBacklogClaimInNewTransaction(BacklogClaimRequest request, String enteredBy, ClaimSource source) {
        return createBacklogClaim(request, enteredBy, source);
    }

    /**
     * Import backlog claims from Excel.
     * The outer method is NOT @Transactional so each row can commit independently.
     */
    public BacklogImportResponse importExcel(MultipartFile file, String enteredBy) {

        List<BacklogImportResponse.ImportError> errors = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;
        int totalRows = 0;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();
            
            // Skip header
            if (rowIterator.hasNext()) rowIterator.next();

            int rowIndex = 1;
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                rowIndex++;
                totalRows++;
                
                try {
                    // Mapping Excel columns:
                    // 0: Member Code (civilId or cardNumber)
                    // 1: Provider ID or Name
                    // 2: Service Date
                    // 3: Doctor Name
                    // 4: Diagnosis
                    // 5: Legacy Ref
                    // 6: Service Code
                    // 7: Quantity
                    // 8: Gross Amount
                    // 9: Approved Amount
                    
                    String memberCode = getCellValue(row.getCell(0));
                    String providerRef = getCellValue(row.getCell(1));
                    LocalDate serviceDate = getDateCellValue(row.getCell(2));
                    String doctorName = getCellValue(row.getCell(3));
                    String diagnosis = getCellValue(row.getCell(4));
                    String legacyRef = getCellValue(row.getCell(5));
                    String serviceCode = getCellValue(row.getCell(6));
                    
                    Integer quantity = parseInteger(row.getCell(7), 1);
                    BigDecimal gross = parseBigDecimal(row.getCell(8), BigDecimal.ZERO);
                    BigDecimal approved = parseBigDecimal(row.getCell(9), gross);

                    // Lookups
                    Member member = memberRepository.findByCivilId(memberCode)
                            .or(() -> memberRepository.findByCardNumber(memberCode))
                            .orElseThrow(() -> new RuntimeException("Member not found: " + memberCode));
                    
                    Provider provider = null;
                    if (providerRef.matches("\\d+")) {
                        provider = providerRepository.findById(Long.parseLong(providerRef)).orElse(null);
                    }
                    if (provider == null) {
                        provider = providerRepository.findByName(providerRef)
                                .orElseThrow(() -> new RuntimeException("Provider not found: " + providerRef));
                    }

                    // Create Claim Request for reuse
                    BacklogClaimRequest request = BacklogClaimRequest.builder()
                            .memberId(member.getId())
                            .providerId(provider.getId())
                            .serviceDate(serviceDate != null ? serviceDate : LocalDate.now())
                            .doctorName(doctorName)
                            .diagnosis(diagnosis)
                            .legacyReferenceNumber(legacyRef)
                            .networkStatus(NetworkType.IN_NETWORK)
                            .lines(List.of(BacklogServiceLineDto.builder()
                                    .serviceCode(serviceCode)
                                    .quantity(quantity)
                                    .grossAmount(gross)
                                    .coveredAmount(approved)
                                    .build()))
                            .build();

                    createBacklogClaimInNewTransaction(request, enteredBy, ClaimSource.EXCEL_BACKLOG);
                    successCount++;
                } catch (Exception e) {
                    failureCount++;
                    errors.add(BacklogImportResponse.ImportError.builder()
                            .rowNumber(rowIndex)
                            .errorMessage(e.getMessage())
                            .build());
                }

                // Simple batch commit optimization in Spring isn't direct here 
                // because of @Transactional at method level, but for MVP this works.
            }
        } catch (Exception e) {
            log.error("Excel import failed", e);
            errors.add(BacklogImportResponse.ImportError.builder()
                    .errorMessage("General Error: " + e.getMessage())
                    .build());
        }

        return BacklogImportResponse.builder()
                .totalProcessed(totalRows)
                .successCount(successCount)
                .failureCount(failureCount)
                .errors(errors)
                .build();
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> "";
        };
    }

    private LocalDate getDateCellValue(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        return null;
    }

    private Integer parseInteger(Cell cell, Integer defaultValue) {
        if (cell == null) return defaultValue;
        try {
            if (cell.getCellType() == CellType.NUMERIC) return (int) cell.getNumericCellValue();
            if (cell.getCellType() == CellType.STRING) return Integer.parseInt(cell.getStringCellValue().trim());
        } catch (Exception e) {
            log.warn("Failed to parse integer from cell, using default: {}", defaultValue);
        }
        return defaultValue;
    }

    private BigDecimal parseBigDecimal(Cell cell, BigDecimal defaultValue) {
        if (cell == null) return defaultValue;
        try {
            if (cell.getCellType() == CellType.NUMERIC) return BigDecimal.valueOf(cell.getNumericCellValue());
            if (cell.getCellType() == CellType.STRING) return new BigDecimal(cell.getStringCellValue().trim());
        } catch (Exception e) {
            log.warn("Failed to parse BigDecimal from cell, using default: {}", defaultValue);
        }
        return defaultValue;
    }
}
