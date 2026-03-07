package com.waad.tba.modules.provider.service;

import com.waad.tba.common.excel.dto.ExcelImportResult;
import com.waad.tba.common.excel.dto.ExcelImportResult.ImportError;
import com.waad.tba.common.excel.dto.ExcelImportResult.ImportError.ErrorType;
import com.waad.tba.common.excel.dto.ExcelImportResult.ImportSummary;
import com.waad.tba.common.excel.dto.ExcelLookupData;
import com.waad.tba.common.excel.dto.ExcelTemplateColumn;
import com.waad.tba.common.excel.dto.ExcelTemplateColumn.ColumnType;
import com.waad.tba.common.excel.service.ExcelParserService;
import com.waad.tba.common.excel.service.ExcelTemplateService;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

/**
 * Provider Excel Template Generator and Import Service
 * 
 * STRICT RULES:
 * - System-generated templates only
 * - Create-only mode (Phase 1)
 * - Provider code/license number auto-generated
 * - Status defaults to ACTIVE
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderExcelTemplateService {
    
    private final ExcelTemplateService templateService;
    private final ExcelParserService parserService;
    private final ProviderRepository providerRepository;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TEMPLATE GENERATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    public byte[] generateTemplate() throws IOException {
        log.info("[ProviderTemplate] Generating Excel template");
        
        List<ExcelTemplateColumn> columns = buildColumnDefinitions();
        List<ExcelLookupData> lookups = buildLookupSheets();
        
        return templateService.generateTemplate("Medical Providers / مقدمي الخدمة", columns, lookups);
    }
    
    private List<ExcelTemplateColumn> buildColumnDefinitions() {
        return List.of(
            ExcelTemplateColumn.builder()
                .name("provider_name")
                .nameAr("اسم مقدم الخدمة")
                .type(ColumnType.TEXT)
                .required(true)
                .example("مستشفى السلام")
                .description("Provider name in Arabic (mandatory)")
                .descriptionAr("اسم مقدم الخدمة بالعربية (إجباري)")
                .width(30)
                .build(),
                
            ExcelTemplateColumn.builder()
                .name("provider_type")
                .nameAr("نوع المقدم")
                .type(ColumnType.ENUM)
                .required(true)
                .allowedValues(Arrays.asList("HOSPITAL", "CLINIC", "LAB", "PHARMACY", "RADIOLOGY",
                                            "مستشفى", "عيادة", "مختبر", "صيدلية", "أشعة"))
                .example("HOSPITAL")
                .description("Provider type (select from dropdown)")
                .descriptionAr("نوع مقدم الخدمة (اختر من القائمة)")
                .width(20)
                .build(),
                
            ExcelTemplateColumn.builder()
                .name("city")
                .nameAr("المدينة")
                .type(ColumnType.TEXT)
                .required(true)
                .example("طرابلس")
                .description("City (mandatory)")
                .descriptionAr("المدينة (إجباري)")
                .width(15)
                .build(),
                
            ExcelTemplateColumn.builder()
                .name("name_english")
                .nameAr("الاسم بالإنجليزية")
                .type(ColumnType.TEXT)
                .required(false)
                .example("Al Salam Hospital")
                .description("Provider name in English")
                .descriptionAr("اسم مقدم الخدمة بالإنجليزية")
                .width(30)
                .build(),
                
            ExcelTemplateColumn.builder()
                .name("phone")
                .nameAr("رقم الهاتف")
                .type(ColumnType.TEXT)
                .required(false)
                .example("0212345678")
                .description("Phone number")
                .descriptionAr("رقم الهاتف")
                .width(15)
                .build(),
                
            ExcelTemplateColumn.builder()
                .name("email")
                .nameAr("البريد الإلكتروني")
                .type(ColumnType.TEXT)
                .required(false)
                .example("info@alsalam.ly")
                .description("Email address")
                .descriptionAr("البريد الإلكتروني")
                .width(25)
                .build(),
                
            ExcelTemplateColumn.builder()
                .name("address")
                .nameAr("العنوان")
                .type(ColumnType.TEXT)
                .required(false)
                .example("شارع الجمهورية، طرابلس")
                .description("Full address")
                .descriptionAr("العنوان الكامل")
                .width(35)
                .build()
        );
    }
    
    private List<ExcelLookupData> buildLookupSheets() {
        ExcelLookupData typesLookup = ExcelLookupData.builder()
            .sheetName("Provider Types")
            .sheetNameAr("أنواع المقدمين")
            .headers(Arrays.asList("Type (EN)", "Type (AR)"))
            .data(Arrays.asList(
                Arrays.asList("HOSPITAL", "مستشفى"),
                Arrays.asList("CLINIC", "عيادة"),
                Arrays.asList("LAB", "مختبر"),
                Arrays.asList("PHARMACY", "صيدلية"),
                Arrays.asList("RADIOLOGY", "أشعة")
            ))
            .description("Valid provider types")
            .descriptionAr("أنواع مقدمي الخدمة الصالحة")
            .build();
        
        return List.of(typesLookup);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // IMPORT PROCESSING
    // ═══════════════════════════════════════════════════════════════════════════
    
    public ExcelImportResult importFromExcel(MultipartFile file) {
        log.info("[ProviderImport] Starting import from file: {}", file.getOriginalFilename());
        
        ImportSummary summary = ImportSummary.builder().build();
        List<ImportError> errors = new ArrayList<>();
        
        try (Workbook workbook = parserService.openWorkbook(file)) {
            Sheet sheet = parserService.getDataSheet(workbook);
            
            Row headerRow = sheet.getRow(0);
            Map<String, Integer> columnIndices = findColumnIndices(headerRow);
            
            validateMandatoryColumns(columnIndices, errors);
            if (!errors.isEmpty()) {
                return buildErrorResult(summary, errors, "Mandatory columns missing");
            }
            
            int firstDataRow = 2;
            int lastRow = sheet.getLastRowNum();
            summary.setTotalRows(lastRow - firstDataRow + 1);
            
            log.info("[ProviderImport] Processing {} rows", summary.getTotalRows());
            
            for (int rowNum = firstDataRow; rowNum <= lastRow; rowNum++) {
                Row row = sheet.getRow(rowNum);
                
                if (parserService.isEmptyRow(row)) {
                    continue;
                }
                
                try {
                    Provider provider = parseAndCreateProvider(row, rowNum, columnIndices, errors);
                    
                    if (provider != null) {
                        providerRepository.save(provider);
                        summary.setCreated(summary.getCreated() + 1);
                        log.debug("[ProviderImport] Created provider: {}", provider.getLicenseNumber());
                    } else {
                        summary.setRejected(summary.getRejected() + 1);
                    }
                    
                } catch (Exception e) {
                    log.error("[ProviderImport] Error processing row {}: {}", rowNum, e.getMessage());
                    errors.add(ImportError.builder()
                        .rowNumber(rowNum - 1)
                        .errorType(ErrorType.PROCESSING_ERROR)
                        .messageAr("خطأ في معالجة الصف")
                        .messageEn("Error processing row: " + e.getMessage())
                        .build());
                    summary.setFailed(summary.getFailed() + 1);
                }
            }
            
            String messageAr = String.format("تم إنشاء %d مقدم خدمة، تم تخطي %d، فشل %d",
                summary.getCreated(), summary.getSkipped(), summary.getRejected() + summary.getFailed());
            String messageEn = String.format("Created %d providers, skipped %d, failed %d",
                summary.getCreated(), summary.getSkipped(), summary.getRejected() + summary.getFailed());
            
            log.info("[ProviderImport] Import completed: {}", messageEn);
            
            return ExcelImportResult.builder()
                .summary(summary)
                .errors(errors)
                .success(summary.getCreated() > 0)
                .messageAr(messageAr)
                .messageEn(messageEn)
                .build();
                
        } catch (IOException e) {
            log.error("[ProviderImport] Failed to read Excel file", e);
            throw new BusinessRuleException("فشل قراءة ملف Excel: " + e.getMessage());
        } catch (Exception e) {
            log.error("[ProviderImport] Import failed", e);
            throw new BusinessRuleException("فشل استيراد البيانات: " + e.getMessage());
        }
    }
    
    private Map<String, Integer> findColumnIndices(Row headerRow) {
        Map<String, Integer> indices = new HashMap<>();
        
        indices.put("provider_name", parserService.findColumnIndex(headerRow, 
            "provider_name", "اسم مقدم الخدمة", "name", "الاسم"));
        indices.put("provider_type", parserService.findColumnIndex(headerRow, 
            "provider_type", "نوع المقدم", "type", "النوع"));
        indices.put("city", parserService.findColumnIndex(headerRow, 
            "city", "المدينة"));
        indices.put("name_english", parserService.findColumnIndex(headerRow, 
            "name_english", "الاسم بالإنجليزية"));
        indices.put("phone", parserService.findColumnIndex(headerRow, 
            "phone", "رقم الهاتف", "الهاتف"));
        indices.put("email", parserService.findColumnIndex(headerRow, 
            "email", "البريد الإلكتروني"));
        indices.put("address", parserService.findColumnIndex(headerRow, 
            "address", "العنوان"));
        
        return indices;
    }
    
    private void validateMandatoryColumns(Map<String, Integer> columnIndices, List<ImportError> errors) {
        if (columnIndices.get("provider_name") == null) {
            errors.add(ImportError.builder()
                .rowNumber(0)
                .errorType(ErrorType.MISSING_REQUIRED)
                .columnName("provider_name")
                .messageAr("عمود اسم مقدم الخدمة مفقود")
                .messageEn("Provider name column is missing")
                .build());
        }
        
        if (columnIndices.get("provider_type") == null) {
            errors.add(ImportError.builder()
                .rowNumber(0)
                .errorType(ErrorType.MISSING_REQUIRED)
                .columnName("provider_type")
                .messageAr("عمود نوع المقدم مفقود")
                .messageEn("Provider type column is missing")
                .build());
        }
        
        if (columnIndices.get("city") == null) {
            errors.add(ImportError.builder()
                .rowNumber(0)
                .errorType(ErrorType.MISSING_REQUIRED)
                .columnName("city")
                .messageAr("عمود المدينة مفقود")
                .messageEn("City column is missing")
                .build());
        }
    }
    
    private Provider parseAndCreateProvider(
            Row row,
            int rowNum,
            Map<String, Integer> columnIndices,
            List<ImportError> errors
    ) {
        String providerName = getCellValue(row, columnIndices.get("provider_name"));
        String providerTypeStr = getCellValue(row, columnIndices.get("provider_type"));
        String city = getCellValue(row, columnIndices.get("city"));
        
        boolean hasErrors = false;
        
        if (providerName == null || providerName.trim().isEmpty()) {
            errors.add(createError(rowNum, ErrorType.MISSING_REQUIRED, "provider_name", 
                "اسم مقدم الخدمة مطلوب", "Provider name is required", providerName));
            hasErrors = true;
        }
        
        if (providerTypeStr == null || providerTypeStr.trim().isEmpty()) {
            errors.add(createError(rowNum, ErrorType.MISSING_REQUIRED, "provider_type", 
                "نوع المقدم مطلوب", "Provider type is required", providerTypeStr));
            hasErrors = true;
        }
        
        if (city == null || city.trim().isEmpty()) {
            errors.add(createError(rowNum, ErrorType.MISSING_REQUIRED, "city", 
                "المدينة مطلوبة", "City is required", city));
            hasErrors = true;
        }
        
        ProviderType providerType = null;
        if (providerTypeStr != null && !providerTypeStr.trim().isEmpty()) {
            providerType = parseProviderType(providerTypeStr);
            if (providerType == null) {
                errors.add(createError(rowNum, ErrorType.INVALID_ENUM, "provider_type", 
                    "قيمة نوع المقدم غير صحيحة: " + providerTypeStr, 
                    "Invalid provider type: " + providerTypeStr, providerTypeStr));
                hasErrors = true;
            }
        }
        
        if (hasErrors) {
            return null;
        }
        
        // Auto-generate license number
        String licenseNumber = generateLicenseNumber(providerType);
        
        // Use provider name (Arabic-only system)
        String nameValue = (providerName != null && !providerName.trim().isEmpty()) 
            ? providerName.trim() 
            : "";

        Provider provider = Provider.builder()
            .name(nameValue)
            .licenseNumber(licenseNumber)
            .providerType(providerType)
            .city(city != null ? city.trim() : null)
            .phone(getCellValue(row, columnIndices.get("phone")))
            .email(getCellValue(row, columnIndices.get("email")))
            .address(getCellValue(row, columnIndices.get("address")))
            .active(true)
            .build();
        
        return provider;
    }
    
    private String generateLicenseNumber(ProviderType type) {
        String prefix = type != null ? type.name().substring(0, 3).toUpperCase() : "PRV";
        // Use UUID to ensure uniqueness even in fast loops
        String uniqueId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return String.format("%s-%s", prefix, uniqueId);
    }
    
    private ProviderType parseProviderType(String value) {
        if (value == null) return null;
        
        String normalized = value.trim().toUpperCase();
        
        if (normalized.equals("HOSPITAL") || normalized.equals("مستشفى")) {
            return ProviderType.HOSPITAL;
        } else if (normalized.equals("CLINIC") || normalized.equals("عيادة")) {
            return ProviderType.CLINIC;
        } else if (normalized.equals("LAB") || normalized.equals("مختبر")) {
            return ProviderType.LAB;
        } else if (normalized.equals("PHARMACY") || normalized.equals("صيدلية")) {
            return ProviderType.PHARMACY;
        } else if (normalized.equals("RADIOLOGY") || normalized.equals("أشعة")) {
            return ProviderType.RADIOLOGY;
        }
        
        return null;
    }
    
    private String getCellValue(Row row, Integer columnIndex) {
        if (columnIndex == null) {
            return null;
        }
        return parserService.getCellValueAsString(row.getCell(columnIndex));
    }
    
    private ImportError createError(int rowNum, ErrorType type, String columnName, 
                                    String messageAr, String messageEn, String value) {
        return ImportError.builder()
            .rowNumber(rowNum - 1)
            .errorType(type)
            .columnName(columnName)
            .messageAr(messageAr)
            .messageEn(messageEn)
            .value(value)
            .build();
    }
    
    private ExcelImportResult buildErrorResult(ImportSummary summary, List<ImportError> errors, String message) {
        return ExcelImportResult.builder()
            .summary(summary)
            .errors(errors)
            .success(false)
            .messageAr("فشل الاستيراد: " + message)
            .messageEn("Import failed: " + message)
            .build();
    }
}
