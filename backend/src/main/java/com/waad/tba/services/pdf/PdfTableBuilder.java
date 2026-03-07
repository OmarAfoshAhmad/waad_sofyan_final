package com.waad.tba.services.pdf;

import com.lowagie.text.*;
import java.awt.Color;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.waad.tba.services.pdf.config.PdfFontConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * PDF Table Builder
 * 
 * Builds professional tables from DTOs using reflection.
 * Supports:
 * - Data tables with headers and rows
 * - Key-value tables for detail views
 * - Custom column widths
 * - Alternating row colors
 * - Header repetition on each page
 * 
 * @since 2026-01-06
 * @deprecated PDF export disabled. Excel is the official reporting format.
 *             Kept for potential legal/compliance reports in the future.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Deprecated(since = "2026-01", forRemoval = false)
@SuppressWarnings("dep-ann")
public class PdfTableBuilder {
    
    private final PdfFontConfig fontConfig;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    
    /**
     * Build key-value table for detail views
     */
    public PdfPTable buildKeyValueTable(List<KeyValue> data) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        try {
            table.setWidths(new float[]{40, 60});
        } catch (DocumentException e) {
            log.error("Failed to set column widths", e);
        }
        
        table.setHeaderRows(0);
        table.setSpacingBefore(10f);
        table.setSpacingAfter(10f);
        
        for (KeyValue kv : data) {
            // Key cell
            PdfPCell keyCell = new PdfPCell(
                new Phrase(kv.getKey(), fontConfig.getFont(kv.getKey(), false)));
            keyCell.setBackgroundColor(new Color(230, 230, 230));
            keyCell.setPadding(8f);
            keyCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(keyCell);
            
            // Value cell
            String value = kv.getValue() != null ? kv.getValue() : "-";
            PdfPCell valueCell = new PdfPCell(
                new Phrase(value, fontConfig.getFont(value, false)));
            valueCell.setPadding(8f);
            valueCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(valueCell);
        }
        
        return table;
    }
    
    /**
     * Simple key-value pair for detail tables
     */
    @Data
    @AllArgsConstructor
    public static class KeyValue {
        private String key;
        private String value;
    }
    
    /**
     * Format value for display
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "-";
        }
        
        if (value instanceof LocalDate) {
            return ((LocalDate) value).format(DATE_FORMATTER);
        }
        
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).format(DATETIME_FORMATTER);
        }
        
        if (value instanceof Boolean) {
            return ((Boolean) value) ? "نعم" : "لا";
        }
        
        return value.toString();
    }
}
