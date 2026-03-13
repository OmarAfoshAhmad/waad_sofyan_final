package com.waad.tba.modules.report.service;

import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.pdf.entity.PdfCompanySettings;
import com.waad.tba.modules.pdf.service.PdfCompanySettingsService;
import com.waad.tba.modules.report.dto.ClaimReportDto;
import com.waad.tba.modules.report.dto.ClaimStatementItemDto;
import com.waad.tba.modules.report.dto.ClaimStatementReportDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportDataService {

    private final ClaimRepository claimRepository;
    private final PdfCompanySettingsService settingsService;

    @Transactional(readOnly = true)
    public ClaimReportDto getClaimReportData(List<Long> claimIds) {
        List<Claim> claims = claimRepository.findAllById(claimIds);
        PdfCompanySettings settings = settingsService.getActiveSettings();
        
        List<ClaimStatementReportDto> groupedClaims = new ArrayList<>();
        BigDecimal grandTotalGross = BigDecimal.ZERO;
        BigDecimal grandTotalNet = BigDecimal.ZERO;
        BigDecimal grandTotalRejected = BigDecimal.ZERO;
        BigDecimal grandTotalPatientShare = BigDecimal.ZERO;

        String batchCode = "N/A";
        String providerName = "N/A";
        if (!claims.isEmpty()) {
            Claim first = claims.get(0);
            batchCode = first.getClaimBatch() != null ? first.getClaimBatch().getBatchCode() : "N/A";
            providerName = first.getProviderName() != null ? first.getProviderName() : "N/A";
        }

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (Claim claim : claims) {
            String patientName = claim.getMember() != null ? claim.getMember().getFullName() : "غير معروف";
            String insuranceNumber = claim.getMember() != null && claim.getMember().getPolicyNumber() != null ? claim.getMember().getPolicyNumber() : "غير معروف";
            
            String originNumber = claim.getClaimBatch() != null ? claim.getClaimBatch().getBatchCode() : claim.getId().toString();
            String diagnosis = claim.getDiagnosisDescription() != null ? claim.getDiagnosisDescription() : claim.getDiagnosisCode();
            
            List<ClaimStatementItemDto> items = new ArrayList<>();
            BigDecimal subTotalGross = BigDecimal.ZERO;
            BigDecimal subTotalRejected = BigDecimal.ZERO;
            BigDecimal subTotalPatientShare = claim.getPatientCoPay() != null ? claim.getPatientCoPay() : BigDecimal.ZERO;
            
            for (ClaimLine line : claim.getLines()) {
                BigDecimal gross = line.getRequestedUnitPrice() != null ? 
                    line.getRequestedUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())) : line.getTotalPrice();
                    
                BigDecimal rejected = line.getRefusedAmount() != null ? line.getRefusedAmount() : BigDecimal.ZERO;
                if (Boolean.TRUE.equals(line.getRejected())) {
                    rejected = gross;
                }
                
                // Net at line level is just for display, the real financial net is at claim level
                BigDecimal lineNet = gross.subtract(rejected);
                if (lineNet.compareTo(BigDecimal.ZERO) < 0) lineNet = BigDecimal.ZERO;

                items.add(ClaimStatementItemDto.builder()
                        .medicalService(line.getServiceName())
                        .serviceDate(claim.getServiceDate())
                        .grossAmount(gross)
                        .netAmount(lineNet)
                        .rejectedAmount(rejected)
                        .rejectionReason(line.getRejectionReason())
                        .build());
                        
                subTotalGross = subTotalGross.add(gross);
                subTotalRejected = subTotalRejected.add(rejected);
            }
            
            BigDecimal subTotalNet = claim.getNetPayableAmount(); // This is correctly (Gross - Rejected - PatientShare)
            
            groupedClaims.add(ClaimStatementReportDto.builder()
                    .patientName(patientName)
                    .insuranceNumber(insuranceNumber)
                    .originNumber("CLM-" + originNumber)
                    .diagnosis(diagnosis)
                    .currentContract(claim.getProviderName())
                    .items(items)
                    .subTotalGross(subTotalGross)
                    .subTotalNet(subTotalNet)
                    .subTotalRejected(subTotalRejected)
                    .build());
                    
            grandTotalGross = grandTotalGross.add(subTotalGross);
            grandTotalNet = grandTotalNet.add(subTotalNet);
            grandTotalRejected = grandTotalRejected.add(subTotalRejected);
            grandTotalPatientShare = grandTotalPatientShare.add(subTotalPatientShare);
        }

        String logoBase64 = "";
        if (settings.getLogoData() != null && settings.getLogoData().length > 0) {
            logoBase64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(settings.getLogoData());
        }

        return ClaimReportDto.builder()
                .reportDate(LocalDate.now().format(dateFormatter))
                .companyName(settings.getCompanyName())
                .companyLogoBase64(logoBase64)
                .groupedClaims(groupedClaims)
                .batchCode(batchCode)
                .providerName(providerName)
                .claimCount(claims.size())
                .grandTotalGross(grandTotalGross)
                .grandTotalNet(grandTotalNet)
                .grandTotalRejected(grandTotalRejected)
                .grandTotalPatientShare(grandTotalPatientShare)
                .build();
    }
}
