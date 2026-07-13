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
import com.waad.tba.modules.providercontract.dto.ProviderContractCreateDto;
import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import com.waad.tba.modules.providercontract.entity.ProviderContract.PricingModel;
import com.waad.tba.modules.providercontract.service.ProviderContractService;
import com.waad.tba.modules.rbac.dto.UserCreateDto;
import com.waad.tba.modules.rbac.service.UserService;
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
    private final ProviderContractService contractService;
    private final UserService userService;
    
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
                .required(false)
                .example("طرابلس")
                .description("City")
                .descriptionAr("المدينة")
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
                .build(),
                
            ExcelTemplateColumn.builder()
                .name("username")
                .nameAr("اسم المستخدم")
                .type(ColumnType.TEXT)
                .required(false)
                .example("ali_2026")
                .description("System username (auto-generated if empty)")
                .descriptionAr("اسم مستخدم النظام (يولد تلقائياً إذا كان فارغاً)")
                .width(20)
                .build(),

            ExcelTemplateColumn.builder()
                .name("network")
                .nameAr("الشبكة")
                .type(ColumnType.TEXT)
                .required(false)
                .example("داخل الشبكة")
                .description("Network status (Inside Network / Outside Network), default is Inside")
                .descriptionAr("حالة الشبكة (داخل الشبكة / خارج الشبكة)، الافتراضي داخل الشبكة")
                .width(20)
                .build(),
                
            ExcelTemplateColumn.builder()
                .name("start_date")
                .nameAr("تاريخ بداية العقد")
                .type(ColumnType.DATE)
                .required(false)
                .example("2025-01-01")
                .description("Contract start date (default: Jan 1st of current year)")
                .descriptionAr("تاريخ البداية (الافتراضي: 1 يناير من السنة الحالية)")
                .width(15)
                .build(),
                
            ExcelTemplateColumn.builder()
                .name("duration_months")
                .nameAr("المدة بالأشهر")
                .type(ColumnType.NUMBER)
                .required(false)
                .example("12")
                .description("Contract duration in months (default: 12)")
                .descriptionAr("مدة العقد بالأشهر (الافتراضي: 12)")
                .width(15)
                .build(),
                
            ExcelTemplateColumn.builder()
                .name("discount")
                .nameAr("نسبة الخصم")
                .type(ColumnType.NUMBER)
                .required(false)
                .example("10")
                .description("Contract discount percentage (default: 10)")
                .descriptionAr("نسبة الخصم للعقد (الافتراضي: 10)")
                .width(15)
                .build(),
                
            ExcelTemplateColumn.builder()
                .name("discount_timing")
                .nameAr("آلية الخصم")
                .type(ColumnType.ENUM)
                .required(false)
                .allowedValues(Arrays.asList("بعد المرفوض", "قبل المرفوض"))
                .example("بعد المرفوض")
                .description("Discount timing (default: بعد المرفوض)")
                .descriptionAr("آلية الخصم (الافتراضي: بعد المرفوض)")
                .width(15)
                .build(),

            ExcelTemplateColumn.builder()
                .name("status")
                .nameAr("الحالة")
                .type(ColumnType.ENUM)
                .required(false)
                .allowedValues(Arrays.asList("ACTIVE", "DRAFT", "SUSPENDED", "نشط", "مسودة", "موقوف"))
                .example("نشط")
                .description("Contract status (default: ACTIVE)")
                .descriptionAr("حالة العقد (الافتراضي: نشط)")
                .width(15)
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
                Arrays.asList("CLINIC", "عياده تخصصية"),
                Arrays.asList("CLINIC_DEN", "عياده اسنان"),
                Arrays.asList("LAB", "مختبر تحاليل"),
                Arrays.asList("PHARMACY", "صيدلية"),
                Arrays.asList("RADIOLOGY", "مركز أشعة"),
                Arrays.asList("PHYSIOTHERAPY", "مركز علاج طبيعي")
            ))
            .description("Valid provider types")
            .descriptionAr("أنواع مقدمي الخدمة الصالحة")
            .build();
            
        ExcelLookupData statusLookup = ExcelLookupData.builder()
            .sheetName("Contract Statuses")
            .sheetNameAr("حالات العقد")
            .headers(Arrays.asList("Status (EN)", "Status (AR)"))
            .data(Arrays.asList(
                Arrays.asList("ACTIVE", "نشط"),
                Arrays.asList("DRAFT", "مسودة"),
                Arrays.asList("SUSPENDED", "موقوف")
            ))
            .description("Valid contract statuses")
            .descriptionAr("حالات العقد الصالحة")
            .build();
        
        ExcelLookupData discountTimingLookup = ExcelLookupData.builder()
            .sheetName("Discount Timing")
            .sheetNameAr("آلية الخصم")
            .headers(Arrays.asList("Timing (AR)"))
            .data(Arrays.asList(
                Arrays.asList("بعد المرفوض"),
                Arrays.asList("قبل المرفوض")
            ))
            .description("Valid discount timing")
            .descriptionAr("آلية الخصم الصالحة")
            .build();
        
        return List.of(typesLookup, statusLookup, discountTimingLookup);
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
                        boolean isUpdate = provider.getId() != null;
                        providerRepository.save(provider);
                        
                        if (isUpdate) {
                            summary.setUpdated(summary.getUpdated() + 1);
                            log.debug("[ProviderImport] Updated provider: {}", provider.getLicenseNumber());
                        } else {
                            summary.setCreated(summary.getCreated() + 1);
                            log.debug("[ProviderImport] Created provider: {}", provider.getLicenseNumber());
                            
                            // 1. Create a contract for the new provider
                            try {
                                String discountStr = getCellValue(row, columnIndices.get("discount"));
                                String statusStr = getCellValue(row, columnIndices.get("status"));
                                String startDateStr = getCellValue(row, columnIndices.get("start_date"));
                                String durationStr = getCellValue(row, columnIndices.get("duration_months"));
                                String discountTimingStr = getCellValue(row, columnIndices.get("discount_timing"));
                                
                                java.math.BigDecimal discount = new java.math.BigDecimal("10.00");
                                if (discountStr != null && !discountStr.trim().isEmpty()) {
                                    try {
                                        discount = new java.math.BigDecimal(discountStr.trim());
                                    } catch (NumberFormatException ignored) {}
                                }
                                
                                boolean discountBeforeRejection = false; // Default to بعد المرفوض
                                if (discountTimingStr != null && discountTimingStr.trim().equals("قبل المرفوض")) {
                                    discountBeforeRejection = true;
                                }
                                
                                ContractStatus cStatus = ContractStatus.ACTIVE;
                                if (statusStr != null && !statusStr.trim().isEmpty()) {
                                    String s = statusStr.trim().toUpperCase();
                                    if (s.equals("مسودة") || s.equals("DRAFT")) cStatus = ContractStatus.DRAFT;
                                    else if (s.equals("موقوف") || s.equals("SUSPENDED")) cStatus = ContractStatus.SUSPENDED;
                                }

                                java.time.LocalDate startDate = java.time.LocalDate.of(java.time.LocalDate.now().getYear(), 1, 1);
                                if (startDateStr != null && !startDateStr.trim().isEmpty()) {
                                    try {
                                        startDate = java.time.LocalDate.parse(startDateStr.trim());
                                    } catch (Exception ignored) {}
                                }
                                
                                int durationMonths = 12;
                                if (durationStr != null && !durationStr.trim().isEmpty()) {
                                    try {
                                        durationMonths = (int) Double.parseDouble(durationStr.trim());
                                    } catch (NumberFormatException ignored) {}
                                }
                                java.time.LocalDate endDate = startDate.plusMonths(durationMonths).minusDays(1);

                                ProviderContractCreateDto contractDto = ProviderContractCreateDto.builder()
                                        .providerId(provider.getId())
                                        .status(cStatus)
                                        .pricingModel(PricingModel.DISCOUNT)
                                        .discountPercent(discount)
                                        .discountBeforeRejection(discountBeforeRejection)
                                        .startDate(startDate)
                                        .endDate(endDate)
                                        .build();
                                contractService.create(contractDto);
                                log.debug("[ProviderImport] Created contract for provider: {} with status: {} and discount: {}", 
                                    provider.getId(), cStatus, discount);
                            } catch (Exception e) {
                                log.error("[ProviderImport] Failed to create contract for provider: {}", provider.getId(), e);
                            }

                            // 2. Create a User for the new provider
                            try {
                                String name = provider.getName() != null ? provider.getName() : "";
                                String licenseNumber = provider.getLicenseNumber() != null ? provider.getLicenseNumber() : "";
                                String email = provider.getEmail();
                                
                                String excelUsername = getCellValue(row, columnIndices.get("username"));
                                String username;
                                if (excelUsername != null && !excelUsername.trim().isEmpty()) {
                                    username = excelUsername.trim();
                                } else {
                                    String englishName = name.trim().replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
                                    username = (englishName.isEmpty() ? licenseNumber.trim().toLowerCase() : englishName) + "@tpa";
                                }
                                
                                UserCreateDto userDto = UserCreateDto.builder()
                                        .username(username)
                                        .password("P@123456")
                                        .fullName(name.trim())
                                        .email(email != null && !email.trim().isEmpty() ? email.trim() : username + ".local")
                                        .userType("PROVIDER_STAFF")
                                        .providerId(provider.getId())
                                        .build();
                                userService.create(userDto);
                                log.debug("[ProviderImport] Created user {} for provider: {}", username, provider.getId());
                            } catch (Exception e) {
                                log.error("[ProviderImport] Failed to create user for provider: {}", provider.getId(), e);
                            }
                        }
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
            
            String messageAr = String.format("تم إنشاء %d وتحديث %d مقدم خدمة، تم تخطي %d، فشل %d",
                summary.getCreated(), summary.getUpdated(), summary.getSkipped(), summary.getRejected() + summary.getFailed());
            String messageEn = String.format("Created %d, updated %d providers, skipped %d, failed %d",
                summary.getCreated(), summary.getUpdated(), summary.getSkipped(), summary.getRejected() + summary.getFailed());
            
            log.info("[ProviderImport] Import completed: {}", messageEn);
            
            return ExcelImportResult.builder()
                .summary(summary)
                .errors(errors)
                .success((summary.getCreated() + summary.getUpdated()) > 0)
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
        indices.put("username", parserService.findColumnIndex(headerRow, 
            "username", "اسم المستخدم"));
        indices.put("network", parserService.findColumnIndex(headerRow, 
            "network", "الشبكة"));
        indices.put("address", parserService.findColumnIndex(headerRow, 
            "address", "العنوان"));
        indices.put("start_date", parserService.findColumnIndex(headerRow, 
            "start_date", "تاريخ البداية", "تاريخ بداية العقد"));
        indices.put("duration_months", parserService.findColumnIndex(headerRow, 
            "duration_months", "المدة بالأشهر", "المدة"));
        indices.put("discount", parserService.findColumnIndex(headerRow, 
            "discount", "نسبة الخصم", "الخصم"));
        indices.put("discount_timing", parserService.findColumnIndex(headerRow, 
            "discount_timing", "آلية الخصم", "الية الخصم"));
        indices.put("status", parserService.findColumnIndex(headerRow, 
            "status", "الحالة", "حالة العقد"));
        
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
        
        // Use provider name (Arabic-only system)
        String nameValue = (providerName != null && !providerName.trim().isEmpty()) 
            ? providerName.trim() 
            : "";

        Optional<Provider> existingOpt = providerRepository.findByName(nameValue);
        Provider provider;

        String networkStatusStr = getCellValue(row, columnIndices.get("network"));
        Provider.NetworkTier networkTier = Provider.NetworkTier.IN_NETWORK; // default
        if (networkStatusStr != null && (networkStatusStr.trim().equalsIgnoreCase("خارج الشبكة") 
                || networkStatusStr.trim().equalsIgnoreCase("OUT_OF_NETWORK")
                || networkStatusStr.trim().equalsIgnoreCase("OUT"))) {
            networkTier = Provider.NetworkTier.OUT_OF_NETWORK;
        }

        if (existingOpt.isPresent()) {
            provider = existingOpt.get();
            provider.setProviderType(providerType);
            if (city != null && !city.trim().isEmpty()) {
                provider.setCity(city.trim());
            }
            provider.setPhone(getCellValue(row, columnIndices.get("phone")));
            provider.setEmail(getCellValue(row, columnIndices.get("email")));
            provider.setAddress(getCellValue(row, columnIndices.get("address")));
            provider.setNetworkStatus(networkTier);
            provider.setActive(true);
            provider.setAllowAllEmployers(true);
        } else {
            // Auto-generate license number
            String licenseNumber = generateLicenseNumber(providerType);
            
            provider = Provider.builder()
                .name(nameValue)
                .licenseNumber(licenseNumber)
                .providerType(providerType)
                .city(city != null ? city.trim() : null)
                .phone(getCellValue(row, columnIndices.get("phone")))
                .email(getCellValue(row, columnIndices.get("email")))
                .address(getCellValue(row, columnIndices.get("address")))
                .networkStatus(networkTier)
                .active(true)
                .allowAllEmployers(true)
                .build();
        }
        
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
        } else if (normalized.equals("CLINIC") || normalized.equals("عياده تخصصية") || normalized.equals("عيادة تخصصية") || normalized.equals("عيادة")) {
            return ProviderType.CLINIC;
        } else if (normalized.equals("CLINIC_DEN") || normalized.equals("عياده اسنان") || normalized.equals("عيادة أسنان") || normalized.equals("عيادة اسنان")) {
            return ProviderType.CLINIC_DEN;
        } else if (normalized.equals("LAB") || normalized.equals("مختبر تحاليل") || normalized.equals("مختبر")) {
            return ProviderType.LAB;
        } else if (normalized.equals("PHARMACY") || normalized.equals("صيدلية")) {
            return ProviderType.PHARMACY;
        } else if (normalized.equals("RADIOLOGY") || normalized.equals("مركز أشعة") || normalized.equals("أشعة")) {
            return ProviderType.RADIOLOGY;
        } else if (normalized.equals("PHYSIOTHERAPY") || normalized.equals("مركز علاج طبيعي")) {
            return ProviderType.PHYSIOTHERAPY;
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
