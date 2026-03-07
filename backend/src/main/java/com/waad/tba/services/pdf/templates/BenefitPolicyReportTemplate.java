package com.waad.tba.services.pdf.templates;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyResponseDto;
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
 * PDF Template for Benefit Policy Reports
 * نموذج تقارير PDF لسياسات المزايا
 */
@Component
@RequiredArgsConstructor
@SuppressWarnings("deprecation")
public class BenefitPolicyReportTemplate {

    private final PdfFontConfig fontConfig;
    private final PdfTableBuilder tableBuilder;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Generate detailed report for single BenefitPolicy
     */
    public List<Element> generateBenefitPolicyDetailReport(BenefitPolicyResponseDto policy) {
        List<Element> elements = new ArrayList<>();
        
        try {
            // Policy Name
            Paragraph policyName = new Paragraph(policy.getName(), 
                fontConfig.getFont(policy.getName(), true));
            policyName.setAlignment(Element.ALIGN_CENTER);
            policyName.setSpacingAfter(15);
            elements.add(policyName);

            elements.add(createSection("المعلومات الأساسية"));
            elements.add(createBasicInfoTable(policy));
            elements.add(new Paragraph("\n"));

            elements.add(createSection("معلومات الجهات"));
            elements.add(createOrganizationInfoTable(policy));
            elements.add(new Paragraph("\n"));

            elements.add(createSection("الفترة الزمنية"));
            elements.add(createDatesTable(policy));
            elements.add(new Paragraph("\n"));

            elements.add(createSection("الحدود المالية"));
            elements.add(createLimitsTable(policy));
            elements.add(new Paragraph("\n"));

            elements.add(createSection("الإحصائيات"));
            elements.add(createStatsTable(policy));
            elements.add(new Paragraph("\n"));

            if (policy.getNotes() != null && !policy.getNotes().isBlank()) {
                elements.add(createSection("ملاحظات إضافية"));
                elements.add(createNotesTable(policy));
                elements.add(new Paragraph("\n"));
            }

            elements.add(createSection("معلومات التدقيق"));
            elements.add(createAuditTable(policy));
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate BenefitPolicy detail report", e);
        }
        
        return elements;
    }

