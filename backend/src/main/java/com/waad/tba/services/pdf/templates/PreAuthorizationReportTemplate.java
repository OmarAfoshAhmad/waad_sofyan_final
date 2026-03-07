package com.waad.tba.services.pdf.templates;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.waad.tba.modules.preauthorization.dto.PreAuthorizationResponseDto;
import com.waad.tba.services.pdf.config.PdfFontConfig;
import com.waad.tba.services.pdf.PdfTableBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF Template for PreAuthorization Reports
 * نموذج تقارير PDF للموافقات المسبقة
 */
@Component
@RequiredArgsConstructor
@SuppressWarnings("deprecation")
public class PreAuthorizationReportTemplate {

    private final PdfFontConfig fontConfig;
    private final PdfTableBuilder tableBuilder;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Generate detailed report for single PreAuthorization
     */
    public List<Element> generatePreAuthorizationDetailReport(PreAuthorizationResponseDto preAuth) {
        List<Element> elements = new ArrayList<>();
        
        try {
            // Reference Number
            Paragraph refNum = new Paragraph("رقم المرجع: " + preAuth.getReferenceNumber(), 
                fontConfig.getFont("رقم المرجع", true));
            refNum.setAlignment(Element.ALIGN_CENTER);
            refNum.setSpacingAfter(15);
            elements.add(refNum);

            elements.add(createSection("المعلومات الأساسية"));
            elements.add(createBasicInfoTable(preAuth));
            elements.add(new Paragraph("\n"));

            elements.add(createSection("معلومات المستفيد"));
            elements.add(createMemberInfoTable(preAuth));
            elements.add(new Paragraph("\n"));

            elements.add(createSection("معلومات المزود"));
            elements.add(createProviderInfoTable(preAuth));
            elements.add(new Paragraph("\n"));

            elements.add(createSection("تفاصيل الخدمة"));
            elements.add(createServiceInfoTable(preAuth));
            elements.add(new Paragraph("\n"));

            elements.add(createSection("المعلومات المالية"));
            elements.add(createFinancialInfoTable(preAuth));
            elements.add(new Paragraph("\n"));

            if (hasAdditionalInfo(preAuth)) {
                elements.add(createSection("معلومات إضافية"));
                elements.add(createAdditionalInfoTable(preAuth));
                elements.add(new Paragraph("\n"));
            }

            elements.add(createSection("حالة الطلب"));
            elements.add(createStatusTable(preAuth));
            elements.add(new Paragraph("\n"));

            elements.add(createSection("معلومات التدقيق"));
            elements.add(createAuditTable(preAuth));
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PreAuthorization detail report", e);
        }
        
        return elements;
    }

