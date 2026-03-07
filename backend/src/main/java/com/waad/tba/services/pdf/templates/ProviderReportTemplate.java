package com.waad.tba.services.pdf.templates;

import com.lowagie.text.*;
import com.waad.tba.modules.provider.dto.ProviderResponseDto;
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
 * Provider Report Template
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SuppressWarnings("deprecation")
public class ProviderReportTemplate {
    
    private final PdfFontConfig fontConfig;
    private final PdfTableBuilder tableBuilder;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    
    public List<Element> generateProviderDetailReport(ProviderResponseDto provider) {
        List<Element> elements = new ArrayList<>();
        
        try {
            // Basic Information
            Paragraph basicTitle = new Paragraph(
                "المعلومات الأساسية / Basic Information",
                fontConfig.getFont("المعلومات الأساسية", true)
            );
            basicTitle.setAlignment(Element.ALIGN_RIGHT);
            basicTitle.setSpacingBefore(10f);
            basicTitle.setSpacingAfter(10f);
            elements.add(basicTitle);
            
            List<PdfTableBuilder.KeyValue> basicInfo = new ArrayList<>();
            basicInfo.add(new PdfTableBuilder.KeyValue(
                "اسم المقدم / Provider Name", provider.getName()));
            basicInfo.add(new PdfTableBuilder.KeyValue(
                "رقم الترخيص / License Number", provider.getLicenseNumber()));
            basicInfo.add(new PdfTableBuilder.KeyValue(
                "الرقم الضريبي / Tax Number", 
                provider.getTaxNumber() != null ? provider.getTaxNumber() : "-"));
            basicInfo.add(new PdfTableBuilder.KeyValue(
                "نوع المقدم / Provider Type", 
                provider.getProviderTypeLabel() != null ? provider.getProviderTypeLabel() : "-"));
            basicInfo.add(new PdfTableBuilder.KeyValue(
                "حالة الشبكة / Network Status", 
                provider.getNetworkStatusLabel() != null ? provider.getNetworkStatusLabel() : "-"));
            basicInfo.add(new PdfTableBuilder.KeyValue(
                "الحالة / Status", 
                provider.getActive() != null && provider.getActive() ? "نشط / Active" : "غير نشط / Inactive"));
            
            elements.add(tableBuilder.buildKeyValueTable(basicInfo));
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
                "المدينة / City", provider.getCity() != null ? provider.getCity() : "-"));
            contactInfo.add(new PdfTableBuilder.KeyValue(
                "العنوان / Address", provider.getAddress() != null ? provider.getAddress() : "-"));
            contactInfo.add(new PdfTableBuilder.KeyValue(
                "الهاتف / Phone", provider.getPhone()));
            contactInfo.add(new PdfTableBuilder.KeyValue(
                "البريد الإلكتروني / Email", 
                provider.getEmail() != null ? provider.getEmail() : "-"));
            
            elements.add(tableBuilder.buildKeyValueTable(contactInfo));
            elements.add(new Paragraph(" "));
            
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
                "تاريخ بدء العقد / Contract Start", 
                provider.getContractStartDate() != null ? provider.getContractStartDate().format(DATE_FORMATTER) : "-"));
            contractInfo.add(new PdfTableBuilder.KeyValue(
                "تاريخ انتهاء العقد / Contract End", 
                provider.getContractEndDate() != null ? provider.getContractEndDate().format(DATE_FORMATTER) : "-"));
            contractInfo.add(new PdfTableBuilder.KeyValue(
                "نسبة الخصم الافتراضية / Default Discount", 
                provider.getDefaultDiscountRate() != null ? provider.getDefaultDiscountRate().toString() + "%" : "-"));
            contractInfo.add(new PdfTableBuilder.KeyValue(
                "تاريخ الإنشاء / Created At", 
                provider.getCreatedAt() != null ? provider.getCreatedAt().format(DATETIME_FORMATTER) : "-"));
            
            elements.add(tableBuilder.buildKeyValueTable(contractInfo));
            
        } catch (Exception e) {
            log.error("[ProviderReportTemplate] Failed to generate provider detail report", e);
            elements.add(new Paragraph("Error generating report", fontConfig.getLatinNormalFont()));
        }
        
        return elements;
    }
    
    public List<Element> generateProviderListReport(List<ProviderResponseDto> providers) {
        List<Element> elements = new ArrayList<>();
        
        try {
            Paragraph intro = new Paragraph(
                String.format("إجمالي مقدمي الخدمة: %d / Total Providers: %d", providers.size(), providers.size()),
                fontConfig.getFont("إجمالي", false)
            );
            intro.setAlignment(Element.ALIGN_CENTER);
            intro.setSpacingAfter(20f);
            elements.add(intro);
            
            for (ProviderResponseDto provider : providers) {
                List<PdfTableBuilder.KeyValue> providerData = new ArrayList<>();
                providerData.add(new PdfTableBuilder.KeyValue("الاسم / Name", provider.getName()));
                providerData.add(new PdfTableBuilder.KeyValue("الترخيص / License", provider.getLicenseNumber()));
                providerData.add(new PdfTableBuilder.KeyValue("الهاتف / Phone", provider.getPhone()));
                providerData.add(new PdfTableBuilder.KeyValue("الحالة / Status", 
                    provider.getActive() != null && provider.getActive() ? "نشط" : "غير نشط"));
                
                elements.add(tableBuilder.buildKeyValueTable(providerData));
                elements.add(new Paragraph(" "));
            }
            
        } catch (Exception e) {
            log.error("[ProviderReportTemplate] Failed to generate provider list report", e);
            elements.add(new Paragraph("Error generating report", fontConfig.getLatinNormalFont()));
        }
        
        return elements;
    }
    
    public static PdfReportMetadata createProviderReportMetadata(String licenseNumber, String username) {
        return PdfReportMetadata.builder()
            .titleAr("تقرير مقدم خدمة")
            .titleEn("Provider Report")
            .reportType(PdfReportMetadata.ReportType.PROVIDER)
            .orientation(PdfReportMetadata.PageOrientation.PORTRAIT)
            .generatedAt(LocalDateTime.now())
            .trackingId("PROVIDER-" + (licenseNumber != null ? licenseNumber : "UNKNOWN"))
            .generatedBy(username)
            .build();
    }
    
    public static PdfReportMetadata createProviderListReportMetadata(int count, String username) {
        return PdfReportMetadata.builder()
            .titleAr("قائمة مقدمي الخدمة")
            .titleEn("Providers List")
            .reportType(PdfReportMetadata.ReportType.PROVIDER)
            .orientation(PdfReportMetadata.PageOrientation.PORTRAIT)
            .generatedAt(LocalDateTime.now())
            .trackingId("PROVIDERS-LIST-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")))
            .generatedBy(username)
            .build();
    }
}
