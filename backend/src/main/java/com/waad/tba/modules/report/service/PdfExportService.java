package com.waad.tba.modules.report.service;

import com.lowagie.text.pdf.BaseFont;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class PdfExportService {

    public byte[] generatePdfFromHtml(String html) throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            
            ClassPathResource fontResource = new ClassPathResource("fonts/Cairo-Regular.ttf");
            if (fontResource.exists()) {
                File fontFile;
                try {
                    fontFile = fontResource.getFile();
                } catch (Exception ex) {
                    Path tempFont = Files.createTempFile("waad-cairo-regular-", ".ttf");
                    try (InputStream input = fontResource.getInputStream()) {
                        Files.copy(input, tempFont, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                    tempFont.toFile().deleteOnExit();
                    fontFile = tempFont.toFile();
                }
                renderer.getFontResolver().addFont(fontFile.getAbsolutePath(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            }
            
            renderer.setDocumentFromString(toPdfSafeXhtml(html));
            renderer.layout();
            renderer.createPDF(outputStream);
            return outputStream.toByteArray();
        }
    }

    private String toPdfSafeXhtml(String html) {
        if (html == null || html.isBlank()) {
            return "<html><head><title></title></head><body></body></html>";
        }

        String xhtml = html
                .replace("\uFEFF", "")
                .stripLeading()
                .replace("&nbsp;", "&#160;");

        xhtml = xhtml.replaceFirst("(?is)^<!doctype[^>]*>\\s*", "");

        xhtml = xhtml.replaceAll("(?i)<meta([^>/]*?)>", "<meta$1 />");
        xhtml = xhtml.replaceAll("(?i)<link([^>/]*?)>", "<link$1 />");
        xhtml = xhtml.replaceAll("(?i)<img([^>/]*?)>", "<img$1 />");
        xhtml = xhtml.replaceAll("(?i)<br([^>/]*?)>", "<br$1 />");
        xhtml = xhtml.replaceAll("(?i)<hr([^>/]*?)>", "<hr$1 />");
        xhtml = xhtml.replaceAll("(?i)<input([^>/]*?)>", "<input$1 />");

        return xhtml;
    }
}
