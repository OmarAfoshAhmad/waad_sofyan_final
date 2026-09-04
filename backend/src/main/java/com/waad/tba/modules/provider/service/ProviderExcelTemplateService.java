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
import com.waad.tba.common.repository.SystemSettingRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.dto.ProviderContractCreateDto;
import com.waad.tba.modules.providercontract.dto.ProviderContractUpdateDto;
import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import com.waad.tba.modules.providercontract.entity.ProviderContract.PricingModel;
import com.waad.tba.modules.providercontract.service.ProviderContractService;
import com.waad.tba.modules.rbac.dto.UserCreateDto;
import com.waad.tba.modules.rbac.dto.UserUpdateDto;
import com.waad.tba.modules.rbac.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
    private final SystemSettingRepository systemSettingRepository;

    private static final String PROVIDER_USER_EMAIL_DOMAIN_KEY = "PROVIDER_USER_EMAIL_DOMAIN";
    private static final String DEFAULT_PROVIDER_USER_EMAIL_DOMAIN = "tpa.local";
    
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
                // English codes only: parseProviderType matches literally, and mixing
                // Arabic labels into the dropdown previously let values through that
                // the parser could not resolve. CLINIC_DEN/PHYSIOTHERAPY were also
                // missing here even though the enum and DB constraint accept them.
                .allowedValues(Arrays.asList("HOSPITAL", "CLINIC", "CLINIC_DEN", "LAB",
                                            "PHARMACY", "RADIOLOGY", "PHYSIOTHERAPY", "OPTICS"))
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
                .description("Provider/user email. If empty, the provider user email is generated using the configured domain.")
                .descriptionAr("بريد المرفق وحساب المستخدم. إذا تُرك فارغاً يُولّد بريد المستخدم وفق النطاق المحدد في إعدادات النظام.")
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
                .name("initial_password")
                .nameAr("كلمة المرور الابتدائية")
                .type(ColumnType.TEXT)
                .required(false)
                .example("ChangeMe@2026")
                .description("Optional initial password. If empty, no provider user is created automatically.")
                .descriptionAr("اختيارية. إذا تركت فارغة لن يتم إنشاء مستخدم للمزود تلقائياً.")
                .width(24)
                .build(),

            ExcelTemplateColumn.builder()
                .name("network")
                .nameAr("الشبكة")
                .type(ColumnType.ENUM)
                .required(false)
                .allowedValues(Arrays.asList("داخل الشبكة", "خارج الشبكة"))
                .example("داخل الشبكة")
                .description("Network status (Inside Network / Outside Network), default is Inside")
                .descriptionAr("حالة الشبكة (داخل الشبكة / خارج الشبكة)، الافتراضي داخل الشبكة")
                .width(20)
                .build(),
                
            ExcelTemplateColumn.builder()
                .name("allow_all_employers")
                .nameAr("شبكة عامة")
                .type(ColumnType.ENUM)
                .required(false)
                .allowedValues(Arrays.asList("نعم", "لا"))
                .example("نعم")
                .description("Is global network (نعم / لا), default is نعم")
                .descriptionAr("هل هي شبكة عامة (نعم / لا)، الافتراضي نعم")
                .width(15)
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
                Arrays.asList("PHYSIOTHERAPY", "مركز علاج طبيعي"),
                Arrays.asList("OPTICS", "مركز بصريات وعيون")
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
    
    @Transactional(rollbackFor = Exception.class)
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
            
            // A freshly downloaded template contains an example in Excel row 2,
            // but users commonly replace that row with their first real provider.
            // Skip it only while it is still recognisably the template example;
            // never discard a real first record merely because of its row number.
            int firstDataRow = isTemplateExampleRow(sheet.getRow(1)) ? 2 : 1;
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

                        ContractImportValues contractValues = parseContractValues(row, rowNum, columnIndices);
                        
                        if (isUpdate) {
                            summary.setUpdated(summary.getUpdated() + 1);
                            log.debug("[ProviderImport] Updated provider: {}", provider.getLicenseNumber());
                            updateExistingContract(provider, contractValues);
                            updateExistingProviderUsers(provider, row, columnIndices);
                        } else {
                            summary.setCreated(summary.getCreated() + 1);
                            log.debug("[ProviderImport] Created provider: {}", provider.getLicenseNumber());
                            
                            // Create the contract in the same transaction as the provider and user.
                            {
                                ProviderContractCreateDto contractDto = ProviderContractCreateDto.builder()
                                        .providerId(provider.getId())
                                        .status(contractValues.status())
                                        .pricingModel(PricingModel.DISCOUNT)
                                        .discountPercent(contractValues.discount())
                                        .discountBeforeRejection(contractValues.beforeRejection())
                                        .startDate(contractValues.startDate())
                                        .endDate(contractValues.endDate())
                                        .build();
                                contractService.create(contractDto);
                                log.debug("[ProviderImport] Created contract for provider: {}", provider.getId());
                            }

                            // 2. Create a User for the new provider only when an explicit initial password is supplied.
                            {
                                String initialPassword = getCellValue(row, columnIndices.get("initial_password"));
                                if (initialPassword == null || initialPassword.trim().isEmpty()) {
                                    log.info("[ProviderImport] Skipped automatic user creation for provider {} because initial_password is empty",
                                            provider.getId());
                                    continue;
                                }

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
                                
                                String userEmail = email != null && !email.trim().isEmpty()
                                        ? email.trim()
                                        : generateProviderUserEmail(username, licenseNumber);

                                UserCreateDto userDto = UserCreateDto.builder()
                                        .username(username)
                                        .password(initialPassword.trim())
                                        .fullName(name.trim())
                                        .email(userEmail)
                                        .userType("PROVIDER_STAFF")
                                        .providerId(provider.getId())
                                        .build();
                                userService.create(userDto);
                                log.debug("[ProviderImport] Created user {} for provider: {}", username, provider.getId());
                            }
                        }
                    } else {
                        summary.setRejected(summary.getRejected() + 1);
                        String reason = errors.isEmpty() ? "بيانات الصف غير صالحة"
                                : errors.get(errors.size() - 1).getMessageAr();
                        throw new BusinessRuleException(reason);
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
                    throw new BusinessRuleException("فشل الصف " + (rowNum + 1)
                            + "؛ أُلغي الاستيراد بالكامل دون حفظ جزئي: " + e.getMessage());
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
    
    private String generateProviderUserEmail(String username, String licenseNumber) {
        String configuredDomain = systemSettingRepository.findBySettingKey(PROVIDER_USER_EMAIL_DOMAIN_KEY)
                .map(setting -> setting.getSettingValue())
                .orElse(DEFAULT_PROVIDER_USER_EMAIL_DOMAIN);
        String domain = configuredDomain == null ? "" : configuredDomain.trim().toLowerCase(Locale.ROOT);
        domain = domain.replaceFirst("^@+", "");
        if (!domain.matches("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")) {
            log.warn("[ProviderImport] Invalid {} value '{}'; using default domain {}",
                    PROVIDER_USER_EMAIL_DOMAIN_KEY, configuredDomain, DEFAULT_PROVIDER_USER_EMAIL_DOMAIN);
            domain = DEFAULT_PROVIDER_USER_EMAIL_DOMAIN;
        }

        String usernameLocalPart = username == null ? "" : username.split("@", 2)[0];
        String localPart = usernameLocalPart.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "");
        if (localPart.isBlank()) {
            localPart = licenseNumber == null ? "provider" : licenseNumber.trim().toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9._-]", "");
        }
        if (localPart.isBlank()) {
            localPart = "provider";
        }
        return localPart + "@" + domain;
    }

    private ContractImportValues parseContractValues(Row row, int rowNum, Map<String, Integer> columns) {
        BigDecimal discount = parseDiscount(row, columns.get("discount"), rowNum);
        boolean beforeRejection = parseDiscountTiming(getCellValue(row, columns.get("discount_timing")), rowNum);
        ContractStatus status = parseContractStatus(getCellValue(row, columns.get("status")), rowNum);
        LocalDate startDate = parseStartDate(row, columns.get("start_date"), rowNum);
        int durationMonths = parseDuration(getCellValue(row, columns.get("duration_months")), rowNum);
        return new ContractImportValues(discount, beforeRejection, status, startDate,
                startDate.plusMonths(durationMonths).minusDays(1));
    }

    /** Excel stores a displayed 10% numeric cell as 0.10; preserve its displayed meaning. */
    private BigDecimal parseDiscount(Row row, Integer columnIndex, int rowNum) {
        if (columnIndex == null) return new BigDecimal("10.00");
        Cell cell = row.getCell(columnIndex);
        if (cell == null || cell.getCellType() == CellType.BLANK) return new BigDecimal("10.00");

        try {
            BigDecimal value;
            CellType type = cell.getCellType() == CellType.FORMULA
                    ? cell.getCachedFormulaResultType() : cell.getCellType();
            if (type == CellType.NUMERIC) {
                value = BigDecimal.valueOf(cell.getNumericCellValue());
                String format = cell.getCellStyle() == null ? "" : cell.getCellStyle().getDataFormatString();
                if (format != null && format.contains("%")) value = value.multiply(BigDecimal.valueOf(100));
            } else {
                String raw = getCellValue(row, columnIndex);
                if (raw == null || raw.isBlank()) return new BigDecimal("10.00");
                String normalized = normalizeText(raw).replace("٪", "%");
                boolean percent = normalized.endsWith("%");
                if (percent) normalized = normalized.substring(0, normalized.length() - 1).trim();
                value = new BigDecimal(normalized.replace(',', '.'));
            }
            value = value.setScale(2, RoundingMode.HALF_UP);
            if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("النسبة يجب أن تكون بين 0 و100");
            }
            return value;
        } catch (RuntimeException ex) {
            throw new BusinessRuleException("نسبة الخصم غير صحيحة في صف Excel " + (rowNum + 1)
                    + ": " + getCellValue(row, columnIndex));
        }
    }

    private boolean parseDiscountTiming(String raw, int rowNum) {
        if (raw == null || raw.isBlank()) return false;
        String value = normalizeText(raw).toUpperCase(Locale.ROOT);
        if (Set.of("قبل المرفوض", "قبل", "BEFORE_REJECTION", "BEFORE").contains(value)) return true;
        if (Set.of("بعد المرفوض", "بعد", "AFTER_REJECTION", "AFTER").contains(value)) return false;
        throw new BusinessRuleException("آلية الخصم غير معروفة في صف Excel " + (rowNum + 1) + ": " + raw);
    }

    private ContractStatus parseContractStatus(String raw, int rowNum) {
        if (raw == null || raw.isBlank()) return ContractStatus.ACTIVE;
        String value = normalizeText(raw).toUpperCase(Locale.ROOT);
        return switch (value) {
            case "ACTIVE", "نشط" -> ContractStatus.ACTIVE;
            case "DRAFT", "مسودة" -> ContractStatus.DRAFT;
            case "SUSPENDED", "موقوف" -> ContractStatus.SUSPENDED;
            default -> throw new BusinessRuleException("حالة العقد غير معروفة في صف Excel "
                    + (rowNum + 1) + ": " + raw);
        };
    }

    private LocalDate parseStartDate(Row row, Integer columnIndex, int rowNum) {
        if (columnIndex == null) return LocalDate.of(LocalDate.now().getYear(), 1, 1);
        Cell cell = row.getCell(columnIndex);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return LocalDate.of(LocalDate.now().getYear(), 1, 1);
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC && org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }
            String raw = normalizeText(getCellValue(row, columnIndex));
            for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_LOCAL_DATE,
                    DateTimeFormatter.ofPattern("dd/MM/yyyy"), DateTimeFormatter.ofPattern("dd-MM-yyyy"))) {
                try { return LocalDate.parse(raw, formatter); } catch (DateTimeParseException ignored) { }
            }
        } catch (RuntimeException ignored) { }
        throw new BusinessRuleException("تاريخ بداية العقد غير صحيح في صف Excel " + (rowNum + 1));
    }

    private int parseDuration(String raw, int rowNum) {
        if (raw == null || raw.isBlank()) return 12;
        try {
            BigDecimal value = new BigDecimal(normalizeText(raw).replace(',', '.'));
            int months = value.intValueExact();
            if (months < 1 || months > 1200) throw new ArithmeticException();
            return months;
        } catch (RuntimeException ex) {
            throw new BusinessRuleException("مدة العقد غير صحيحة في صف Excel " + (rowNum + 1) + ": " + raw);
        }
    }

    private void updateExistingContract(Provider provider, ContractImportValues values) {
        var page = contractService.findByProvider(provider.getId(), PageRequest.of(0, 2));
        if (page.isEmpty()) {
            contractService.create(ProviderContractCreateDto.builder()
                    .providerId(provider.getId())
                    .status(values.status())
                    .pricingModel(PricingModel.DISCOUNT)
                    .discountPercent(values.discount())
                    .discountBeforeRejection(values.beforeRejection())
                    .startDate(values.startDate())
                    .endDate(values.endDate())
                    .build());
            log.info("[ProviderImport] Repaired provider {} by creating its missing contract", provider.getId());
            return;
        }
        if (page.getTotalElements() != 1) {
            throw new BusinessRuleException(
                    "للمرفق أكثر من عقد؛ يجب تحديد العقد يدويًا قبل الاستيراد: " + provider.getName());
        }
        var contract = page.getContent().get(0);
        contractService.update(contract.getId(), ProviderContractUpdateDto.builder()
                .pricingModel(PricingModel.DISCOUNT)
                .discountPercent(values.discount())
                .discountBeforeRejection(values.beforeRejection())
                .termsEffectiveFrom(LocalDate.now().isAfter(values.startDate()) ? LocalDate.now() : values.startDate())
                .termsChangeReason("تحديث ذري من قالب استيراد المرافق")
                .startDate(values.startDate())
                .endDate(values.endDate())
                .build());
        if (values.status() != contract.getStatus()) {
            switch (values.status()) {
                case ACTIVE -> contractService.activate(contract.getId());
                case SUSPENDED -> contractService.suspend(contract.getId(), "تحديث ذري من قالب استيراد المرافق");
                case DRAFT -> throw new BusinessRuleException(
                        "لا يمكن إعادة عقد قائم إلى مسودة بواسطة الاستيراد: " + contract.getContractCode());
                default -> throw new BusinessRuleException("حالة عقد غير مدعومة في استيراد المرافق");
            }
        }
    }

    private void updateExistingProviderUsers(Provider provider, Row row, Map<String, Integer> columns) {
        List<com.waad.tba.modules.rbac.dto.UserResponseDto> users = userService.findByProviderId(provider.getId());
        if (users.isEmpty()) {
            String password = getCellValue(row, columns.get("initial_password"));
            if (password == null || password.isBlank()) {
                throw new BusinessRuleException("المرفق موجود ولكن لا يوجد مستخدم مرتبط به؛ أدخل كلمة المرور الابتدائية لإنشائه: "
                        + provider.getName());
            }
            String requestedUsername = getCellValue(row, columns.get("username"));
            String username = requestedUsername == null || requestedUsername.isBlank()
                    ? provider.getLicenseNumber().toLowerCase(Locale.ROOT) + "@tpa"
                    : requestedUsername.trim();
            String email = provider.getEmail() == null || provider.getEmail().isBlank()
                    ? generateProviderUserEmail(username, provider.getLicenseNumber()) : provider.getEmail().trim();
            userService.create(UserCreateDto.builder()
                    .username(username).password(password.trim()).fullName(provider.getName())
                    .email(email).phone(provider.getPhone()).userType("PROVIDER_STAFF")
                    .providerId(provider.getId()).build());
            log.info("[ProviderImport] Repaired provider {} by creating its missing user", provider.getId());
            return;
        }
        String requestedUsername = getCellValue(row, columns.get("username"));
        String normalizedRequested = requestedUsername == null ? "" : requestedUsername.trim();

        if (users.size() > 1) {
            if (normalizedRequested.isBlank()) {
                // The spreadsheet does not identify which account is authoritative.
                // Provider/contract data can still be updated safely, but account
                // identities must remain untouched.
                log.info("[ProviderImport] Provider {} has {} linked users; preserving all accounts because username is blank",
                        provider.getId(), users.size());
                return;
            }
            var matchingUsers = users.stream()
                    .filter(user -> user.getUsername() != null
                            && user.getUsername().equalsIgnoreCase(normalizedRequested))
                    .toList();
            if (matchingUsers.size() != 1) {
                throw new BusinessRuleException("المرفق «" + provider.getName() + "» مرتبط بأكثر من مستخدم ("
                        + users.stream().map(com.waad.tba.modules.rbac.dto.UserResponseDto::getUsername)
                                .filter(java.util.Objects::nonNull).sorted().collect(java.util.stream.Collectors.joining("، "))
                        + "). اسم المستخدم في الملف «" + normalizedRequested
                        + "» لا يطابق حساباً مرتبطاً؛ اترك الخانة فارغة للمحافظة على الحسابات أو أدخل أحد الأسماء الحالية.");
            }
            updateExistingProviderUser(provider, matchingUsers.get(0), matchingUsers.get(0).getUsername());
            return;
        }

        var user = users.get(0);
        String username = normalizedRequested.isBlank() ? user.getUsername() : normalizedRequested;
        updateExistingProviderUser(provider, user, username);
    }

    private void updateExistingProviderUser(Provider provider,
            com.waad.tba.modules.rbac.dto.UserResponseDto user, String username) {
        String email = provider.getEmail() == null || provider.getEmail().isBlank()
                ? user.getEmail() : provider.getEmail().trim();
        if (email == null || email.isBlank()) {
            email = generateProviderUserEmail(username, provider.getLicenseNumber());
        }
        userService.update(user.getId(), UserUpdateDto.builder()
                .username(username)
                .fullName(provider.getName())
                .email(email)
                .phone(provider.getPhone())
                .active(true)
                .userType("PROVIDER_STAFF")
                .providerId(provider.getId())
                .build());
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ')
                .replaceAll("[\\u200E\\u200F\\u202A-\\u202E]", "")
                .replaceAll("\\s+", " ").trim();
    }

    private boolean isTemplateExampleRow(Row row) {
        if (row == null) return false;
        String providerName = normalizeText(getCellValue(row, 0));
        if (providerName.equalsIgnoreCase("example")) return true;
        String providerType = normalizeText(getCellValue(row, 1));
        String username = normalizeText(getCellValue(row, 7));
        return "مستشفى السلام".equals(providerName)
                && "HOSPITAL".equalsIgnoreCase(providerType)
                && "ali_2026".equalsIgnoreCase(username);
    }

    private record ContractImportValues(BigDecimal discount, boolean beforeRejection,
                                        ContractStatus status, LocalDate startDate, LocalDate endDate) { }

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
        indices.put("initial_password", parserService.findColumnIndex(headerRow,
            "initial_password", "كلمة المرور الابتدائية", "كلمة المرور", "password"));
        indices.put("network", parserService.findColumnIndex(headerRow, 
            "network", "الشبكة"));
        indices.put("allow_all_employers", parserService.findColumnIndex(headerRow, 
            "allow_all_employers", "شبكة عامة", "جهات العمل المتعاقدة", "allow_all_employers"));
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

        String allowAllEmployersStr = getCellValue(row, columnIndices.get("allow_all_employers"));
        boolean allowAllEmployers = true; // default
        if (allowAllEmployersStr != null && (allowAllEmployersStr.trim().equalsIgnoreCase("لا") 
                || allowAllEmployersStr.trim().equalsIgnoreCase("false")
                || allowAllEmployersStr.trim().equalsIgnoreCase("no")
                || allowAllEmployersStr.trim().equalsIgnoreCase("0"))) {
            allowAllEmployers = false;
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
            provider.setAllowAllEmployers(allowAllEmployers);
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
                .allowAllEmployers(allowAllEmployers)
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
        } else if (normalized.equals("OPTICS") || normalized.equals("مركز بصريات") || normalized.equals("بصريات")) {
            return ProviderType.OPTICS;
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
