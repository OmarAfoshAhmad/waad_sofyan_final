package com.waad.tba.services.pdf;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.waad.tba.services.pdf.config.PdfFontConfig;
import com.waad.tba.services.pdf.dto.PdfReportMetadata;
import com.waad.tba.services.pdf.dto.PdfReportRequest;
import com.waad.tba.services.pdf.templates.MemberReportTemplate;
import com.waad.tba.services.pdf.templates.ProviderReportTemplate;
import com.waad.tba.services.pdf.templates.ContractReportTemplate;
import com.waad.tba.services.pdf.templates.ClaimReportTemplate;
import com.waad.tba.services.pdf.templates.PreAuthorizationReportTemplate;
import com.waad.tba.services.pdf.templates.BenefitPolicyReportTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * PDF Report Generation Service
 * 
 * Main service for generating professional PDF reports.
 * 
 * @since 2026-01-06
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfReportService {
    
    private final PdfFontConfig fontConfig;
    private final MemberReportTemplate memberReportTemplate;
    private final ProviderReportTemplate providerReportTemplate;
    private final ContractReportTemplate contractReportTemplate;
    private final ClaimReportTemplate claimReportTemplate;
    private final PreAuthorizationReportTemplate preAuthorizationReportTemplate;
    private final BenefitPolicyReportTemplate benefitPolicyReportTemplate;
    
    /**
     * Generate PDF report from request
     */
    public byte[] generateReport(PdfReportRequest<?> request) {
        log.info("[PdfReportService] Generating report: {}", request.getMetadata().getReportType());
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Document document = null;
        
        try {
            PdfReportMetadata metadata = request.getMetadata();
            
            // Create document
            document = createDocument(metadata);
            
            // Create writer
            PdfWriter writer = PdfWriter.getInstance(document, outputStream);
            writer.setPageEvent(new HeaderFooterHandler(metadata, fontConfig));
            
            // Open document
            document.open();
            
            // Add title
            addTitle(document, metadata);
            
            // Add content based on report type
            List<Element> contentElements = generateContent(request);
            for (Element element : contentElements) {
                document.add(element);
            }
            
            log.info("[PdfReportService] Report generated successfully: {} bytes", 
                    outputStream.size());
            
        } catch (Exception e) {
            log.error("[PdfReportService] Failed to generate report", e);
            throw new RuntimeException("Failed to generate PDF report", e);
        } finally {
            if (document != null && document.isOpen()) {
                document.close();
            }
        }
        
        return outputStream.toByteArray();
    }
    
    /**
     * Create document with appropriate page size and orientation
     */
    private Document createDocument(PdfReportMetadata metadata) {
        Rectangle pageSize = metadata.getOrientation() == PdfReportMetadata.PageOrientation.LANDSCAPE
            ? PageSize.A4.rotate()
            : PageSize.A4;
        
        return new Document(pageSize, 50f, 50f, 100f, 70f); // margins: left, right, top, bottom
    }
    
    /**
     * Add title to document
     */
    private void addTitle(Document document, PdfReportMetadata metadata) throws DocumentException {
        Paragraph title = new Paragraph(
            metadata.getTitleAr(),
            fontConfig.getFont(metadata.getTitleAr(), true)
        );
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(10f);
        document.add(title);
        document.add(Chunk.NEWLINE);
    }
    
    /**
     * Generate content elements based on report type
     */
    @SuppressWarnings("unchecked")
    private List<Element> generateContent(PdfReportRequest<?> request) {
        PdfReportMetadata metadata = request.getMetadata();
        
        switch (metadata.getReportType()) {
            case MEMBER:
                if (request.getData() != null) {
                    return memberReportTemplate.generateMemberDetailReport(
                        (com.waad.tba.modules.member.dto.MemberResponseDto) request.getData());
                } else if (request.getDataList() != null) {
                    return memberReportTemplate.generateMemberListReport(
                        (List<com.waad.tba.modules.member.dto.MemberResponseDto>) request.getDataList());
                }
                break;
            
            case PROVIDER:
                if (request.getData() != null) {
                    return providerReportTemplate.generateProviderDetailReport(
                        (com.waad.tba.modules.provider.dto.ProviderResponseDto) request.getData());
                } else if (request.getDataList() != null) {
                    return providerReportTemplate.generateProviderListReport(
                        (List<com.waad.tba.modules.provider.dto.ProviderResponseDto>) request.getDataList());
                }
                break;
            
            case CONTRACT:
                if (request.getData() != null) {
                    return contractReportTemplate.generateContractDetailReport(
                        (com.waad.tba.modules.providercontract.dto.ContractResponseDto) request.getData());
                } else if (request.getDataList() != null) {
                    return contractReportTemplate.generateContractListReport(
                        (List<com.waad.tba.modules.providercontract.dto.ContractResponseDto>) request.getDataList());
                }
                break;
            
            case CLAIM:
                if (request.getData() != null) {
                    return claimReportTemplate.generateClaimDetailReport(
                        (com.waad.tba.modules.claim.dto.ClaimResponseDto) request.getData());
                } else if (request.getDataList() != null) {
                    return claimReportTemplate.generateClaimListReport(
                        (List<com.waad.tba.modules.claim.dto.ClaimResponseDto>) request.getDataList());
                }
                break;
            
            case PREAUTHORIZATION:
                if (request.getData() != null) {
                    return preAuthorizationReportTemplate.generatePreAuthorizationDetailReport(
                        (com.waad.tba.modules.preauthorization.dto.PreAuthorizationResponseDto) request.getData());
                } else if (request.getDataList() != null) {
                    return preAuthorizationReportTemplate.generatePreAuthorizationListReport(
                        (List<com.waad.tba.modules.preauthorization.dto.PreAuthorizationResponseDto>) request.getDataList());
                }
                break;
            
            case BENEFIT_POLICY:
                if (request.getData() != null) {
                    return benefitPolicyReportTemplate.generateBenefitPolicyDetailReport(
                        (com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyResponseDto) request.getData());
                } else if (request.getDataList() != null) {
                    return benefitPolicyReportTemplate.generateBenefitPolicyListReport(
                        (List<com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyResponseDto>) request.getDataList());
                }
                break;
            
            default:
                log.warn("[PdfReportService] Report type not implemented: {}", metadata.getReportType());
        }
        
        return List.of(new Paragraph("Report content not available", fontConfig.getLatinNormalFont()));
    }
    
    /**
     * Header and Footer Page Event Handler
     */
    private static class HeaderFooterHandler extends PdfPageEventHelper {
        private final PdfReportMetadata metadata;
        private final PdfFontConfig fontConfig;
        
        public HeaderFooterHandler(PdfReportMetadata metadata, PdfFontConfig fontConfig) {
            this.metadata = metadata;
            this.fontConfig = fontConfig;
        }
        
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Rectangle page = document.getPageSize();
            
            try {
                // Header
                Phrase header = new Phrase(
                    metadata.getTitleAr(),
                    fontConfig.getArabicSmallFont()
                );
                ColumnText.showTextAligned(
                    writer.getDirectContent(),
                    Element.ALIGN_CENTER,
                    header,
                    (page.getLeft() + page.getRight()) / 2,
                    page.getTop() - 30,
                    0
                );
                
                // Footer - Left side (page number)
                Phrase pageNum = new Phrase(
                    String.format("Page %d", writer.getPageNumber()),
                    fontConfig.getLatinSmallFont()
                );
                ColumnText.showTextAligned(
                    writer.getDirectContent(),
                    Element.ALIGN_LEFT,
                    pageNum,
                    page.getLeft() + 50,
                    page.getBottom() + 30,
                    0
                );
                
                // Footer - Right side (generation info)
                String generatedInfo = String.format(
                    "Generated: %s",
                    metadata.getGeneratedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                );
                Phrase footer = new Phrase(generatedInfo, fontConfig.getLatinSmallFont());
                ColumnText.showTextAligned(
                    writer.getDirectContent(),
                    Element.ALIGN_RIGHT,
                    footer,
                    page.getRight() - 50,
                    page.getBottom() + 30,
                    0
                );
                
            } catch (Exception e) {
                // Log but don't fail the document
                System.err.println("Error adding header/footer: " + e.getMessage());
            }
        }
    }
}
