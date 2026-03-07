package com.waad.tba.services.pdf.templates;

import com.lowagie.text.*;
import com.waad.tba.modules.providercontract.dto.ContractResponseDto;
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
 * Provider Contract Report Template
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SuppressWarnings("deprecation")
public class ContractReportTemplate {
    
    private final PdfFontConfig fontConfig;
    private final PdfTableBuilder tableBuilder;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    
    public List<Element> generateContractDetailReport(ContractResponseDto contract) {
        List<Element> elements = new ArrayList<>();
        
        try {
            // Contract Information
            Paragraph contractTitle = new Paragraph(
                "معلومات العقد / Contract Information",
                fontConfig.getFont("معلومات العقد", true)
            );
            contractTitle.setAlignment(Element.ALIGN_RIGHT);
            contractTitle.setSpacingBefore(10f);
            contractTitle.setSpacingAfter(10f);
            elements.add(contractTitle);
            
            List<PdfTableBuilder.KeyValue> contractInfo = new ArrayList<>();
            contractInfo.add(new PdfTableBuilder.KeyValue(
                "رقم العقد / Contract Number", contract.getContractNumber()));
            contractInfo.add(new PdfTableBuilder.KeyValue(
                "كود العقد / Contract Code", contract.getContractCode()));
            contractInfo.add(new PdfTableBuilder.KeyValue(
                "مقدم الخدمة / Provider", contract.getProviderName()));
            contractInfo.add(new PdfTableBuilder.KeyValue(
                "الحالة / Status", 
                contract.getStatusLabel() != null ? contract.getStatusLabel() : "-"));
            contractInfo.add(new PdfTableBuilder.KeyValue(
                "نموذج التسعير / Pricing Model", 
                contract.getPricingModelLabel() != null ? contract.getPricingModelLabel() : "-"));
            
            elements.add(tableBuilder.buildKeyValueTable(contractInfo));
            elements.add(new Paragraph(" "));
            
            // Financial Information
            Paragraph financialTitle = new Paragraph(
                "المعلومات المالية / Financial Information",
                fontConfig.getFont("المعلومات المالية", true)
            );
            financialTitle.setAlignment(Element.ALIGN_RIGHT);
            financialTitle.setSpacingBefore(10f);
            financialTitle.setSpacingAfter(10f);
            elements.add(financialTitle);
            
            List<PdfTableBuilder.KeyValue> financialInfo = new ArrayList<>();
            financialInfo.add(new PdfTableBuilder.KeyValue(
                "نسبة الخصم / Discount %", 
                contract.getDiscountPercent() != null ? contract.getDiscountPercent().toString() + "%" : "-"));
            financialInfo.add(new PdfTableBuilder.KeyValue(
                "القيمة الإجمالية / Total Value", 
                contract.getTotalValue() != null ? contract.getTotalValue().toString() : "-"));
            financialInfo.add(new PdfTableBuilder.KeyValue(
                "العملة / Currency", 
                contract.getCurrency() != null ? contract.getCurrency() : "-"));
            financialInfo.add(new PdfTableBuilder.KeyValue(
                "شروط الدفع / Payment Terms", 
                contract.getPaymentTerms() != null ? contract.getPaymentTerms() : "-"));
            
            elements.add(tableBuilder.buildKeyValueTable(financialInfo));
            elements.add(new Paragraph(" "));
            
            // Dates Information
            Paragraph datesTitle = new Paragraph(
                "التواريخ / Dates",
                fontConfig.getFont("التواريخ", true)
            );
            datesTitle.setAlignment(Element.ALIGN_RIGHT);
            datesTitle.setSpacingBefore(10f);
            datesTitle.setSpacingAfter(10f);
            elements.add(datesTitle);
            
            List<PdfTableBuilder.KeyValue> datesInfo = new ArrayList<>();
            datesInfo.add(new PdfTableBuilder.KeyValue(
                "تاريخ البدء / Start Date", 
                contract.getStartDate() != null ? contract.getStartDate().format(DATE_FORMATTER) : "-"));
            datesInfo.add(new PdfTableBuilder.KeyValue(
                "تاريخ الانتهاء / End Date", 
                contract.getEndDate() != null ? contract.getEndDate().format(DATE_FORMATTER) : "-"));
            datesInfo.add(new PdfTableBuilder.KeyValue(
                "تاريخ التوقيع / Signed Date", 
                contract.getSignedDate() != null ? contract.getSignedDate().format(DATE_FORMATTER) : "-"));
            datesInfo.add(new PdfTableBuilder.KeyValue(
                "التجديد التلقائي / Auto Renew", 
                contract.getAutoRenew() != null && contract.getAutoRenew() ? "نعم / Yes" : "لا / No"));
            
            elements.add(tableBuilder.buildKeyValueTable(datesInfo));
            elements.add(new Paragraph(" "));
            
            // Contact Information
            Paragraph contactTitle = new Paragraph(
                "معلومات الاتصال / Contact Information",
                fontConfig.getFont("معلومات الاتصال", true)
            );
            contactTitle.setAlignment(Element.ALIGN_RIGHT);
            contactTitle.setSpacingBefore(10f);
            contactTitle.setSpacingAfter(10f);
            elements.add(contactTitle);
            
            List<PdfTableBuilder.KeyValue> contactInfo = new ArrayList<>();
            contactInfo.add(new PdfTableBuilder.KeyValue(
                "شخص الاتصال / Contact Person", 
                contract.getContactPerson() != null ? contract.getContactPerson() : "-"));
            contactInfo.add(new PdfTableBuilder.KeyValue(
                "هاتف الاتصال / Contact Phone", 
                contract.getContactPhone() != null ? contract.getContactPhone() : "-"));
            
            elements.add(tableBuilder.buildKeyValueTable(contactInfo));
            
            // Notes if available
            if (contract.getNotes() != null && !contract.getNotes().trim().isEmpty()) {
                elements.add(new Paragraph(" "));
                Paragraph notesTitle = new Paragraph(
                    "ملاحظات / Notes",
                    fontConfig.getFont("ملاحظات", true)
                );
                notesTitle.setAlignment(Element.ALIGN_RIGHT);
                notesTitle.setSpacingBefore(10f);
                notesTitle.setSpacingAfter(10f);
                elements.add(notesTitle);
                
                Paragraph notes = new Paragraph(
                    contract.getNotes(),
                    fontConfig.getFont(contract.getNotes(), false)
                );
                notes.setAlignment(Element.ALIGN_RIGHT);
                elements.add(notes);
            }
            
        } catch (Exception e) {
            log.error("[ContractReportTemplate] Failed to generate contract detail report", e);
            elements.add(new Paragraph("Error generating report", fontConfig.getLatinNormalFont()));
        }
        
        return elements;
    }
    
