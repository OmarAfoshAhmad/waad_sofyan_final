package com.waad.tba.modules.provider.service;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.medicaltaxonomy.dto.ExcelImportResultDto;
import com.waad.tba.modules.medicaltaxonomy.dto.ExcelImportResultDto.ImportError;
import com.waad.tba.modules.medicaltaxonomy.dto.ExcelImportResultDto.ImportSummary;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for importing Providers from Excel files
 * 
 * Expected Excel format:
 * | name | licenseNumber | providerType | city | phone | email | active |
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderExcelService {

    private final ProviderRepository providerRepository;

    /** Maximum allowed Excel file size: 10 MB */
    private static final long MAX_EXCEL_FILE_SIZE = 10L * 1024 * 1024;

    @Transactional
    public ExcelImportResultDto importFromExcel(MultipartFile file) {
        log.info("[ProviderExcel] Starting import from file: {}", file.getOriginalFilename());

        if (file.isEmpty()) {
            throw new BusinessRuleException("الملف فارغ");
        }

        if (file.getSize() > MAX_EXCEL_FILE_SIZE) {
            throw new BusinessRuleException("حجم الملف يتجاوز الحد المسموح (10 ميغابايت)");
        }

        if (!isExcelFile(file)) {
            throw new BusinessRuleException("نوع الملف غير صحيح. يجب أن يكون ملف Excel (.xlsx أو .xls)");
        }

        ImportSummary summary = ImportSummary.builder()
                .total(0)
                .inserted(0)
                .updated(0)
                .skipped(0)
                .failed(0)
                .errors(new ArrayList<>())
                .build();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BusinessRuleException("الملف لا يحتوي على صف رأس (Header)");
            }

            Map<String, Integer> columnMap = mapColumns(headerRow);
            validateRequiredColumns(columnMap);

            int lastRow = sheet.getLastRowNum();
            log.info("[ProviderExcel] Processing {} rows", lastRow);

            for (int rowNum = 1; rowNum <= lastRow; rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null || isEmptyRow(row)) {
                    continue;
                }

                summary.setTotal(summary.getTotal() + 1);

                try {
                    processRow(row, rowNum + 1, columnMap, summary);
                } catch (Exception e) {
                    log.error("[ProviderExcel] Error processing row {}: {}", rowNum + 1, e.getMessage());
                    summary.setFailed(summary.getFailed() + 1);
                    summary.getErrors().add(ImportError.builder()
                            .row(rowNum + 1)
                            .error(e.getMessage())
                            .build());
                }
            }

            String message = buildSuccessMessage(summary);
            log.info("[ProviderExcel] Import completed: {}", message);

            return ExcelImportResultDto.builder()
                    .success(true)
                    .summary(summary)
                    .message(message)
                    .build();

        } catch (IOException e) {
            log.error("[ProviderExcel] Failed to read Excel file", e);
            throw new BusinessRuleException("فشل قراءة ملف Excel: " + e.getMessage());
        } catch (Exception e) {
            log.error("[ProviderExcel] Import failed", e);
            throw new BusinessRuleException("فشل استيراد البيانات: " + e.getMessage());
        }
    }

    private void processRow(Row row, int rowNum, Map<String, Integer> columnMap, ImportSummary summary) {
        String name = getCellValueAsString(row, columnMap.get("name"));
        String licenseNumber = getCellValueAsString(row, columnMap.get("licenseNumber"));
        String providerTypeStr = getCellValueAsString(row, columnMap.get("providerType"));
        String city = getCellValueAsString(row, columnMap.get("city"));
        String phone = getCellValueAsString(row, columnMap.get("phone"));
        String email = getCellValueAsString(row, columnMap.get("email"));
        Boolean active = getCellValueAsBoolean(row, columnMap.get("active"));

        // Validate required fields
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessRuleException("الاسم (name) مطلوب");
        }
        if (licenseNumber == null || licenseNumber.trim().isEmpty()) {
            throw new BusinessRuleException("رقم الترخيص (licenseNumber) مطلوب");
        }
        if (providerTypeStr == null || providerTypeStr.trim().isEmpty()) {
            throw new BusinessRuleException("نوع مقدم الخدمة (providerType) مطلوب");
        }

        // Parse provider type
        ProviderType providerType = parseProviderType(providerTypeStr);
        if (providerType == null) {
            throw new BusinessRuleException("نوع مقدم الخدمة غير صحيح: " + providerTypeStr);
        }

        // Check if provider exists by license number
        Provider existingProvider = providerRepository.findByLicenseNumber(licenseNumber.trim()).orElse(null);

        if (existingProvider != null) {
            // Update existing provider
            if (name != null && !name.trim().isEmpty()) {
                existingProvider.setName(name.trim());
            }
            existingProvider.setProviderType(providerType);
            if (city != null && !city.trim().isEmpty()) {
                existingProvider.setCity(city.trim());
            }
            if (phone != null && !phone.trim().isEmpty()) {
                existingProvider.setPhone(phone.trim());
            }
            if (email != null && !email.trim().isEmpty()) {
                existingProvider.setEmail(email.trim());
            }
            if (active != null) {
                existingProvider.setActive(active);
            }
            existingProvider.setUpdatedAt(LocalDateTime.now());

            providerRepository.save(existingProvider);
            summary.setUpdated(summary.getUpdated() + 1);
            log.debug("[ProviderExcel] Updated provider: {}", licenseNumber);

        } else {
            // Insert new provider
            Provider newProvider = Provider.builder()
                    .name(name.trim())
                    .licenseNumber(licenseNumber.trim())
                    .providerType(providerType)
                    .city(city != null ? city.trim() : null)
                    .phone(phone != null ? phone.trim() : null)
                    .email(email != null ? email.trim() : null)
                    .active(active != null ? active : true)
                    .build();

            providerRepository.save(newProvider);
            summary.setInserted(summary.getInserted() + 1);
            log.debug("[ProviderExcel] Inserted provider: {}", licenseNumber);
        }
    }

    private ProviderType parseProviderType(String value) {
        String normalized = value.trim().toUpperCase();

        // Direct match
        try {
            return ProviderType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            // Try Arabic/alternative names
            return switch (normalized) {
                case "مستشفى", "مستشفي", "مستشفيات" -> ProviderType.HOSPITAL;
                case "عيادة", "عيادات" -> ProviderType.CLINIC;
                case "مختبر", "مختبرات", "LABORATORY" -> ProviderType.LAB;
                case "صيدلية", "صيدليات" -> ProviderType.PHARMACY;
                case "أشعة", "اشعة", "مركز أشعة" -> ProviderType.RADIOLOGY;
                default -> null;
            };
        }
    }

    private Map<String, Integer> mapColumns(Row headerRow) {
        Map<String, Integer> columnMap = new HashMap<>();

        for (Cell cell : headerRow) {
            String columnName = cell.getStringCellValue().trim().toLowerCase();

            if (columnName.equals("name") || columnName.equals("الاسم") || columnName.equals("اسم")
                    || columnName.equals("namearabic") || columnName.equals("name_arabic")) {
                columnMap.put("name", cell.getColumnIndex());
            } else if (columnName.equals("licensenumber") || columnName.equals("license_number")
                    || columnName.equals("license") || columnName.equals("رقم الترخيص")) {
                columnMap.put("licenseNumber", cell.getColumnIndex());
            } else if (columnName.equals("providertype") || columnName.equals("provider_type")
                    || columnName.equals("type") || columnName.equals("النوع")) {
                columnMap.put("providerType", cell.getColumnIndex());
            } else if (columnName.equals("city") || columnName.equals("المدينة")) {
                columnMap.put("city", cell.getColumnIndex());
            } else if (columnName.equals("phone") || columnName.equals("هاتف") || columnName.equals("الهاتف")) {
                columnMap.put("phone", cell.getColumnIndex());
            } else if (columnName.equals("email") || columnName.equals("بريد") || columnName.equals("بريد الكتروني")) {
                columnMap.put("email", cell.getColumnIndex());
            } else if (columnName.equals("active") || columnName.equals("نشط") || columnName.equals("الحالة")) {
                columnMap.put("active", cell.getColumnIndex());
            }
        }

        return columnMap;
    }

    private void validateRequiredColumns(Map<String, Integer> columnMap) {
        List<String> missing = new ArrayList<>();

        if (!columnMap.containsKey("name")) {
            missing.add("name (الاسم)");
        }
        if (!columnMap.containsKey("licenseNumber")) {
            missing.add("licenseNumber (رقم الترخيص)");
        }
        if (!columnMap.containsKey("providerType")) {
            missing.add("providerType (النوع)");
        }

        if (!missing.isEmpty()) {
            throw new BusinessRuleException("أعمدة مطلوبة مفقودة: " + String.join(", ", missing));
        }
    }

    private String getCellValueAsString(Row row, Integer colIndex) {
        if (colIndex == null) {
            return null;
        }

        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            return null;
        }

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    private Boolean getCellValueAsBoolean(Row row, Integer colIndex) {
        if (colIndex == null) {
            return null;
        }

        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            return null;
        }

        return switch (cell.getCellType()) {
            case BOOLEAN -> cell.getBooleanCellValue();
            case STRING -> {
                String value = cell.getStringCellValue().trim().toLowerCase();
                yield value.equals("true") || value.equals("yes") || value.equals("نعم") || value.equals("1");
            }
            case NUMERIC -> cell.getNumericCellValue() == 1.0;
            default -> null;
        };
    }

    private boolean isEmptyRow(Row row) {
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    private boolean isExcelFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            return false;
        }
        return filename.endsWith(".xlsx") || filename.endsWith(".xls");
    }

    private String buildSuccessMessage(ImportSummary summary) {
        StringBuilder msg = new StringBuilder();
        msg.append("تم استيراد البيانات بنجاح. ");

        if (summary.getInserted() > 0) {
            msg.append(summary.getInserted()).append(" سجل جديد، ");
        }
        if (summary.getUpdated() > 0) {
            msg.append(summary.getUpdated()).append(" سجل محدّث، ");
        }
        if (summary.getFailed() > 0) {
            msg.append(summary.getFailed()).append(" سجل فشل");
        }

        return msg.toString();
    }
}
