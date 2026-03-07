package com.waad.tba.services.pdf.templates;

import com.lowagie.text.*;
import com.waad.tba.modules.claim.dto.ClaimResponseDto;
import com.waad.tba.services.pdf.PdfTableBuilder;
import com.waad.tba.services.pdf.config.PdfFontConfig;
import com.waad.tba.services.pdf.dto.PdfReportMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Claim Report Template
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SuppressWarnings("deprecation")
public class ClaimReportTemplate {

    private final PdfFontConfig fontConfig;
    private final PdfTableBuilder tableBuilder;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public List<Element> generateClaimDetailReport(ClaimResponseDto claim) {
        List<Element> elements = new ArrayList<>();

        try {
            // Member Information
            Paragraph memberTitle = new Paragraph(
                    "معلومات العضو / Member Information",
                    fontConfig.getFont("معلومات العضو", true));
            memberTitle.setAlignment(Element.ALIGN_RIGHT);
            memberTitle.setSpacingBefore(10f);
            memberTitle.setSpacingAfter(10f);
            elements.add(memberTitle);

            List<PdfTableBuilder.KeyValue> memberInfo = new ArrayList<>();
            memberInfo.add(new PdfTableBuilder.KeyValue(
                    "اسم العضو / Member Name", claim.getMemberFullName()));
            memberInfo.add(new PdfTableBuilder.KeyValue(
                    "الهوية الوطنية / Civil ID", claim.getMemberCivilId()));
            memberInfo.add(new PdfTableBuilder.KeyValue(
                    "شركة التأمين / Insurance Company",
                    claim.getInsuranceCompanyName() != null ? claim.getInsuranceCompanyName() : "-"));

            elements.add(tableBuilder.buildKeyValueTable(memberInfo));
            elements.add(new Paragraph(" "));

            // Medical Information
            Paragraph medicalTitle = new Paragraph(
                    "المعلومات الطبية / Medical Information",
                    fontConfig.getFont("المعلومات الطبية", true));
            medicalTitle.setAlignment(Element.ALIGN_RIGHT);
            medicalTitle.setSpacingBefore(10f);
            medicalTitle.setSpacingAfter(10f);
            elements.add(medicalTitle);

            List<PdfTableBuilder.KeyValue> medicalInfo = new ArrayList<>();
            medicalInfo.add(new PdfTableBuilder.KeyValue(
                    "مقدم الخدمة / Provider", claim.getProviderName()));
            medicalInfo.add(new PdfTableBuilder.KeyValue(
                    "الطبيب / Doctor",
                    claim.getDoctorName() != null ? claim.getDoctorName() : "-"));
            medicalInfo.add(new PdfTableBuilder.KeyValue(
                    "التشخيص / Diagnosis",
                    claim.getDiagnosis() != null ? claim.getDiagnosis() : "-"));
            medicalInfo.add(new PdfTableBuilder.KeyValue(
                    "تاريخ الزيارة / Visit Date",
                    claim.getVisitDate() != null ? claim.getVisitDate().format(DATE_FORMATTER) : "-"));
            medicalInfo.add(new PdfTableBuilder.KeyValue(
                    "عدد الخدمات / Service Count",
                    claim.getServiceCount() != null ? claim.getServiceCount().toString() : "0"));

            elements.add(tableBuilder.buildKeyValueTable(medicalInfo));
            elements.add(new Paragraph(" "));

            // Financial Information
            Paragraph financialTitle = new Paragraph(
                    "المعلومات المالية / Financial Information",
                    fontConfig.getFont("المعلومات المالية", true));
            financialTitle.setAlignment(Element.ALIGN_RIGHT);
            financialTitle.setSpacingBefore(10f);
            financialTitle.setSpacingAfter(10f);
            elements.add(financialTitle);

            List<PdfTableBuilder.KeyValue> financialInfo = new ArrayList<>();
            financialInfo.add(new PdfTableBuilder.KeyValue(
                    "المبلغ المطلوب / Requested Amount",
                    claim.getRequestedAmount() != null ? claim.getRequestedAmount().toString() : "0.00"));
            financialInfo.add(new PdfTableBuilder.KeyValue(
                    "المبلغ الموافق / Approved Amount",
                    claim.getApprovedAmount() != null ? claim.getApprovedAmount().toString() : "0.00"));
            financialInfo.add(new PdfTableBuilder.KeyValue(
                    "الفرق / Difference",
                    claim.getDifferenceAmount() != null ? claim.getDifferenceAmount().toString() : "0.00"));
            financialInfo.add(new PdfTableBuilder.KeyValue(
                    "تحمل المريض / Patient Co-Pay",
                    claim.getPatientCoPay() != null ? claim.getPatientCoPay().toString() : "0.00"));
            financialInfo.add(new PdfTableBuilder.KeyValue(
                    "صافي المقدم / Net Provider Amount",
                    claim.getNetProviderAmount() != null ? claim.getNetProviderAmount().toString() : "0.00"));

            elements.add(tableBuilder.buildKeyValueTable(financialInfo));
            elements.add(new Paragraph(" "));

            // Status Information
            Paragraph statusTitle = new Paragraph(
                    "الحالة والمراجعة / Status & Review",
                    fontConfig.getFont("الحالة والمراجعة", true));
            statusTitle.setAlignment(Element.ALIGN_RIGHT);
            statusTitle.setSpacingBefore(10f);
            statusTitle.setSpacingAfter(10f);
            elements.add(statusTitle);

            List<PdfTableBuilder.KeyValue> statusInfo = new ArrayList<>();
            statusInfo.add(new PdfTableBuilder.KeyValue(
                    "الحالة / Status",
                    claim.getStatusLabel() != null ? claim.getStatusLabel() : "-"));
            statusInfo.add(new PdfTableBuilder.KeyValue(
                    "تاريخ المراجعة / Reviewed At",
                    claim.getReviewedAt() != null ? claim.getReviewedAt().format(DATETIME_FORMATTER) : "-"));
            statusInfo.add(new PdfTableBuilder.KeyValue(
                    "تاريخ الإنشاء / Created At",
                    claim.getCreatedAt() != null ? claim.getCreatedAt().format(DATETIME_FORMATTER) : "-"));

            elements.add(tableBuilder.buildKeyValueTable(statusInfo));

            // Reviewer Comment if available
            if (claim.getReviewerComment() != null && !claim.getReviewerComment().trim().isEmpty()) {
                elements.add(new Paragraph(" "));
                Paragraph commentTitle = new Paragraph(
                        "تعليق المراجع / Reviewer Comment",
                        fontConfig.getFont("تعليق المراجع", true));
                commentTitle.setAlignment(Element.ALIGN_RIGHT);
                commentTitle.setSpacingBefore(10f);
                commentTitle.setSpacingAfter(10f);
                elements.add(commentTitle);

                Paragraph comment = new Paragraph(
                        claim.getReviewerComment(),
                        fontConfig.getFont(claim.getReviewerComment(), false));
                comment.setAlignment(Element.ALIGN_RIGHT);
                elements.add(comment);
            }

        } catch (Exception e) {
            log.error("[ClaimReportTemplate] Failed to generate claim detail report", e);
            elements.add(new Paragraph("Error generating report", fontConfig.getLatinNormalFont()));
        }

        return elements;
    }

