package com.waad.tba.common.excel;

import com.waad.tba.common.exception.BusinessRuleException;
import org.springframework.web.multipart.MultipartFile;

/**
 * Shared guard for Excel (.xlsx) upload endpoints: rejects empty files, files
 * over the sane per-upload cap, and anything not named/typed as an Excel
 * workbook, before the bytes ever reach Apache POI parsing.
 */
public final class ExcelUploadValidator {

    private static final long MAX_UPLOAD_BYTES = 20L * 1024 * 1024; // 20MB — generous for a price list, far below the 60MB global multipart cap

    private ExcelUploadValidator() {
    }

    public static void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("الملف فارغ");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new BusinessRuleException("حجم الملف يتجاوز الحد المسموح به (20 ميجابايت)");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessRuleException("صيغة الملف غير مدعومة، يجب أن يكون الملف بصيغة xlsx");
        }
    }
}
