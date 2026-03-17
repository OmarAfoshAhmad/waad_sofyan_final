package com.waad.tba.services.pdf;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * HTML to PDF Converter Service (Flying Saucer)
 * 
 * Converts processed HTML templates to PDF using Flying Saucer
 * with full CSS support including @page rules for headers/footers.
 * 
 * @since 2026-01-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HtmlToPdfService {

    /**
     * Convert HTML string to PDF bytes
     * 
     * @param html Well-formed XHTML content
     * @return PDF as byte array
     * @throws IOException if PDF generation fails
     */
    public byte[] convertHtmlToPdf(String html) throws IOException {
        log.debug("[HtmlToPdfService] Converting HTML to PDF ({} chars)", html.length());

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            // Create ITextRenderer (Flying Saucer)
            ITextRenderer renderer = new ITextRenderer();

            // Set document from HTML string
            renderer.setDocumentFromString(html);

            // Layout the PDF
            renderer.layout();

            // Create PDF
            renderer.createPDF(outputStream);

            byte[] pdfBytes = outputStream.toByteArray();

            log.info("[HtmlToPdfService] PDF generated successfully: {} bytes", pdfBytes.length);

            return pdfBytes;

        } catch (Exception e) {
            log.error("[HtmlToPdfService] Failed to generate PDF", e);
            throw new IOException("PDF generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Convert HTML with base URL for resolving relative resources
     * 
     * @param html    XHTML content
     * @param baseUrl Base URL for resources (images, CSS)
     * @return PDF as byte array
     * @throws IOException if PDF generation fails
     */
    public byte[] convertHtmlToPdf(String html, String baseUrl) throws IOException {
        log.debug("[HtmlToPdfService] Converting HTML to PDF with base URL: {}", baseUrl);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            ITextRenderer renderer = new ITextRenderer();

            // Set document with base URL
            renderer.setDocumentFromString(html, baseUrl);

            renderer.layout();
            renderer.createPDF(outputStream);

            byte[] pdfBytes = outputStream.toByteArray();

            log.info("[HtmlToPdfService] PDF generated with base URL: {} bytes", pdfBytes.length);

            return pdfBytes;

        } catch (Exception e) {
            log.error("[HtmlToPdfService] Failed to generate PDF with base URL", e);
            throw new IOException("PDF generation failed: " + e.getMessage(), e);
        }
    }
}
