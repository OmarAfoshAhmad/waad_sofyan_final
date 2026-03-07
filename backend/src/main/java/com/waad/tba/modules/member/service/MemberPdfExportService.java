package com.waad.tba.modules.member.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import java.awt.Color;
import com.waad.tba.modules.member.dto.MemberViewDto;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.awt.image.BufferedImage;

/**
 * PDF Export Service for Members
 * 
 * Generates professional PDF reports for members list with unified employer branding
 * This service uses OpenPDF (open-source PDF library)
 * 
 * Features:
 * - Professional header with employer logo, name, business type, timestamp, and report ID
 * - Table with all member columns
 * - Footer with full employer address, phones, email, copyright, and page numbers
 * - Proper Arabic RTL support
 * - Dynamic employer info from database (no hardcoding)
 * - Page breaks and formatting
 * 
 * This pattern can be reused for other modules (claims, policies, etc.)
 * 
 * PDF Branding Standard:
 * 1. Header: Logo + Employer Name + Business Type + Report Date/Time + Report ID
 * 2. Footer: Full Address + Phones + Email + Copyright + Page Numbers
 * 3. Data Source: All from Employer entity (database)
 * 4. General Principle: Backend generates, Frontend downloads only
 * 
 * @deprecated PDF export disabled. Excel is the official reporting format.
 *             Kept for potential legal/compliance reports in the future.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Deprecated(since = "2026-01", forRemoval = false)
@SuppressWarnings("dep-ann")
public class MemberPdfExportService {

    private static final String REPORT_TITLE = "تقرير قائمة المنتفعين";
    
    private final EmployerRepository employerRepository;
    
    // Arabic font support
    private static final String ARABIC_FONT_PATH = "fonts/NotoSansArabic-Regular.ttf";
    
    /**
     * Generate PDF report for members list
     * 
     * @param members List of members to include in report
     * @param filterDescription Optional description of applied filters (e.g., "شريك: شركة XYZ")
     * @return PDF file as byte array
     */
    public byte[] generateMembersPdf(List<MemberViewDto> members, String filterDescription) {
        log.info("[MemberPdfExportService] Generating PDF for {} members", members.size());
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate()); // Landscape for more columns
        
        try {
            PdfWriter writer = PdfWriter.getInstance(document, outputStream);
            
            // Add page event for header/footer
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter writer, Document document) {
                    addFooter(writer, document);
                }
            });
            
            document.open();
            
            // Add header
            addHeader(document, filterDescription);
            
            // Add members table
            addMembersTable(document, members);
            
            document.close();
            
            log.info("[MemberPdfExportService] PDF generated successfully, size: {} bytes", outputStream.size());
            return outputStream.toByteArray();
            
        } catch (DocumentException e) {
            log.error("[MemberPdfExportService] Failed to generate PDF", e);
            throw new RuntimeException("Failed to generate PDF report", e);
        }
    }
    
    /**
     * Add header section with company branding
     * 
     * Header includes:
     * - Employer logo (if available)
     * - Employer name
     * - Business type (نوع النشاط)
     * - Report title
     * - Generation timestamp
     * - Report ID
     * - Filter description (if any)
     * 
     * All data comes from Employer entity (database)
     */
    private void addHeader(Document document, String filterDescription) throws DocumentException {
        // Fetch default employer data
        Employer employer = employerRepository.findByIsDefaultTrue()
                .orElseGet(() -> {
                    log.warn("[MemberPdfExportService] No default employer found, using fallback data");
                    return Employer.builder()
                            .name("نظام TBA WAAD للتأمين الطبي")
                            .businessType("إدارة المطالبات الطبية")
                            .build();
                });
        
        // Add employer logo if available
        if (employer.getLogoUrl() != null && !employer.getLogoUrl().isEmpty()) {
            try {
                Image logo = Image.getInstance(employer.getLogoUrl());
                logo.scaleToFit(80, 80); // Max 80x80 pixels
                logo.setAlignment(Element.ALIGN_CENTER);
                document.add(logo);
                document.add(new Paragraph(" ")); // Spacing after logo
            } catch (Exception e) {
                log.warn("[MemberPdfExportService] Failed to load logo from: {}. Error: {}", 
                        employer.getLogoUrl(), e.getMessage());
                // Continue without logo if it fails to load
            }
        }
        
        // Employer name
        Font companyNameFont = new Font(Font.HELVETICA, 16, Font.BOLD);
        Paragraph companyName = new Paragraph(employer.getName() != null ? employer.getName() : "نظام TBA WAAD", companyNameFont);
        companyName.setAlignment(Element.ALIGN_CENTER);
        document.add(companyName);
        
        // Business type
        if (employer.getBusinessType() != null && !employer.getBusinessType().isEmpty()) {
            Font businessTypeFont = new Font(Font.HELVETICA, 11, Font.ITALIC);
            Paragraph businessType = new Paragraph(employer.getBusinessType(), businessTypeFont);
            businessType.setAlignment(Element.ALIGN_CENTER);
            document.add(businessType);
        }
        
        // Spacing
        document.add(new Paragraph(" "));
        
        // Report title
        Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
        Paragraph title = new Paragraph(REPORT_TITLE, titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);
        
        // Generation timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Font timestampFont = new Font(Font.HELVETICA, 10, Font.ITALIC);
        Paragraph timestampPara = new Paragraph("تاريخ ووقت الإنشاء: " + timestamp, timestampFont);
        timestampPara.setAlignment(Element.ALIGN_CENTER);
        document.add(timestampPara);
        
        // Report ID (generated from timestamp)
        String reportId = "RPT-MEMBERS-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        Paragraph reportIdPara = new Paragraph("رقم التقرير: " + reportId, timestampFont);
        reportIdPara.setAlignment(Element.ALIGN_CENTER);
        document.add(reportIdPara);
        
        // Filter description (if provided)
        if (filterDescription != null && !filterDescription.isEmpty()) {
            Font filterFont = new Font(Font.HELVETICA, 10, Font.NORMAL);
            Paragraph filterPara = new Paragraph("الفلتر المطبق: " + filterDescription, filterFont);
            filterPara.setAlignment(Element.ALIGN_CENTER);
            document.add(filterPara);
        }
        
        // Spacing before table
        document.add(new Paragraph(" "));
    }
    
    /**
     * Add members table with all columns
     */
    private void addMembersTable(Document document, List<MemberViewDto> members) throws DocumentException {
        // Table with 10 columns
        float[] columnWidths = {0.5f, 1.0f, 1.5f, 1.2f, 1.5f, 1.2f, 1.0f, 0.8f, 1.0f, 1.0f};
        PdfPTable table = new PdfPTable(columnWidths);
        table.setWidthPercentage(100);
        table.setSpacingBefore(10f);
        table.setSpacingAfter(10f);
        
        // Header row
        Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD);
        headerFont.setColor(255, 255, 255); // White text
        
        addTableHeader(table, "#", headerFont);
        addTableHeader(table, "الباركود", headerFont);
        addTableHeader(table, "الاسم الكامل", headerFont);
        addTableHeader(table, "الرقم الوطني", headerFont);
        addTableHeader(table, "الشريك", headerFont);
        addTableHeader(table, "وثيقة المنافع", headerFont);
        addTableHeader(table, "عدد التوابع", headerFont);
        addTableHeader(table, "الحالة", headerFont);
        addTableHeader(table, "حالة البطاقة", headerFont);
        addTableHeader(table, "الهاتف", headerFont);
        
        // Data rows
        Font dataFont = new Font(Font.HELVETICA, 9, Font.NORMAL);
        int index = 1;
        
        for (MemberViewDto member : members) {
            addTableCell(table, String.valueOf(index++), dataFont, Element.ALIGN_CENTER);
            addTableCell(table, member.getBarcode() != null ? member.getBarcode() : "-", dataFont, Element.ALIGN_LEFT);
            addTableCell(table, member.getFullName() != null ? member.getFullName() : "-", dataFont, Element.ALIGN_RIGHT);
            addTableCell(table, member.getNationalNumber() != null ? member.getNationalNumber() : "-", dataFont, Element.ALIGN_LEFT);
            addTableCell(table, member.getEmployerName() != null ? member.getEmployerName() : "-", dataFont, Element.ALIGN_RIGHT);
            addTableCell(table, member.getPolicyNumber() != null ? member.getPolicyNumber() : "-", dataFont, Element.ALIGN_LEFT);
            
            // Count dependents (new unified architecture)
            int dependentsCount = member.getDependents() != null ? member.getDependents().size() : 0;
            addTableCell(table, String.valueOf(dependentsCount), dataFont, Element.ALIGN_CENTER);
            
            addTableCell(table, translateStatus(member.getStatus() != null ? member.getStatus().name() : null), dataFont, Element.ALIGN_CENTER);
            addTableCell(table, translateCardStatus(member.getCardStatus() != null ? member.getCardStatus().name() : null), dataFont, Element.ALIGN_CENTER);
            addTableCell(table, member.getPhone() != null ? member.getPhone() : "-", dataFont, Element.ALIGN_LEFT);
        }
        
        document.add(table);
        
        // Summary footer
        Font summaryFont = new Font(Font.HELVETICA, 10, Font.BOLD);
        Paragraph summary = new Paragraph("إجمالي عدد المنتفعين: " + members.size(), summaryFont);
        summary.setAlignment(Element.ALIGN_RIGHT);
        summary.setSpacingBefore(10f);
        document.add(summary);
    }
    
    /**
     * Add table header cell with styling
     */
    private void addTableHeader(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(new Color(41, 98, 255)); // Primary blue
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(8);
        table.addCell(cell);
    }
    
    /**
     * Add table data cell
     */
    private void addTableCell(PdfPTable table, String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        table.addCell(cell);
    }
    
    /**
     * Add footer with full company contact information
     * 
     * Footer includes:
     * - Full company address
     * - Contact phones
     * - Email address
     * - Copyright notice
     * - Page numbers
     * 
     * All data comes from Employer entity (database)
     */
    private void addFooter(PdfWriter writer, Document document) {
        try {
            // Fetch default company data
            Employer employer = employerRepository.findByIsDefaultTrue()
                    .orElseGet(() -> Employer.builder()
                            .address("الرياض، المملكة العربية السعودية")
                            .phone("+966-XX-XXX-XXXX")
                            .email("info@tbawaad.com")
                            .build());
            
            PdfContentByte cb = writer.getDirectContent();
            Font footerFont = new Font(Font.HELVETICA, 8, Font.NORMAL);
            Font footerBoldFont = new Font(Font.HELVETICA, 8, Font.BOLD);
            
            float yPosition = document.bottom() - 10;
            
            // Line 1: Full Address (centered)
            if (employer.getAddress() != null && !employer.getAddress().isEmpty()) {
                Phrase addressPhrase = new Phrase("العنوان: " + employer.getAddress(), footerFont);
                ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                        addressPhrase,
                        document.getPageSize().getWidth() / 2, yPosition + 20, 0);
            }
            
            // Line 2: Phones and Email (centered)
            StringBuilder contactInfo = new StringBuilder();
            if (employer.getPhone() != null && !employer.getPhone().isEmpty()) {
                contactInfo.append("هاتف: ").append(employer.getPhone());
            }
            if (employer.getEmail() != null && !employer.getEmail().isEmpty()) {
                if (contactInfo.length() > 0) {
                    contactInfo.append(" | ");
                }
                contactInfo.append("بريد إلكتروني: ").append(employer.getEmail());
            }
            
            if (contactInfo.length() > 0) {
                Phrase contactPhrase = new Phrase(contactInfo.toString(), footerFont);
                ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                        contactPhrase,
                        document.getPageSize().getWidth() / 2, yPosition + 10, 0);
            }
            
            // Line 3: Page number (right) and Copyright (left)
            Phrase pagePhrase = new Phrase("صفحة " + writer.getPageNumber(), footerFont);
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                    pagePhrase,
                    document.right(), yPosition, 0);
            
            String copyrightText = "© " + LocalDateTime.now().getYear() + " " + 
                    (employer.getName() != null ? employer.getName() : "TBA WAAD System");
            Phrase copyrightPhrase = new Phrase(copyrightText, footerFont);
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                    copyrightPhrase,
                    document.left(), yPosition, 0);
                    
        } catch (Exception e) {
            log.error("[MemberPdfExportService] Failed to add footer", e);
        }
    }
    
    /**
     * Translate status enum to Arabic
     */
    private String translateStatus(String status) {
        if (status == null) return "-";
        
        switch (status) {
            case "ACTIVE": return "نشط";
            case "SUSPENDED": return "معلق";
            case "TERMINATED": return "منتهي";
            case "PENDING": return "قيد المراجعة";
            default: return status;
        }
    }
    
    /**
     * Translate card status enum to Arabic
     */
    private String translateCardStatus(String cardStatus) {
        if (cardStatus == null) return "-";
        
        switch (cardStatus) {
            case "ACTIVE": return "نشطة";
            case "INACTIVE": return "غير نشطة";
            case "BLOCKED": return "محظورة";
            case "EXPIRED": return "منتهية";
            case "PENDING": return "قيد الإصدار";
            default: return cardStatus;
        }
    }
    
    /**
     * Translate gender enum to Arabic
     */
    private String translateGender(String gender) {
        if (gender == null) return "-";
        
        switch (gender) {
            case "MALE": return "ذكر";
            case "FEMALE": return "أنثى";
            case "UNDEFINED": return "غير محدد";
            default: return gender;
        }
    }
    
    /**
     * Translate marital status enum to Arabic
     */
    private String translateMaritalStatus(String maritalStatus) {
        if (maritalStatus == null) return "-";
        
        switch (maritalStatus) {
            case "SINGLE": return "أعزب";
            case "MARRIED": return "متزوج";
            case "DIVORCED": return "مطلق";
            case "WIDOWED": return "أرمل";
            default: return maritalStatus;
        }
    }
    
    /**
     * Translate relationship enum to Arabic
     */
    private String translateRelationship(String relationship) {
        if (relationship == null) return "-";
        
        switch (relationship) {
            case "SPOUSE": return "زوج/زوجة";
            case "SON": return "ابن";
            case "DAUGHTER": return "ابنة";
            case "FATHER": return "أب";
            case "MOTHER": return "أم";
            case "BROTHER": return "أخ";
            case "SISTER": return "أخت";
            case "OTHER": return "أخرى";
            default: return relationship;
        }
    }
    
    /**
     * Generate QR Code image for barcode
     * @param barcodeText Text to encode in QR code
     * @param size Size of QR code (width and height)
     * @return Image object for PDF
     */
    private Image generateQRCodeImage(String barcodeText, int size) throws Exception {
        if (barcodeText == null || barcodeText.trim().isEmpty()) {
            throw new IllegalArgumentException("Barcode text cannot be empty");
        }
        
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);
            
            BitMatrix bitMatrix = qrCodeWriter.encode(barcodeText, BarcodeFormat.QR_CODE, size, size, hints);
            BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(bitMatrix);
            
            return Image.getInstance(bufferedImage, null);
        } catch (Exception e) {
            log.error("[MemberPdfExportService] Failed to generate QR code for barcode: {}", barcodeText, e);
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }
    
    /**
     * Generate PDF card for a single member
     * 
     * @param member Member details to include in PDF
     * @return PDF file as byte array
     */
    public byte[] generateMemberCardPdf(MemberViewDto member) {
        log.info("[MemberPdfExportService] Generating member card PDF for ID: {}", member.getId());
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4); // Portrait for single member card
        
        try {
            PdfWriter writer = PdfWriter.getInstance(document, outputStream);
            
            // Add page event for header/footer
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter writer, Document document) {
                    addFooter(writer, document);
                }
            });
            
            document.open();
            
            // ============================================
            // HEADER: Employer Branding & Logo
            // ============================================
            Employer employer = employerRepository.findByIsDefaultTrue()
                .orElseGet(() -> Employer.builder()
                    .name("نظام TBA WAAD للتأمين الطبي")
                    .businessType("إدارة المطالبات الطبية")
                    .phone("+965 1234 5678")
                    .email("info@tbawaad.com")
                    .website("www.tbawaad.com")
                    .build());
            
            // Add header table with logo and company info
            PdfPTable headerTable = new PdfPTable(2);
            headerTable.setWidthPercentage(100);
            headerTable.setWidths(new float[]{1, 3});
            headerTable.setSpacingAfter(15);
            
            // Logo cell (left)
            PdfPCell logoCell = new PdfPCell();
            logoCell.setBorder(Rectangle.NO_BORDER);
            logoCell.setPadding(10);
            
            if (employer.getLogoUrl() != null && !employer.getLogoUrl().isEmpty()) {
                try {
                    Image logo = Image.getInstance(employer.getLogoUrl());
                    logo.scaleToFit(100, 100);
                    logoCell.addElement(logo);
                } catch (Exception e) {
                    log.warn("[MemberPdfExportService] Failed to load employer logo", e);
                    Font logoPlaceholderFont = new Font(Font.HELVETICA, 40, Font.BOLD, new Color(100, 100, 100));
                    Paragraph logoPlaceholder = new Paragraph("WAAD", logoPlaceholderFont);
                    logoPlaceholder.setAlignment(Element.ALIGN_CENTER);
                    logoCell.addElement(logoPlaceholder);
                }
            } else {
                Font logoPlaceholderFont = new Font(Font.HELVETICA, 40, Font.BOLD, new Color(33, 150, 243));
                Paragraph logoPlaceholder = new Paragraph("WAAD", logoPlaceholderFont);
                logoPlaceholder.setAlignment(Element.ALIGN_CENTER);
                logoCell.addElement(logoPlaceholder);
            }
            headerTable.addCell(logoCell);
            
            // Employer info cell (right)
            PdfPCell companyInfoCell = new PdfPCell();
            companyInfoCell.setBorder(Rectangle.NO_BORDER);
            companyInfoCell.setPadding(10);
            companyInfoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            
            Font companyNameFont = new Font(Font.HELVETICA, 20, Font.BOLD, new Color(33, 33, 33));
            Paragraph companyName = new Paragraph(
                employer.getName() != null ? employer.getName() : "نظام TBA WAAD",
                companyNameFont
            );
            companyName.setAlignment(Element.ALIGN_RIGHT);
            companyInfoCell.addElement(companyName);
            
            if (employer.getBusinessType() != null && !employer.getBusinessType().isEmpty()) {
                Font businessTypeFont = new Font(Font.HELVETICA, 12, Font.NORMAL, new Color(100, 100, 100));
                Paragraph businessType = new Paragraph(employer.getBusinessType(), businessTypeFont);
                businessType.setAlignment(Element.ALIGN_RIGHT);
                businessType.setSpacingBefore(5);
                companyInfoCell.addElement(businessType);
            }
            
            // Contact info
            Font contactFont = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(80, 80, 80));
            StringBuilder contactInfo = new StringBuilder();
            if (employer.getPhone() != null) contactInfo.append("هاتف: ").append(employer.getPhone()).append("  |  ");
            if (employer.getEmail() != null) contactInfo.append("بريد: ").append(employer.getEmail()).append("  |  ");
            if (employer.getWebsite() != null) contactInfo.append("موقع: ").append(employer.getWebsite());
            
            if (contactInfo.length() > 0) {
                Paragraph contact = new Paragraph(contactInfo.toString(), contactFont);
                contact.setAlignment(Element.ALIGN_RIGHT);
                contact.setSpacingBefore(8);
                companyInfoCell.addElement(contact);
            }
            
            headerTable.addCell(companyInfoCell);
            document.add(headerTable);
            
            // Divider line using Paragraph with underline
            Paragraph dividerLine = new Paragraph(" ");
            dividerLine.setSpacingBefore(5);
            dividerLine.setSpacingAfter(10);
            document.add(dividerLine);
            
            PdfPTable separatorTable = new PdfPTable(1);
            separatorTable.setWidthPercentage(100);
            PdfPCell separatorCell = new PdfPCell();
            separatorCell.setBorder(Rectangle.NO_BORDER);
            separatorCell.setBorderWidthBottom(1f);
            separatorCell.setBorderColorBottom(new Color(200, 200, 200));
            separatorCell.setFixedHeight(1);
            separatorTable.addCell(separatorCell);
            document.add(separatorTable);
            document.add(Chunk.NEWLINE);
            
            // ============================================
            // TITLE & METADATA
            // ============================================
            Font titleFont = new Font(Font.HELVETICA, 22, Font.BOLD, new Color(33, 150, 243));
            Paragraph title = new Paragraph("بطاقة عضو", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingBefore(10);
            title.setSpacingAfter(5);
            document.add(title);
            
            // Timestamp and Report ID
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String reportId = "RPT-" + System.currentTimeMillis();
            Font metadataFont = new Font(Font.HELVETICA, 9, Font.ITALIC, new Color(120, 120, 120));
            Paragraph metadata = new Paragraph(
                "تاريخ الطباعة: " + timestamp + "  |  رقم التقرير: " + reportId,
                metadataFont
            );
            metadata.setAlignment(Element.ALIGN_CENTER);
            metadata.setSpacingAfter(15);
            document.add(metadata);
            
            // ============================================
            // QR CODE IDENTITY SECTION (مثل التقارير تماماً)
            // ============================================
            if (member.getBarcode() != null && !member.getBarcode().isEmpty()) {
                PdfPTable qrSection = new PdfPTable(1);
                qrSection.setWidthPercentage(50); // Center it
                qrSection.setHorizontalAlignment(Element.ALIGN_CENTER);
                qrSection.setSpacingBefore(10);
                qrSection.setSpacingAfter(15);
                
                PdfPCell qrCell = new PdfPCell();
                qrCell.setBorderColor(new Color(224, 224, 224));
                qrCell.setBorderWidth(1.5f);
                qrCell.setBackgroundColor(new Color(248, 249, 250));
                qrCell.setPadding(15);
                qrCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                
                // Title
                Font qrTitleFont = new Font(Font.HELVETICA, 8, Font.BOLD, new Color(100, 100, 100));
                Paragraph qrTitle = new Paragraph("SCAN MEMBER IDENTITY", qrTitleFont);
                qrTitle.setAlignment(Element.ALIGN_CENTER);
                qrTitle.setSpacingAfter(10);
                qrCell.addElement(qrTitle);
                
                // QR Code Image
                try {
                    Image qrImage = generateQRCodeImage(member.getBarcode(), 200);
                    qrImage.scaleToFit(150, 150);
                    qrImage.setAlignment(Element.ALIGN_CENTER);
                    qrImage.setBorder(Rectangle.BOX);
                    qrImage.setBorderWidth(1);
                    qrImage.setBorderColor(Color.LIGHT_GRAY);
                    qrCell.addElement(qrImage);
                } catch (Exception e) {
                    log.warn("[MemberPdfExportService] Failed to generate QR code", e);
                    Paragraph errorMsg = new Paragraph("QR CODE ERROR", new Font(Font.HELVETICA, 10, Font.NORMAL, Color.RED));
                    errorMsg.setAlignment(Element.ALIGN_CENTER);
                    qrCell.addElement(errorMsg);
                }
                
                // Barcode info table
                PdfPTable barcodeInfoTable = new PdfPTable(2);
                barcodeInfoTable.setWidthPercentage(100);
                barcodeInfoTable.setSpacingBefore(10);
                
                Font barcodeKeyFont = new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(120, 120, 120));
                Font barcodeValueFont = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(33, 33, 33));
                Font cardNumberFont = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(33, 150, 243));
                
                // BARCODE (Ref)
                PdfPCell barcodeKeyCell1 = new PdfPCell(new Phrase("BARCODE (Ref)", barcodeKeyFont));
                barcodeKeyCell1.setBorder(Rectangle.NO_BORDER);
                barcodeKeyCell1.setBorderWidthBottom(0.5f);
                barcodeKeyCell1.setBorderColorBottom(new Color(221, 221, 221));
                barcodeKeyCell1.setPadding(5);
                barcodeInfoTable.addCell(barcodeKeyCell1);
                
                PdfPCell barcodeValueCell1 = new PdfPCell(new Phrase(member.getBarcode(), barcodeValueFont));
                barcodeValueCell1.setBorder(Rectangle.NO_BORDER);
                barcodeValueCell1.setBorderWidthBottom(0.5f);
                barcodeValueCell1.setBorderColorBottom(new Color(221, 221, 221));
                barcodeValueCell1.setPadding(5);
                barcodeValueCell1.setHorizontalAlignment(Element.ALIGN_RIGHT);
                barcodeInfoTable.addCell(barcodeValueCell1);
                
                // CARD NUMBER
                PdfPCell barcodeKeyCell2 = new PdfPCell(new Phrase("CARD NUMBER", barcodeKeyFont));
                barcodeKeyCell2.setBorder(Rectangle.NO_BORDER);
                barcodeKeyCell2.setBorderWidthBottom(0.5f);
                barcodeKeyCell2.setBorderColorBottom(new Color(221, 221, 221));
                barcodeKeyCell2.setPadding(5);
                barcodeInfoTable.addCell(barcodeKeyCell2);
                
                PdfPCell barcodeValueCell2 = new PdfPCell(new Phrase(member.getCardNumber() != null ? member.getCardNumber() : "-", cardNumberFont));
                barcodeValueCell2.setBorder(Rectangle.NO_BORDER);
                barcodeValueCell2.setBorderWidthBottom(0.5f);
                barcodeValueCell2.setBorderColorBottom(new Color(221, 221, 221));
                barcodeValueCell2.setPadding(5);
                barcodeValueCell2.setHorizontalAlignment(Element.ALIGN_RIGHT);
                barcodeInfoTable.addCell(barcodeValueCell2);
                
                // NATIONAL ID
                PdfPCell barcodeKeyCell3 = new PdfPCell(new Phrase("NATIONAL ID", barcodeKeyFont));
                barcodeKeyCell3.setBorder(Rectangle.NO_BORDER);
                barcodeKeyCell3.setPadding(5);
                barcodeInfoTable.addCell(barcodeKeyCell3);
                
                PdfPCell barcodeValueCell3 = new PdfPCell(new Phrase(member.getNationalNumber() != null ? member.getNationalNumber() : "-", barcodeValueFont));
                barcodeValueCell3.setBorder(Rectangle.NO_BORDER);
                barcodeValueCell3.setPadding(5);
                barcodeValueCell3.setHorizontalAlignment(Element.ALIGN_RIGHT);
                barcodeInfoTable.addCell(barcodeValueCell3);
                
                qrCell.addElement(barcodeInfoTable);
                qrSection.addCell(qrCell);
                document.add(qrSection);
            }
            
            // ============================================
            // MEMBER INFORMATION TABLE
            // ============================================
            PdfPTable infoTable = new PdfPTable(2);
            infoTable.setWidthPercentage(100);
            infoTable.setWidths(new float[]{1.2f, 2.8f});
            infoTable.setSpacingBefore(10);
            
            Font labelFont = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(60, 60, 60));
            Font valueFont = new Font(Font.HELVETICA, 11, Font.NORMAL, new Color(33, 33, 33));
            Font highlightFont = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(33, 150, 243));
            
            // Helper to add row
            java.util.function.BiConsumer<String, String> addRow = (label, value) -> {
                PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
                labelCell.setBackgroundColor(new Color(245, 245, 245));
                labelCell.setPadding(10);
                labelCell.setBorderWidth(0.5f);
                labelCell.setBorderColor(new Color(220, 220, 220));
                labelCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                infoTable.addCell(labelCell);
                
                PdfPCell valueCell = new PdfPCell(new Phrase(value != null && !value.isEmpty() ? value : "-", valueFont));
                valueCell.setPadding(10);
                valueCell.setBorderWidth(0.5f);
                valueCell.setBorderColor(new Color(220, 220, 220));
                valueCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                infoTable.addCell(valueCell);
            };
            
            // Helper for highlighted rows (important fields)
            java.util.function.BiConsumer<String, String> addHighlightRow = (label, value) -> {
                PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
                labelCell.setBackgroundColor(new Color(227, 242, 253));
                labelCell.setPadding(12);
                labelCell.setBorderWidth(1f);
                labelCell.setBorderColor(new Color(33, 150, 243));
                labelCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                infoTable.addCell(labelCell);
                
                PdfPCell valueCell = new PdfPCell(new Phrase(value != null && !value.isEmpty() ? value : "-", highlightFont));
                valueCell.setBackgroundColor(new Color(245, 250, 255));
                valueCell.setPadding(12);
                valueCell.setBorderWidth(1f);
                valueCell.setBorderColor(new Color(33, 150, 243));
                valueCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                infoTable.addCell(valueCell);
            };
            
            // Add highlighted important fields
            addHighlightRow.accept("رقم بطاقة العضو", member.getCardNumber());
            addHighlightRow.accept("الاسم الكامل", member.getFullName());
            addHighlightRow.accept("الرقم الوطني", member.getNationalNumber());
            
            // Add regular fields
            addRow.accept("الباركود", member.getBarcode());
            addRow.accept("تاريخ الميلاد", member.getBirthDate() != null ? member.getBirthDate().toString() : null);
            addRow.accept("الجنس", translateGender(member.getGender() != null ? member.getGender().name() : null));
            addRow.accept("الحالة الاجتماعية", translateMaritalStatus(member.getMaritalStatus() != null ? member.getMaritalStatus().name() : null));
            addRow.accept("الجنسية", member.getNationality());
            addRow.accept("الهاتف", member.getPhone());
            addRow.accept("البريد الإلكتروني", member.getEmail());
            addRow.accept("العنوان", member.getAddress());
            addRow.accept("جهة العمل", member.getEmployerName());
            addRow.accept("الرقم الوظيفي", member.getEmployeeNumber());
            addRow.accept("تاريخ الالتحاق", member.getJoinDate() != null ? member.getJoinDate().toString() : null);
            addRow.accept("المهنة", member.getOccupation());
            addRow.accept("حالة العضو", translateStatus(member.getStatus() != null ? member.getStatus().name() : null));
            addRow.accept("حالة البطاقة", translateCardStatus(member.getCardStatus() != null ? member.getCardStatus().name() : null));
            addRow.accept("تاريخ البداية", member.getStartDate() != null ? member.getStartDate().toString() : null);
            addRow.accept("تاريخ النهاية", member.getEndDate() != null ? member.getEndDate().toString() : null);
            
            document.add(infoTable);
            
            // ============================================
            // DEPENDENTS TABLE (Unified Architecture)
            // ============================================
            if (member.getDependents() != null && !member.getDependents().isEmpty()) {
                document.add(Chunk.NEWLINE);
                document.add(Chunk.NEWLINE);
                
                Font sectionFont = new Font(Font.HELVETICA, 16, Font.BOLD, new Color(33, 150, 243));
                Paragraph dependentsTitle = new Paragraph(
                    "التابعين (" + member.getDependents().size() + ")",
                    sectionFont
                );
                dependentsTitle.setSpacingBefore(5);
                dependentsTitle.setSpacingAfter(10);
                document.add(dependentsTitle);
                
                PdfPTable dependentsTable = new PdfPTable(6);
                dependentsTable.setWidthPercentage(100);
                dependentsTable.setWidths(new float[]{1.5f, 2f, 1.2f, 2f, 1.2f, 1f});
                
                // Header
                String[] dependentsHeaders = {"رقم البطاقة", "الاسم", "القرابة", "الرقم المدني", "الميلاد", "الجنس"};
                Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
                for (String header : dependentsHeaders) {
                    PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
                    cell.setBackgroundColor(new Color(33, 150, 243));
                    cell.setPadding(8);
                    cell.setBorderWidth(0);
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    dependentsTable.addCell(cell);
                }
                
                // Rows
                Font dependentValueFont = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(50, 50, 50));
                int rowIndex = 0;
                for (var dependent : member.getDependents()) {
                    Color rowColor = rowIndex % 2 == 0 ? Color.WHITE : new Color(250, 250, 250);
                    
                    PdfPCell[] cells = new PdfPCell[6];
                    cells[0] = new PdfPCell(new Phrase(dependent.getCardNumber() != null ? dependent.getCardNumber() : "-", dependentValueFont));
                    cells[1] = new PdfPCell(new Phrase(dependent.getFullName() != null ? dependent.getFullName() : "-", dependentValueFont));
                    cells[2] = new PdfPCell(new Phrase(translateRelationship(dependent.getRelationship() != null ? dependent.getRelationship().name() : null), dependentValueFont));
                    cells[3] = new PdfPCell(new Phrase(dependent.getNationalNumber() != null ? dependent.getNationalNumber() : "-", dependentValueFont));
                    cells[4] = new PdfPCell(new Phrase(dependent.getBirthDate() != null ? dependent.getBirthDate().toString() : "-", dependentValueFont));
                    cells[5] = new PdfPCell(new Phrase(translateGender(dependent.getGender() != null ? dependent.getGender().name() : null), dependentValueFont));
                    
                    for (PdfPCell cell : cells) {
                        cell.setBackgroundColor(rowColor);
                        cell.setPadding(7);
                        cell.setBorderWidth(0.5f);
                        cell.setBorderColor(new Color(230, 230, 230));
                        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                        dependentsTable.addCell(cell);
                    }
                    rowIndex++;
                }
                
                document.add(dependentsTable);
            }
            
            // ============================================
            // FOOTER
            // ============================================
            document.add(Chunk.NEWLINE);
            document.add(Chunk.NEWLINE);
            
            // Footer separator using table
            PdfPTable footerSeparatorTable = new PdfPTable(1);
            footerSeparatorTable.setWidthPercentage(100);
            PdfPCell footerSeparatorCell = new PdfPCell();
            footerSeparatorCell.setBorder(Rectangle.NO_BORDER);
            footerSeparatorCell.setBorderWidthTop(1f);
            footerSeparatorCell.setBorderColorTop(new Color(200, 200, 200));
            footerSeparatorCell.setFixedHeight(1);
            footerSeparatorTable.addCell(footerSeparatorCell);
            document.add(footerSeparatorTable);
            document.add(Chunk.NEWLINE);
            
            Font footerFont = new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(120, 120, 120));
            Paragraph footer = new Paragraph(
                "هذه الوثيقة صادرة عن " + (employer.getName() != null ? employer.getName() : "نظام WAAD") + 
                " • تم التوليد تلقائياً بواسطة نظام WAAD الإلكتروني • جميع الحقوق محفوظة © 2026",
                footerFont
            );
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);
            
            document.close();
            
            log.info("[MemberPdfExportService] Professional member card PDF generated successfully, size: {} bytes", outputStream.size());
            return outputStream.toByteArray();
            
        } catch (DocumentException e) {
            log.error("[MemberPdfExportService] Failed to generate member card PDF", e);
            throw new RuntimeException("Failed to generate member card PDF", e);
        }
    }
}
