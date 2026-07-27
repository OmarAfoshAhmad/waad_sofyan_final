package com.waad.tba.modules.rbac.service;

import com.waad.tba.common.excel.dto.ExcelImportResult;
import com.waad.tba.common.excel.dto.ExcelLookupData;
import com.waad.tba.common.excel.dto.ExcelTemplateColumn;
import com.waad.tba.common.excel.service.ExcelParserService;
import com.waad.tba.common.excel.service.ExcelTemplateService;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.rbac.dto.UserCreateDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderUserExcelImportService {

    private final ExcelTemplateService excelTemplateService;
    private final ExcelParserService excelParserService;
    private final ProviderRepository providerRepository;
    private final UserService userService;

    private static final String PROVIDER_USERS_MODULE = "ProviderUsers";
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    /**
     * A single hardcoded default password shared by every imported row with
     * a blank password column would mean any bulk import (e.g. 200 provider
     * staff) creates that many accounts sharing one well-known credential —
     * generate a unique random one per row instead.
     */
    private String generateRandomPassword() {
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lower = "abcdefghijkmnpqrstuvwxyz";
        String digits = "23456789";
        String symbols = "@#$%";
        StringBuilder sb = new StringBuilder();
        sb.append(upper.charAt(RANDOM.nextInt(upper.length())));
        sb.append(lower.charAt(RANDOM.nextInt(lower.length())));
        sb.append(digits.charAt(RANDOM.nextInt(digits.length())));
        sb.append(symbols.charAt(RANDOM.nextInt(symbols.length())));
        String all = upper + lower + digits + symbols;
        for (int i = 0; i < 8; i++) {
            sb.append(all.charAt(RANDOM.nextInt(all.length())));
        }
        return sb.toString();
    }

    public byte[] generateTemplate() throws IOException {
        List<ExcelTemplateColumn> columns = getTemplateColumns();
        List<ExcelLookupData> lookups = getLookupData();
        return excelTemplateService.generateTemplate(PROVIDER_USERS_MODULE, columns, lookups);
    }

    public ExcelImportResult importUsers(MultipartFile file) {
        com.waad.tba.common.excel.ExcelUploadValidator.validate(file);

        ExcelImportResult result = new ExcelImportResult();
        com.waad.tba.common.excel.dto.ExcelImportResult.ImportSummary summary = result.getSummary();

        try {
            org.apache.poi.ss.usermodel.Workbook workbook = excelParserService.openWorkbook(file);
            org.apache.poi.ss.usermodel.Sheet sheet = excelParserService.getDataSheet(workbook);
            
            int lastRow = sheet.getLastRowNum();
            for (int rowIdx = 1; rowIdx <= lastRow; rowIdx++) {
                org.apache.poi.ss.usermodel.Row row = sheet.getRow(rowIdx);
                if (excelParserService.isEmptyRow(row)) {
                    continue;
                }
                
                int rowNumber = rowIdx + 1;
                summary.setTotalRows(summary.getTotalRows() + 1);

                try {
                    String username = excelParserService.getCellValueAsString(row.getCell(0));
                    String fullName = excelParserService.getCellValueAsString(row.getCell(1));
                    String email = excelParserService.getCellValueAsString(row.getCell(2));
                    String phone = excelParserService.getCellValueAsString(row.getCell(3));
                    String providerValue = excelParserService.getCellValueAsString(row.getCell(4));
                    String password = excelParserService.getCellValueAsString(row.getCell(5));

                    boolean generatedPassword = password == null || password.trim().isEmpty();
                    if (generatedPassword) {
                        password = generateRandomPassword();
                    }

                    if (providerValue == null || !providerValue.contains(" - ")) {
                        throw new IllegalArgumentException("يجب اختيار المرفق الصحي من القائمة المنسدلة");
                    }

                    Long providerId = Long.parseLong(providerValue.split(" - ")[0].trim());

                    UserCreateDto dto = UserCreateDto.builder()
                            .username(username)
                            .fullName(fullName)
                            .email(email)
                            .phone(phone)
                            .password(password)
                            .userType("PROVIDER_STAFF")
                            .providerId(providerId)
                            .build();

                    userService.create(dto);
                    summary.setCreated(summary.getCreated() + 1);
                    if (generatedPassword) {
                        log.info("Provider user '{}' created with an auto-generated password — share it with them out of band; it is not returned in the API response.", username);
                    }
                } catch (IllegalArgumentException e) {
                    // Validation errors are meant for the caller (e.g. "select a
                    // valid provider"), unlike unexpected internal exceptions below.
                    summary.setFailed(summary.getFailed() + 1);
                    result.getErrors().add(com.waad.tba.common.excel.dto.ExcelImportResult.ImportError.builder()
                            .rowNumber(rowNumber)
                            .messageAr("استيراد المستخدم: " + e.getMessage())
                            .build());
                } catch (Exception e) {
                    log.error("Unexpected error importing provider user at row {}", rowNumber, e);
                    summary.setFailed(summary.getFailed() + 1);
                    result.getErrors().add(com.waad.tba.common.excel.dto.ExcelImportResult.ImportError.builder()
                            .rowNumber(rowNumber)
                            .messageAr("تعذر استيراد هذا الصف")
                            .build());
                }
            }
            workbook.close();
        } catch (Exception e) {
            log.error("Failed to parse Excel file", e);
            result.getErrors().add(com.waad.tba.common.excel.dto.ExcelImportResult.ImportError.builder()
                    .rowNumber(0)
                    .messageAr("فشل في قراءة الملف")
                    .build());
        }

        return result;
    }

    private List<ExcelTemplateColumn> getTemplateColumns() {
        List<ExcelTemplateColumn> columns = new ArrayList<>();
        columns.add(ExcelTemplateColumn.builder().name("username").nameAr("اسم المستخدم").required(true).example("ex: ahmed_dr").type(ExcelTemplateColumn.ColumnType.TEXT).build());
        columns.add(ExcelTemplateColumn.builder().name("fullName").nameAr("الاسم الكامل").required(true).example("ex: د. أحمد محمد").type(ExcelTemplateColumn.ColumnType.TEXT).build());
        columns.add(ExcelTemplateColumn.builder().name("email").nameAr("البريد الإلكتروني").required(true).example("ex: ahmed@provider.com").type(ExcelTemplateColumn.ColumnType.TEXT).build());
        columns.add(ExcelTemplateColumn.builder().name("phone").nameAr("رقم الهاتف").required(false).example("ex: 0500000000").type(ExcelTemplateColumn.ColumnType.TEXT).build());
        columns.add(ExcelTemplateColumn.builder().name("provider").nameAr("المرفق الصحي").required(true).example("اختر من القائمة").type(ExcelTemplateColumn.ColumnType.TEXT).build());
        columns.add(ExcelTemplateColumn.builder().name("password").nameAr("كلمة المرور").required(false).example("يترك فارغاً لتعيين الكلمة الافتراضية").type(ExcelTemplateColumn.ColumnType.TEXT).build());
        return columns;
    }

    private List<ExcelLookupData> getLookupData() {
        List<Provider> providers = providerRepository.findByActiveTrue(Pageable.unpaged()).getContent();
        List<String> providerStrings = new ArrayList<>();
        
        for (Provider provider : providers) {
            providerStrings.add(provider.getId() + " - " + provider.getName());
        }
        
        List<ExcelLookupData> lookups = new ArrayList<>();
        
        List<String> headers = new ArrayList<>();
        headers.add("المرافق");
        
        List<List<String>> data = new ArrayList<>();
        for(String providerStr : providerStrings) {
            List<String> row = new ArrayList<>();
            row.add(providerStr);
            data.add(row);
        }
        
        lookups.add(ExcelLookupData.builder()
                .sheetName("provider")
                .sheetNameAr("المرافق الصحية")
                .headers(headers)
                .data(data)
                .build());
        
        return lookups;
    }
}
