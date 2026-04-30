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
                BigDecimal grandTotalExpectedNet = BigDecimal.ZERO;

                String batchCode = "N/A";
                String providerName = "N/A";

                // Find first valid provider name and batch code from all claims
                for (Claim c : claims) {
                        if (providerName.equals("N/A") && c.getProviderName() != null) {
                                providerName = c.getProviderName();
                        }
                        if (batchCode.equals("N/A") && c.getClaimBatch() != null) {
                                batchCode = c.getClaimBatch().getBatchCode();
                        }
                }

                DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

                for (Claim claim : claims) {
                        String patientName = claim.getMember() != null ? claim.getMember().getFullName() : "غير معروف";
                        String insuranceNumber = "غير معروف";
                        String patientRef = "غير معروف";
                        if (claim.getMember() != null) {
                                if (claim.getMember().getCardNumber() != null
                                                && !claim.getMember().getCardNumber().isBlank()) {
                                        patientRef = claim.getMember().getCardNumber();
                                        insuranceNumber = claim.getMember().getCardNumber();
                                } else if (claim.getMember().getPolicyNumber() != null
                                                && !claim.getMember().getPolicyNumber().isBlank()) {
                                        insuranceNumber = claim.getMember().getPolicyNumber();
                                } else if (claim.getMember().getNationalNumber() != null
                                                && !claim.getMember().getNationalNumber().isBlank()) {
                                        insuranceNumber = claim.getMember().getNationalNumber();
                                }
                        }

                        String currentBatchCode = claim.getClaimBatch() != null ? claim.getClaimBatch().getBatchCode()
                                        : "N/A";
                        String diagnosis = claim.getDiagnosisDescription() != null ? claim.getDiagnosisDescription()
                                        : claim.getDiagnosisCode();

                        List<ClaimStatementItemDto> items = new ArrayList<>();
                        BigDecimal subTotalGross = BigDecimal.ZERO;
                        BigDecimal subTotalRejected = BigDecimal.ZERO;
                        BigDecimal subTotalPatientShare = claim.getPatientCoPay() != null ? claim.getPatientCoPay()
                                        : BigDecimal.ZERO;

                        for (ClaimLine line : claim.getLines()) {
                                BigDecimal gross = line.getRequestedUnitPrice() != null
                                                ? line.getRequestedUnitPrice()
                                                                .multiply(BigDecimal.valueOf(line.getQuantity()))
                                                : line.getTotalPrice();

                                BigDecimal rejected = line.getRefusedAmount() != null ? line.getRefusedAmount()
                                                : BigDecimal.ZERO;
                                boolean claimIsRejected = claim
                                                .getStatus() == com.waad.tba.modules.claim.entity.ClaimStatus.REJECTED;
                                if (Boolean.TRUE.equals(line.getRejected()) || claimIsRejected) {
                                        rejected = gross;
                                }

                                BigDecimal lineNet = gross.subtract(rejected);
                                if (lineNet.compareTo(BigDecimal.ZERO) < 0)
                                        lineNet = BigDecimal.ZERO;

                                String reportReason = line.getRejectionReason();
                                if ((reportReason == null || reportReason.isBlank())) {
                                        if (Boolean.TRUE.equals(line.getRejected()) || claimIsRejected) {
                                                reportReason = "الخدمة مرفوضة بالكامل";
                                        } else if (rejected.compareTo(BigDecimal.ZERO) > 0) {
                                                if (reportReason == null || reportReason.isBlank()) {
                                                        reportReason = "تجاوز السعر التعاقدي و/أو سقف المنفعة";
                                                }
                                        } else if (claim.getReviewerComment() != null
                                                        && !claim.getReviewerComment().isBlank()) {
                                                // Fallback to claim level comment if line has no specific reason but
                                                // has refusal
                                                reportReason = claim.getReviewerComment();
                                        }
                                }

                                items.add(ClaimStatementItemDto.builder()
                                                .medicalService(line.getServiceName())
                                                .serviceDate(claim.getServiceDate())
                                                .grossAmount(gross)
                                                .netAmount(lineNet)
                                                .rejectedAmount(rejected)
                                                .rejectionReason(reportReason)
                                                .rejectionReasonArabic(reportReason)
                                                .build());

                                subTotalGross = subTotalGross.add(gross);
                                subTotalRejected = subTotalRejected.add(rejected);
                        }

                        BigDecimal subTotalNet = subTotalGross.subtract(subTotalRejected);
                        BigDecimal subTotalExpectedNet = subTotalNet;
                        if (subTotalExpectedNet.compareTo(BigDecimal.ZERO) < 0) {
                                subTotalExpectedNet = BigDecimal.ZERO;
                        }
                        BigDecimal subTotalNetDifference = subTotalNet.subtract(subTotalExpectedNet);
                        boolean subTotalInconsistent = subTotalNetDifference.abs()
                                        .compareTo(new BigDecimal("0.001")) > 0;

                        groupedClaims.add(ClaimStatementReportDto.builder()
                                        .patientName(patientName)
                                        .insuranceNumber(insuranceNumber)
                                        .patientRef(patientRef)
                                        .batchCode(currentBatchCode)
                                        .claimId(claim.getId())
                                        .originNo(claim.getMember() != null && claim.getMember().getCardNumber() != null
                                                        ? claim.getMember().getCardNumber()
                                                        : null)
                                        .complaint(claim.getComplaint())
                                        .diagnosis(diagnosis)
                                        .currentContract(claim.getProviderName())
                                        .items(items)
                                        .subTotalGross(subTotalGross)
                                        .subTotalNet(subTotalNet)
                                        .subTotalRejected(subTotalRejected)
                                        .subTotalPatientShare(subTotalPatientShare)
                                        .subTotalExpectedNet(subTotalExpectedNet)
                                        .subTotalNetDifference(subTotalNetDifference)
                                        .subTotalInconsistent(subTotalInconsistent)
                                        .build());

                        grandTotalGross = grandTotalGross.add(subTotalGross);
                        grandTotalNet = grandTotalNet.add(subTotalNet);
                        grandTotalRejected = grandTotalRejected.add(subTotalRejected);
                        grandTotalPatientShare = grandTotalPatientShare.add(subTotalPatientShare);
                        grandTotalExpectedNet = grandTotalExpectedNet.add(subTotalExpectedNet);
                }

                BigDecimal grandTotalNetDifference = grandTotalNet.subtract(grandTotalExpectedNet);
                boolean grandTotalInconsistent = grandTotalNetDifference.abs().compareTo(new BigDecimal("0.001")) > 0;

                String logoBase64 = settings.getLogoBase64DataUrl();
                if (logoBase64 == null)
                        logoBase64 = "";

                // Default Intro Text with batch replacement if necessary
                String intro = settings.getClaimReportIntro();
                if (intro == null || intro.isEmpty()) {
                        intro = "نحيطكم علماً بأننا قد انتهينا من مراجعة المطالبات المالية المقدمة من طرفكم والمشار إليها في الدفعة رقم ("
                                        + batchCode
                                        + ")، وقد تمت المراجعة الفنية والمالية وفق المعايير المعتمدة، وكانت النتائج كالتالي:";
                } else if (intro.contains("{batchCode}")) {
                        intro = intro.replace("{batchCode}", batchCode);
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
                                .grandTotalExpectedNet(grandTotalExpectedNet)
                                .grandTotalNetDifference(grandTotalNetDifference)
                                .grandTotalInconsistent(grandTotalInconsistent)
                                // New specialized settings
                                .reportTitle(settings.getClaimReportTitle() != null ? settings.getClaimReportTitle()
                                                : "نظام وعد الطبي")
                                .primaryColor(settings.getClaimReportPrimaryColor() != null
                                                ? settings.getClaimReportPrimaryColor()
                                                : "#005f6b")
                                .introText(intro)
                                .footerNote(settings.getClaimReportFooterNote() != null
                                                ? settings.getClaimReportFooterNote()
                                                : "يرجى التكرم بمراجعة التفاصيل والملاحظات المرفقة، وفي حال وجود أي اعتراض يرجى مراسلتنا في غضون أسبوعين من تاريخه.")
                                .sigRightTop(settings.getClaimReportSigRightTop() != null
                                                ? settings.getClaimReportSigRightTop()
                                                : "والسلام عليكم")
                                .sigRightBottom(
                                                settings.getClaimReportSigRightBottom() != null
                                                                ? settings.getClaimReportSigRightBottom()
                                                                : "قسم المراجعة والتدقيق")
                                .sigLeftTop(settings.getClaimReportSigLeftTop() != null
                                                ? settings.getClaimReportSigLeftTop()
                                                : "")
                                .sigLeftBottom(settings.getClaimReportSigLeftBottom() != null
                                                ? settings.getClaimReportSigLeftBottom()
                                                : "إدارة الحسابات")
                                .build();
        }
}