    /**
     * Generate list report for multiple PreAuthorizations
     */
    public List<Element> generatePreAuthorizationListReport(List<PreAuthorizationResponseDto> preAuths) {
        List<Element> elements = new ArrayList<>();
        
        try {
            // Summary
            Paragraph summary = new Paragraph("إجمالي الموافقات: " + preAuths.size(), 
                fontConfig.getFont("إجمالي الموافقات", true));
            summary.setAlignment(Element.ALIGN_RIGHT);
            summary.setSpacingAfter(15);
            elements.add(summary);

            // Table
            elements.add(createListTable(preAuths));
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PreAuthorization list report", e);
        }
        
        return elements;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    private Paragraph createSection(String title) {
        Paragraph section = new Paragraph(title, fontConfig.getFont(title, true));
        section.setAlignment(Element.ALIGN_RIGHT);
        section.setSpacingBefore(10);
        section.setSpacingAfter(5);
        return section;
    }

    private PdfPTable createBasicInfoTable(PreAuthorizationResponseDto preAuth) {
        List<PdfTableBuilder.KeyValue> data = new ArrayList<>();
        data.add(new PdfTableBuilder.KeyValue("حالة الطلب", formatStatus(preAuth.getStatus())));
        data.add(new PdfTableBuilder.KeyValue("الأولوية", formatPriority(preAuth.getPriority())));
        data.add(new PdfTableBuilder.KeyValue("تاريخ الطلب", formatDate(preAuth.getRequestDate())));
        data.add(new PdfTableBuilder.KeyValue("تاريخ الانتهاء", formatDate(preAuth.getExpiryDate())));
        if (preAuth.getDaysUntilExpiry() != null) {
            data.add(new PdfTableBuilder.KeyValue("الأيام المتبقية", preAuth.getDaysUntilExpiry().toString() + " يوم"));
        }
        return tableBuilder.buildKeyValueTable(data);
    }

    private PdfPTable createMemberInfoTable(PreAuthorizationResponseDto preAuth) {
        List<PdfTableBuilder.KeyValue> data = new ArrayList<>();
        data.add(new PdfTableBuilder.KeyValue("اسم المستفيد", preAuth.getMemberName()));
        data.add(new PdfTableBuilder.KeyValue("رقم البطاقة", preAuth.getMemberCardNumber()));
        data.add(new PdfTableBuilder.KeyValue("رقم المستفيد", preAuth.getMemberId() != null ? preAuth.getMemberId().toString() : "-"));
        return tableBuilder.buildKeyValueTable(data);
    }

    private PdfPTable createProviderInfoTable(PreAuthorizationResponseDto preAuth) {
        List<PdfTableBuilder.KeyValue> data = new ArrayList<>();
        data.add(new PdfTableBuilder.KeyValue("اسم المزود", preAuth.getProviderName()));
        data.add(new PdfTableBuilder.KeyValue("رقم الترخيص", preAuth.getProviderLicense()));
        data.add(new PdfTableBuilder.KeyValue("رقم المزود", preAuth.getProviderId() != null ? preAuth.getProviderId().toString() : "-"));
        return tableBuilder.buildKeyValueTable(data);
    }

    private PdfPTable createServiceInfoTable(PreAuthorizationResponseDto preAuth) {
        List<PdfTableBuilder.KeyValue> data = new ArrayList<>();
        data.add(new PdfTableBuilder.KeyValue("كود الخدمة", preAuth.getServiceCode()));
        data.add(new PdfTableBuilder.KeyValue("اسم الخدمة", preAuth.getServiceName()));
        data.add(new PdfTableBuilder.KeyValue("رمز التشخيص", preAuth.getDiagnosisCode()));
        data.add(new PdfTableBuilder.KeyValue("وصف التشخيص", preAuth.getDiagnosisDescription()));
        return tableBuilder.buildKeyValueTable(data);
    }

    private PdfPTable createFinancialInfoTable(PreAuthorizationResponseDto preAuth) {
        List<PdfTableBuilder.KeyValue> data = new ArrayList<>();
        data.add(new PdfTableBuilder.KeyValue("السعر التعاقدي", formatAmount(preAuth.getContractPrice(), preAuth.getCurrency())));
        data.add(new PdfTableBuilder.KeyValue("المبلغ المعتمد", formatAmount(preAuth.getApprovedAmount(), preAuth.getCurrency())));
        data.add(new PdfTableBuilder.KeyValue("نسبة التحمل", formatPercentage(preAuth.getCopayPercentage())));
        data.add(new PdfTableBuilder.KeyValue("مبلغ التحمل", formatAmount(preAuth.getCopayAmount(), preAuth.getCurrency())));
        data.add(new PdfTableBuilder.KeyValue("المبلغ المغطى", formatAmount(preAuth.getInsuranceCoveredAmount(), preAuth.getCurrency())));
        data.add(new PdfTableBuilder.KeyValue("وجود عقد", formatBoolean(preAuth.getHasContract())));
        return tableBuilder.buildKeyValueTable(data);
    }

    private PdfPTable createAdditionalInfoTable(PreAuthorizationResponseDto preAuth) {
        List<PdfTableBuilder.KeyValue> data = new ArrayList<>();
        if (preAuth.getNotes() != null && !preAuth.getNotes().isBlank()) {
            data.add(new PdfTableBuilder.KeyValue("ملاحظات", preAuth.getNotes()));
        }
        if (preAuth.getRejectionReason() != null && !preAuth.getRejectionReason().isBlank()) {
            data.add(new PdfTableBuilder.KeyValue("سبب الرفض", preAuth.getRejectionReason()));
        }
        return tableBuilder.buildKeyValueTable(data);
    }

    private PdfPTable createStatusTable(PreAuthorizationResponseDto preAuth) {
        List<PdfTableBuilder.KeyValue> data = new ArrayList<>();
        data.add(new PdfTableBuilder.KeyValue("صالح", formatBoolean(preAuth.getIsValid())));
        data.add(new PdfTableBuilder.KeyValue("منتهي", formatBoolean(preAuth.getIsExpired())));
        data.add(new PdfTableBuilder.KeyValue("يمكن الاعتماد", formatBoolean(preAuth.getCanBeApproved())));
        data.add(new PdfTableBuilder.KeyValue("يمكن الرفض", formatBoolean(preAuth.getCanBeRejected())));
        data.add(new PdfTableBuilder.KeyValue("يمكن الإلغاء", formatBoolean(preAuth.getCanBeCancelled())));
        data.add(new PdfTableBuilder.KeyValue("نشط", formatBoolean(preAuth.getActive())));
        return tableBuilder.buildKeyValueTable(data);
    }

    private PdfPTable createAuditTable(PreAuthorizationResponseDto preAuth) {
        List<PdfTableBuilder.KeyValue> data = new ArrayList<>();
        data.add(new PdfTableBuilder.KeyValue("تاريخ الإنشاء", formatDateTime(preAuth.getCreatedAt())));
        data.add(new PdfTableBuilder.KeyValue("تاريخ التحديث", formatDateTime(preAuth.getUpdatedAt())));
        if (preAuth.getCreatedBy() != null) {
            data.add(new PdfTableBuilder.KeyValue("منشئ بواسطة", preAuth.getCreatedBy()));
        }
        if (preAuth.getUpdatedBy() != null) {
            data.add(new PdfTableBuilder.KeyValue("محدث بواسطة", preAuth.getUpdatedBy()));
        }
        if (preAuth.getApprovedAt() != null) {
            data.add(new PdfTableBuilder.KeyValue("تاريخ الاعتماد", formatDateTime(preAuth.getApprovedAt())));
        }
        if (preAuth.getApprovedBy() != null) {
            data.add(new PdfTableBuilder.KeyValue("معتمد بواسطة", preAuth.getApprovedBy()));
        }
        return tableBuilder.buildKeyValueTable(data);
    }

    private PdfPTable createListTable(List<PreAuthorizationResponseDto> preAuths) {
        // Create simple card-style table for each preAuth
        // (Since we don't have buildDataTable, we'll build it differently)
        PdfPTable mainTable = new PdfPTable(1);
        mainTable.setWidthPercentage(100);
        mainTable.setSpacingBefore(10);
        
        for (int i = 0; i < preAuths.size(); i++) {
            PreAuthorizationResponseDto preAuth = preAuths.get(i);
            List<PdfTableBuilder.KeyValue> data = new ArrayList<>();
            data.add(new PdfTableBuilder.KeyValue("الرقم", String.valueOf(i + 1)));
            data.add(new PdfTableBuilder.KeyValue("رقم المرجع", preAuth.getReferenceNumber()));
            data.add(new PdfTableBuilder.KeyValue("المستفيد", preAuth.getMemberName()));
            data.add(new PdfTableBuilder.KeyValue("المزود", preAuth.getProviderName()));
            data.add(new PdfTableBuilder.KeyValue("الخدمة", preAuth.getServiceName()));
            data.add(new PdfTableBuilder.KeyValue("الحالة", formatStatus(preAuth.getStatus())));
            data.add(new PdfTableBuilder.KeyValue("المبلغ المعتمد", formatAmount(preAuth.getApprovedAmount(), preAuth.getCurrency())));
            
            PdfPTable itemTable = tableBuilder.buildKeyValueTable(data);
            PdfPCell cell = new PdfPCell();
            cell.addElement(itemTable);
            cell.setPadding(5);
            cell.setBorder(Rectangle.NO_BORDER);
            mainTable.addCell(cell);
        }
        
        return mainTable;
    }

    private String formatDate(LocalDate date) {
        return date != null ? date.format(dateFormatter) : "-";
    }

    private String formatDateTime(Object dateTime) {
        return dateTime != null ? dateTime.toString() : "-";
    }

    private String formatAmount(BigDecimal amount, String currency) {
        if (amount == null) return "-";
        String curr = currency != null ? currency : "SAR";
        return String.format("%,.2f %s", amount, curr);
    }

    private String formatPercentage(BigDecimal percentage) {
        return percentage != null ? percentage + "%" : "-";
    }

    private String formatBoolean(Boolean value) {
        if (value == null) return "غير محدد";
        return value ? "نعم" : "لا";
    }

    private String formatStatus(String status) {
        if (status == null) return "-";
        return switch (status.toUpperCase()) {
            case "PENDING" -> "قيد الانتظار";
            case "APPROVED" -> "معتمد";
            case "REJECTED" -> "مرفوض";
            case "CANCELLED" -> "ملغي";
            case "EXPIRED" -> "منتهي";
            default -> status;
        };
    }

    private String formatPriority(String priority) {
        if (priority == null) return "-";
        return switch (priority.toUpperCase()) {
            case "URGENT" -> "عاجل";
            case "HIGH" -> "عالي";
            case "NORMAL" -> "عادي";
            case "LOW" -> "منخفض";
            default -> priority;
        };
    }

    private boolean hasAdditionalInfo(PreAuthorizationResponseDto preAuth) {
        return (preAuth.getNotes() != null && !preAuth.getNotes().isBlank()) ||
               (preAuth.getRejectionReason() != null && !preAuth.getRejectionReason().isBlank());
    }
}
