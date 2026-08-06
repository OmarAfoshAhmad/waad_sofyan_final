package com.waad.tba.modules.provider.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.waad.tba.common.excel.dto.ExcelImportResult;
import com.waad.tba.common.excel.service.ExcelParserService;
import com.waad.tba.common.excel.service.ExcelTemplateService;
import com.waad.tba.common.entity.SystemSetting;
import com.waad.tba.common.repository.SystemSettingRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.service.ProviderContractService;
import com.waad.tba.modules.rbac.dto.UserCreateDto;
import com.waad.tba.modules.rbac.service.UserService;

@ExtendWith(MockitoExtension.class)
class ProviderExcelTemplateServiceSecurityTest {

    @Mock
    private ExcelTemplateService templateService;

    @Mock
    private ProviderRepository providerRepository;

    @Mock
    private ProviderContractService contractService;

    @Mock
    private UserService userService;

    @Mock
    private SystemSettingRepository systemSettingRepository;

    private ProviderExcelTemplateService service;

    @BeforeEach
    void setUp() {
        service = new ProviderExcelTemplateService(
                templateService,
                new ExcelParserService(),
                providerRepository,
                contractService,
                userService,
                systemSettingRepository);
    }

    @Test
    void providerImportDoesNotCreateUserWhenInitialPasswordIsBlank() throws Exception {
        when(providerRepository.findByName("مستشفى الاختبار")).thenReturn(Optional.empty());
        when(providerRepository.save(any(Provider.class))).thenAnswer(invocation -> {
            Provider provider = invocation.getArgument(0);
            provider.setId(100L);
            return provider;
        });

        ExcelImportResult result = service.importFromExcel(buildExcelFile(""));

        assertTrue(result.isSuccess());
        assertEquals(1, result.getSummary().getCreated());
        verify(userService, never()).create(any(UserCreateDto.class));
    }

    @Test
    void providerImportUsesExplicitInitialPasswordOnly() throws Exception {
        when(providerRepository.findByName("مستشفى الاختبار")).thenReturn(Optional.empty());
        when(providerRepository.save(any(Provider.class))).thenAnswer(invocation -> {
            Provider provider = invocation.getArgument(0);
            provider.setId(100L);
            return provider;
        });

        service.importFromExcel(buildExcelFile("StrongPass@2026"));

        ArgumentCaptor<UserCreateDto> captor = ArgumentCaptor.forClass(UserCreateDto.class);
        verify(userService).create(captor.capture());
        assertEquals("StrongPass@2026", captor.getValue().getPassword());
    }

    @Test
    void providerImportGeneratesUserEmailWithConfiguredDomainWhenEmailIsBlank() throws Exception {
        when(providerRepository.findByName("مستشفى الاختبار")).thenReturn(Optional.empty());
        when(providerRepository.save(any(Provider.class))).thenAnswer(invocation -> {
            Provider provider = invocation.getArgument(0);
            provider.setId(100L);
            return provider;
        });
        when(systemSettingRepository.findBySettingKey("PROVIDER_USER_EMAIL_DOMAIN"))
                .thenReturn(Optional.of(SystemSetting.builder().settingValue("providers.waad.ly").build()));

        service.importFromExcel(buildExcelFile("StrongPass@2026", ""));

        ArgumentCaptor<UserCreateDto> captor = ArgumentCaptor.forClass(UserCreateDto.class);
        verify(userService).create(captor.capture());
        assertEquals("testprovider@providers.waad.ly", captor.getValue().getEmail());
    }

    @Test
    void legacyProviderExcelEndpointAndHardcodedPasswordMustNotReturn() throws Exception {
        assertTrue(Files.notExists(Path.of(
                "src/main/java/com/waad/tba/modules/provider/controller/ProviderExcelController.java")));
        assertTrue(Files.notExists(Path.of(
                "src/main/java/com/waad/tba/modules/provider/service/ProviderExcelService.java")));

        String source = Files.readString(Path.of(
                "src/main/java/com/waad/tba/modules/provider/service/ProviderExcelTemplateService.java"));
        String frontend = Files.readString(Path.of(
                "../frontend/src/services/api/providers.service.js"));

        assertTrue(!source.contains("P@123456"));
        assertTrue(!frontend.contains("/import/excel"));
    }

    private MockMultipartFile buildExcelFile(String initialPassword) throws Exception {
        return buildExcelFile(initialPassword, "test-provider@example.com");
    }

    private MockMultipartFile buildExcelFile(String initialPassword, String email) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Data");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("provider_name");
            header.createCell(1).setCellValue("provider_type");
            header.createCell(2).setCellValue("email");
            header.createCell(3).setCellValue("username");
            header.createCell(4).setCellValue("initial_password");

            var ignoredTemplateExampleRow = sheet.createRow(1);
            ignoredTemplateExampleRow.createCell(0).setCellValue("example");

            var row = sheet.createRow(2);
            row.createCell(0).setCellValue("مستشفى الاختبار");
            row.createCell(1).setCellValue("HOSPITAL");
            row.createCell(2).setCellValue(email);
            row.createCell(3).setCellValue("testprovider@tpa");
            row.createCell(4).setCellValue(initialPassword);

            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "providers.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray());
        }
    }
}