    /**
     * Generate list report for multiple BenefitPolicies
     */
    public List<Element> generateBenefitPolicyListReport(List<BenefitPolicyResponseDto> policies) {
        List<Element> elements = new ArrayList<>();
        
        try {
            // Summary
            Paragraph summary = new Paragraph("إجمالي السياسات: " + policies.size(), 
                fontConfig.getFont("إجمالي السياسات", true));
            summary.setAlignment(Element.ALIGN_RIGHT);
            summary.setSpacingAfter(15);
            elements.add(summary);

            // Statistics
            long activePolicies = policies.stream().filter(BenefitPolicyResponseDto::isActive).count();
            long effectivePolicies = policies.stream().filter(BenefitPolicyResponseDto::isEffective).count();
            
            Paragraph stats = new Paragraph(
                String.format("السياسات النشطة: %d | السياسات السارية: %d", activePolicies, effectivePolicies),
                fontConfig.getFont("السياسات النشطة", true)
            );
            stats.setAlignment(Element.ALIGN_RIGHT);
            stats.setSpacingAfter(15);
            elements.add(stats);

            // Table
            elements.add(createListTable(policies));
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate BenefitPolicy list report", e);
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

    private PdfPTable createBasicInfoTable(BenefitPolicyResponseDto policy) {
        List<PdfTableBuilder.KeyValue> data = new ArrayList<>();
        data.add(new PdfTableBuilder.KeyValue("كود السياسة", policy.getPolicyCode()));
        data.add(new PdfTableBuilder.KeyValue("الوصف", policy.getDescription()));
        data.add(new PdfTableBuilder.KeyValue("الحالة", policy.getStatusDisplay()));
        data.add(new PdfTableBuilder.KeyValue("نشطة", formatBoolean(policy.isActive())));
        data.add(new PdfTableBuilder.KeyValue("سارية المفعول", formatBoolean(policy.isEffective())));
        return tableBuilder.buildKeyValueTable(data);
    }

    private PdfPTable createOrganizationInfoTable(BenefitPolicyResponseDto policy) {
        List<PdfTableBuilder.KeyValue> data = new ArrayList<>();
        data.add(new PdfTableBuilder.KeyValue("جهة العمل", policy.getEmployerName()));
        data.add(new PdfTableBuilder.KeyValue("رقم جهة العمل", policy.getEmployerOrgId() != null ? policy.getEmployerOrgId().toString() : "-"));
        return tableBuilder.buildKeyValueTable(data);
    }

    private PdfPTable createDatesTable(BenefitPolicyResponseDto policy) {
        List<PdfTableBuilder.KeyValue> data = new ArrayList<>();
        data.add(new PdfTableBuilder.KeyValue("تاريخ البداية", formatDate(policy.getStartDate())));
        data.add(new PdfTableBuilder.KeyValue("تاريخ الانتهاء", formatDate(policy.getEndDate())));
        return tableBuilder.buildKeyValueTable(data);
    }

    private PdfPTable createLimitsTable(BenefitPolicyResponseDto policy) {
        List<PdfTableBuilder.KeyValue> data = new ArrayList<>();
        data.add(new PdfTableBuilder.KeyValue("الحد السنوي", formatAmount(policy.getAnnualLimit())));
        data.add(new PdfTableBuilder.KeyValue("الحد لكل مستفيد", formatAmount(policy.getPerMemberLimit())));
        data.add(new PdfTableBuilder.KeyValue("الحد لكل عائلة", formatAmount(policy.getPerFamilyLimit())));
        data.add(new PdfTableBuilder.KeyValue("نسبة التغطية الافتراضية", formatPercentage(policy.getDefaultCoveragePercent())));
        return tableBuilder.buildKeyValueTable(data);
    }

    private PdfPTable createStatsTable(BenefitPolicyResponseDto policy) {
        List<PdfTableBuilder.KeyValue> data = new ArrayList<>();
        data.add(new PdfTableBuilder.KeyValue("عدد المستفيدين المغطيين", policy.getCoveredMembersCount() != null ? policy.getCoveredMembersCount().toString() : "0"));
        data.add(new PdfTableBuilder.KeyValue("عدد القواعد", policy.getRulesCount() != null ? policy.getRulesCount().toString() : "0"));
        data.add(new PdfTableBuilder.KeyValue("عدد القواعد النشطة", policy.getActiveRulesCount() != null ? policy.getActiveRulesCount().toString() : "0"));
        return tableBuilder.buildKeyValueTable(data);
    }

    private PdfPTable createNotesTable(BenefitPolicyResponseDto policy) {
        List<PdfTableBuilder.KeyValue> data = new ArrayList<>();
        data.add(new PdfTableBuilder.KeyValue("الملاحظات", policy.getNotes()));
        return tableBuilder.buildKeyValueTable(data);
    }

    private PdfPTable createAuditTable(BenefitPolicyResponseDto policy) {
        List<PdfTableBuilder.KeyValue> data = new ArrayList<>();
        data.add(new PdfTableBuilder.KeyValue("تاريخ الإنشاء", formatDateTime(policy.getCreatedAt())));
        data.add(new PdfTableBuilder.KeyValue("تاريخ التحديث", formatDateTime(policy.getUpdatedAt())));
        return tableBuilder.buildKeyValueTable(data);
    }

    private PdfPTable createListTable(List<BenefitPolicyResponseDto> policies) {
        // Create simple card-style table for each policy
        PdfPTable mainTable = new PdfPTable(1);
        mainTable.setWidthPercentage(100);
        mainTable.setSpacingBefore(10);
        
        for (int i = 0; i < policies.size(); i++) {
            BenefitPolicyResponseDto policy = policies.get(i);
            List<PdfTableBuilder.KeyValue> data = new ArrayList<>();
            data.add(new PdfTableBuilder.KeyValue("الرقم", String.valueOf(i + 1)));
            data.add(new PdfTableBuilder.KeyValue("كود السياسة", policy.getPolicyCode()));
            data.add(new PdfTableBuilder.KeyValue("الاسم", policy.getName()));
            data.add(new PdfTableBuilder.KeyValue("جهة العمل", policy.getEmployerName()));
            data.add(new PdfTableBuilder.KeyValue("الحالة", policy.getStatusDisplay()));
            data.add(new PdfTableBuilder.KeyValue("الحد السنوي", formatAmount(policy.getAnnualLimit())));
            data.add(new PdfTableBuilder.KeyValue("المستفيدين", policy.getCoveredMembersCount() != null ? policy.getCoveredMembersCount().toString() : "0"));
            
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

    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "-";
        return String.format("%,.2f SAR", amount);
    }

    private String formatPercentage(Integer percentage) {
        return percentage != null ? percentage + "%" : "-";
    }

    private String formatBoolean(boolean value) {
        return value ? "نعم" : "لا";
    }
}
