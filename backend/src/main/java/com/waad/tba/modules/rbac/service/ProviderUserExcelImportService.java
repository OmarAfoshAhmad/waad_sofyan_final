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

    private static final String DEFAULT_PASSWORD = "Aa@1234567";
    private static final String PROVIDER_USERS_MODULE = "ProviderUsers";

    public byte[] generateTemplate() throws IOException {
        List<ExcelTemplateColumn> columns = getTemplateColumns();
        List<ExcelLookupData> lookups = getLookupData();
        return excelTemplateService.generateTemplate(PROVIDER_USERS_MODULE, columns, lookups);
    }

    public ExcelImportResult importUsers(MultipartFile file) {
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

                    if (password == null || password.trim().isEmpty()) {
                        password = DEFAULT_PASSWORD;
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
                } catch (Exception e) {
                    summary.setFailed(summary.getFailed() + 1);
                    result.getErrors().add(com.waad.tba.common.excel.dto.ExcelImportResult.ImportError.builder()
                            .rowNumber(rowNumber)
                            .messageAr("استيراد المستخدم: " + e.getMessage())
                            .build());
                }
            }
            workbook.close();
        } catch (Exception e) {
            log.error("Failed to parse Excel file", e);
            result.getErrors().add(com.waad.tba.common.excel.dto.ExcelImportResult.ImportError.builder()
                    .rowNumber(0)
                    .messageAr("فشل في قراءة الملف: " + e.getMessage())
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