    public List<Element> generateContractListReport(List<ContractResponseDto> contracts) {
        List<Element> elements = new ArrayList<>();
        
        try {
            Paragraph intro = new Paragraph(
                String.format("إجمالي العقود: %d / Total Contracts: %d", contracts.size(), contracts.size()),
                fontConfig.getFont("إجمالي", false)
            );
            intro.setAlignment(Element.ALIGN_CENTER);
            intro.setSpacingAfter(20f);
            elements.add(intro);
            
            for (ContractResponseDto contract : contracts) {
                List<PdfTableBuilder.KeyValue> contractData = new ArrayList<>();
                contractData.add(new PdfTableBuilder.KeyValue("رقم العقد / Number", contract.getContractNumber()));
                contractData.add(new PdfTableBuilder.KeyValue("المقدم / Provider", contract.getProviderName()));
                contractData.add(new PdfTableBuilder.KeyValue("الحالة / Status", 
                    contract.getStatusLabel() != null ? contract.getStatusLabel() : "-"));
                contractData.add(new PdfTableBuilder.KeyValue("التواريخ / Dates", 
                    (contract.getStartDate() != null ? contract.getStartDate().format(DATE_FORMATTER) : "-") + 
                    " → " + 
                    (contract.getEndDate() != null ? contract.getEndDate().format(DATE_FORMATTER) : "-")));
                
                elements.add(tableBuilder.buildKeyValueTable(contractData));
                elements.add(new Paragraph(" "));
            }
            
        } catch (Exception e) {
            log.error("[ContractReportTemplate] Failed to generate contract list report", e);
            elements.add(new Paragraph("Error generating report", fontConfig.getLatinNormalFont()));
        }
        
        return elements;
    }
    
    public static PdfReportMetadata createContractReportMetadata(String contractNumber, String username) {
        return PdfReportMetadata.builder()
            .titleAr("تقرير عقد مقدم خدمة")
            .titleEn("Provider Contract Report")
            .reportType(PdfReportMetadata.ReportType.CONTRACT)
            .orientation(PdfReportMetadata.PageOrientation.PORTRAIT)
            .generatedAt(LocalDateTime.now())
            .trackingId("CONTRACT-" + (contractNumber != null ? contractNumber : "UNKNOWN"))
            .generatedBy(username)
            .build();
    }
    
    public static PdfReportMetadata createContractListReportMetadata(int count, String username) {
        return PdfReportMetadata.builder()
            .titleAr("قائمة العقود")
            .titleEn("Contracts List")
            .reportType(PdfReportMetadata.ReportType.CONTRACT)
            .orientation(PdfReportMetadata.PageOrientation.PORTRAIT)
            .generatedAt(LocalDateTime.now())
            .trackingId("CONTRACTS-LIST-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")))
            .generatedBy(username)
            .build();
    }
}
