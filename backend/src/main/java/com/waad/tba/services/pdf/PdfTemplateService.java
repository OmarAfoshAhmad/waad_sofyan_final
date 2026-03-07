package com.waad.tba.services.pdf;

import com.waad.tba.modules.pdf.entity.PdfCompanySettings;
import com.waad.tba.modules.pdf.service.PdfCompanySettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;
import java.util.Map;

/**
 * Thymeleaf Template Processing Service
 * 
 * Processes Thymeleaf templates with company settings
 * for PDF generation.
 * 
 * @since 2026-01-11
 * @deprecated PDF export disabled. Excel is the official reporting format.
 *             Kept for potential legal/compliance reports in the future.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Deprecated(since = "2026-01", forRemoval = false)
@SuppressWarnings("dep-ann")
public class PdfTemplateService {
    
    private final TemplateEngine templateEngine;
    private final PdfCompanySettingsService companySettingsService;
    
    /**
     * Process template with data and company settings
     * 
     * @param templateName Thymeleaf template name (without .html extension)
     * @param variables Data variables for the template
     * @param locale Locale for internationalization (ar, en)
     * @return Processed HTML string ready for PDF conversion
     */
    public String processTemplate(String templateName, Map<String, Object> variables, Locale locale) {
        log.debug("[PdfTemplateService] Processing template: {} with locale: {}", templateName, locale);
        
        // Get active company settings
        PdfCompanySettings settings = companySettingsService.getActiveSettings();
        
        // Create Thymeleaf context
        Context context = new Context(locale);
        
        // Add company settings to context
        context.setVariable("company", settings);
        
        // Add user variables
        if (variables != null) {
            variables.forEach(context::setVariable);
        }
        
        // Process template
        String html = templateEngine.process(templateName, context);
        
        log.debug("[PdfTemplateService] Template processed successfully: {} ({} chars)", 
            templateName, html.length());
        
        return html;
    }
    
    /**
     * Process template with Arabic locale (default)
     */
    public String processTemplate(String templateName, Map<String, Object> variables) {
        return processTemplate(templateName, variables, new Locale("ar", "SA"));
    }
}