    public List<Element> generateClaimListReport(List<ClaimResponseDto> claims) {
        List<Element> elements = new ArrayList<>();

        try {
            Paragraph intro = new Paragraph(
                    String.format("إجمالي المطالبات: %d / Total Claims: %d", claims.size(), claims.size()),
                    fontConfig.getFont("إجمالي", false));
            intro.setAlignment(Element.ALIGN_CENTER);
            intro.setSpacingAfter(20f);
            elements.add(intro);

            for (ClaimResponseDto claim : claims) {
                List<PdfTableBuilder.KeyValue> claimData = new ArrayList<>();
                claimData.add(new PdfTableBuilder.KeyValue("العضو / Member", claim.getMemberFullName()));
                claimData.add(new PdfTableBuilder.KeyValue("المقدم / Provider", claim.getProviderName()));
                claimData.add(new PdfTableBuilder.KeyValue("المبلغ / Amount",
                        claim.getApprovedAmount() != null ? claim.getApprovedAmount().toString() : "0.00"));
                claimData.add(new PdfTableBuilder.KeyValue("الحالة / Status",
                        claim.getStatusLabel() != null ? claim.getStatusLabel() : "-"));

                elements.add(tableBuilder.buildKeyValueTable(claimData));
                elements.add(new Paragraph(" "));
            }

        } catch (Exception e) {
            log.error("[ClaimReportTemplate] Failed to generate claim list report", e);
            elements.add(new Paragraph("Error generating report", fontConfig.getLatinNormalFont()));
        }

        return elements;
    }

    public static PdfReportMetadata createClaimReportMetadata(Long claimId, String username) {
        return PdfReportMetadata.builder()
                .titleAr("تقرير مطالبة")
                .titleEn("Claim Report")
                .reportType(PdfReportMetadata.ReportType.CLAIM)
                .orientation(PdfReportMetadata.PageOrientation.PORTRAIT)
                .generatedAt(LocalDateTime.now())
                .trackingId("CLAIM-" + (claimId != null ? claimId.toString() : "UNKNOWN"))
                .generatedBy(username)
                .build();
    }

    public static PdfReportMetadata createClaimListReportMetadata(int count, String username) {
        return PdfReportMetadata.builder()
                .titleAr("قائمة المطالبات")
                .titleEn("Claims List")
                .reportType(PdfReportMetadata.ReportType.CLAIM)
                .orientation(PdfReportMetadata.PageOrientation.PORTRAIT)
                .generatedAt(LocalDateTime.now())
                .trackingId("CLAIMS-LIST-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")))
                .generatedBy(username)
                .build();
    }
}
