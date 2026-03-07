package com.waad.tba.services.pdf.templates;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPTable;
import com.waad.tba.modules.member.dto.MemberResponseDto;
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
 * Member Report Template
 * 
 * Generates PDF reports for members using MemberResponseDto.
 * 
 * @since 2026-01-06
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SuppressWarnings("deprecation")
public class MemberReportTemplate {
    
    private final PdfFontConfig fontConfig;
    private final PdfTableBuilder tableBuilder;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    
    /**
     * Generate detail report for single member
     */
    public List<Element> generateMemberDetailReport(MemberResponseDto member) {
        List<Element> elements = new ArrayList<>();
        
        try {
            // Section: Personal Information
            Paragraph personalTitle = new Paragraph(
                "المعلومات الشخصية / Personal Information",
                fontConfig.getFont("المعلومات الشخصية", true)
            );
            personalTitle.setAlignment(Element.ALIGN_RIGHT);
            personalTitle.setSpacingBefore(10f);
            personalTitle.setSpacingAfter(10f);
            elements.add(personalTitle);
            
            List<PdfTableBuilder.KeyValue> personalInfo = new ArrayList<>();
            personalInfo.add(new PdfTableBuilder.KeyValue(
                "رقم البوليصة / Policy Number", 
                member.getPolicyNumber() != null ? member.getPolicyNumber() : "-"
            ));
            personalInfo.add(new PdfTableBuilder.KeyValue(
                "الاسم الكامل / Full Name", 
                member.getFullName()
            ));
            personalInfo.add(new PdfTableBuilder.KeyValue(
                "الهوية الوطنية / Civil ID", 
                member.getNationalNumber()
            ));
            personalInfo.add(new PdfTableBuilder.KeyValue(
                "تاريخ الميلاد / Date of Birth", 
                member.getBirthDate() != null ? member.getBirthDate().format(DATE_FORMATTER) : "-"
            ));
            personalInfo.add(new PdfTableBuilder.KeyValue(
                "الجنس / Gender", 
                member.getGender() != null ? member.getGender() : "-"
            ));
            
            PdfPTable personalTable = tableBuilder.buildKeyValueTable(personalInfo);
            elements.add(personalTable);
            
            // Section: Contact Information
            elements.add(new Paragraph(" "));
            
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
                "رقم الجوال / Phone", 
                member.getPhone()
            ));
            contactInfo.add(new PdfTableBuilder.KeyValue(
                "البريد الإلكتروني / Email", 
                member.getEmail() != null ? member.getEmail() : "-"
            ));
            
            PdfPTable contactTable = tableBuilder.buildKeyValueTable(contactInfo);
            elements.add(contactTable);
            
            // Section: Insurance Information
            elements.add(new Paragraph(" "));
            
            Paragraph insuranceTitle = new Paragraph(
                "معلومات التأمين / Insurance Information",
                fontConfig.getFont("معلومات التأمين", true)
            );
            insuranceTitle.setAlignment(Element.ALIGN_RIGHT);
            insuranceTitle.setSpacingBefore(10f);
            insuranceTitle.setSpacingAfter(10f);
            elements.add(insuranceTitle);
            
            List<PdfTableBuilder.KeyValue> insuranceInfo = new ArrayList<>();
            insuranceInfo.add(new PdfTableBuilder.KeyValue(
                "الحالة / Status", 
                member.getActive() != null && member.getActive() ? "نشط / Active" : "غير نشط / Inactive"
            ));
            insuranceInfo.add(new PdfTableBuilder.KeyValue(
                "جهة العمل / Employer", 
                member.getEmployerName() != null ? member.getEmployerName() : "-"
            ));
            insuranceInfo.add(new PdfTableBuilder.KeyValue(
                "رقم جهة العمل / Employer ID", 
                member.getEmployerId() != null ? member.getEmployerId().toString() : "-"
            ));
            insuranceInfo.add(new PdfTableBuilder.KeyValue(
                "تاريخ الإنشاء / Created At", 
                member.getCreatedAt() != null ? member.getCreatedAt().format(DATETIME_FORMATTER) : "-"
            ));
            insuranceInfo.add(new PdfTableBuilder.KeyValue(
                "آخر تحديث / Updated At", 
                member.getUpdatedAt() != null ? member.getUpdatedAt().format(DATETIME_FORMATTER) : "-"
            ));
            
            PdfPTable insuranceTable = tableBuilder.buildKeyValueTable(insuranceInfo);
            elements.add(insuranceTable);
            
        } catch (Exception e) {
            log.error("[MemberReportTemplate] Failed to generate member detail report", e);
            elements.add(new Paragraph("Error generating report", fontConfig.getLatinNormalFont()));
        }
        
        return elements;
    }
    
    /**
     * Generate list report for multiple members
     */
    public List<Element> generateMemberListReport(List<MemberResponseDto> members) {
        List<Element> elements = new ArrayList<>();
        
        try {
            Paragraph intro = new Paragraph(
                String.format("إجمالي الأعضاء: %d / Total Members: %d", members.size(), members.size()),
                fontConfig.getFont("إجمالي", false)
            );
            intro.setAlignment(Element.ALIGN_CENTER);
            intro.setSpacingAfter(20f);
            elements.add(intro);
            
            // Build simple list table
            for (MemberResponseDto member : members) {
                List<PdfTableBuilder.KeyValue> memberData = new ArrayList<>();
                memberData.add(new PdfTableBuilder.KeyValue("الاسم / Name", member.getFullName()));
                memberData.add(new PdfTableBuilder.KeyValue("الهوية / Civil ID", member.getNationalNumber()));
                memberData.add(new PdfTableBuilder.KeyValue("الجوال / Phone", member.getPhone()));
                memberData.add(new PdfTableBuilder.KeyValue("الحالة / Status", 
                    member.getActive() != null && member.getActive() ? "نشط" : "غير نشط"));
                
                PdfPTable table = tableBuilder.buildKeyValueTable(memberData);
                elements.add(table);
                elements.add(new Paragraph(" "));
            }
            
        } catch (Exception e) {
            log.error("[MemberReportTemplate] Failed to generate member list report", e);
            elements.add(new Paragraph("Error generating report", fontConfig.getLatinNormalFont()));
        }
        
        return elements;
    }
    
    /**
     * Create metadata for member detail report
     */
    public static PdfReportMetadata createMemberReportMetadata(
            String policyNumber, String username) {
        return PdfReportMetadata.builder()
            .titleAr("تقرير عضو")
            .titleEn("Member Report")
            .reportType(PdfReportMetadata.ReportType.MEMBER)
            .orientation(PdfReportMetadata.PageOrientation.PORTRAIT)
            .generatedAt(LocalDateTime.now())
            .trackingId("MEMBER-" + (policyNumber != null ? policyNumber : "UNKNOWN"))
            .generatedBy(username)
            .build();
    }
    
    /**
     * Create metadata for member list report
     */
    public static PdfReportMetadata createMemberListReportMetadata(
            int memberCount, String username) {
        return PdfReportMetadata.builder()
            .titleAr("قائمة الأعضاء")
            .titleEn("Members List")
            .reportType(PdfReportMetadata.ReportType.MEMBER)
            .orientation(PdfReportMetadata.PageOrientation.PORTRAIT)
            .generatedAt(LocalDateTime.now())
            .trackingId("MEMBERS-LIST-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")))
            .generatedBy(username)
            .build();
    }
}
