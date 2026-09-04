package com.waad.tba.modules.provider.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.waad.tba.modules.providercontract.dto.ProviderContractCreateDto;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.rbac.dto.UserCreateDto;
import com.waad.tba.modules.rbac.dto.UserUpdateDto;
import com.waad.tba.modules.rbac.dto.UserResponseDto;
import com.waad.tba.modules.providercontract.dto.ProviderContractResponseDto;
import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import org.springframework.data.domain.PageImpl;
import java.util.List;
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
    void providerImportReadsExcelTenPercentAsTenAndPreservesBeforeRejection() throws Exception {
        when(providerRepository.findByName("مستشفى الاختبار")).thenReturn(Optional.empty());
        when(providerRepository.save(any(Provider.class))).thenAnswer(invocation -> {
            Provider provider = invocation.getArgument(0);
            provider.setId(100L);
            return provider;
        });

        service.importFromExcel(buildExcelFileWithContractTerms(0.10, "0%", "قبل المرفوض"));

        ArgumentCaptor<ProviderContractCreateDto> captor = ArgumentCaptor.forClass(ProviderContractCreateDto.class);
        verify(contractService).create(captor.capture());
        assertEquals(0, captor.getValue().getDiscountPercent().compareTo(new java.math.BigDecimal("10.00")));
        assertTrue(captor.getValue().getDiscountBeforeRejection());
        assertEquals(java.time.LocalDate.of(2025, 1, 1), captor.getValue().getStartDate());
        assertEquals(java.time.LocalDate.of(2025, 12, 31), captor.getValue().getEndDate());
    }

    @Test
    void providerImportDoesNotSilentlySkipARealProviderEnteredInExcelRowTwo() throws Exception {
        when(providerRepository.findByName("مستشفى الاختبار")).thenReturn(Optional.empty());
        when(providerRepository.save(any(Provider.class))).thenAnswer(invocation -> {
            Provider provider = invocation.getArgument(0);
            provider.setId(100L);
            return provider;
        });

        ExcelImportResult result = service.importFromExcel(buildExcelFileWithFirstRealRow());

        assertTrue(result.isSuccess());
        assertEquals(1, result.getSummary().getCreated());
        verify(contractService).create(any(ProviderContractCreateDto.class));
    }

    @Test
    void providerImportFailsClosedInsteadOfKeepingProviderWithoutContract() throws Exception {
        when(providerRepository.findByName("مستشفى الاختبار")).thenReturn(Optional.empty());
        when(providerRepository.save(any(Provider.class))).thenAnswer(invocation -> {
            Provider provider = invocation.getArgument(0);
            provider.setId(100L);
            return provider;
        });
        when(contractService.create(any(ProviderContractCreateDto.class)))
                .thenThrow(new BusinessRuleException("تعذر إنشاء العقد"));

        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> service.importFromExcel(buildExcelFile("StrongPass@2026")));
        assertTrue(error.getMessage().contains("أُلغي الاستيراد بالكامل"));
        verify(userService, never()).create(any(UserCreateDto.class));
    }

    @Test
    void providerReimportUpdatesLinkedUserInsteadOfLeavingOldGeneratedEmail() throws Exception {
        Provider existing = Provider.builder().id(100L).name("مستشفى الاختبار")
                .licenseNumber("HOS-100").providerType(Provider.ProviderType.HOSPITAL).active(true).build();
        when(providerRepository.findByName("مستشفى الاختبار")).thenReturn(Optional.of(existing));
        when(providerRepository.save(any(Provider.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(contractService.findByProvider(org.mockito.ArgumentMatchers.eq(100L), any()))
                .thenReturn(new PageImpl<>(List.of(ProviderContractResponseDto.builder()
                        .id(500L).contractCode("CON-500").status(ContractStatus.ACTIVE).build())));
        when(userService.findByProviderId(100L)).thenReturn(List.of(UserResponseDto.builder()
                .id(700L).username("old@tpa").email("old@tpa.local").build()));

        service.importFromExcel(buildExcelFile("", "new-provider@example.com"));

        ArgumentCaptor<UserUpdateDto> captor = ArgumentCaptor.forClass(UserUpdateDto.class);
        verify(userService).update(org.mockito.ArgumentMatchers.eq(700L), captor.capture());
        assertEquals("new-provider@example.com", captor.getValue().getEmail());
        assertEquals(100L, captor.getValue().getProviderId());
    }

    @Test
    void providerReimportWithMultipleUsersUpdatesOnlyTheExplicitlyMatchingAccount() throws Exception {
        Provider existing = Provider.builder().id(100L).name("مستشفى الاختبار")
                .licenseNumber("HOS-100").providerType(Provider.ProviderType.HOSPITAL).active(false).build();
        when(providerRepository.findByName("مستشفى الاختبار")).thenReturn(Optional.of(existing));
        when(providerRepository.save(any(Provider.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(contractService.findByProvider(org.mockito.ArgumentMatchers.eq(100L), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        when(userService.findByProviderId(100L)).thenReturn(List.of(
                UserResponseDto.builder().id(700L).username("first").email("first@tpa.local").build(),
                UserResponseDto.builder().id(701L).username("testprovider@tpa").email("second@tpa.local").build()));

        service.importFromExcel(buildExcelFile("", ""));

        ArgumentCaptor<UserUpdateDto> captor = ArgumentCaptor.forClass(UserUpdateDto.class);
        verify(userService, times(1)).update(org.mockito.ArgumentMatchers.eq(701L), captor.capture());
        verify(userService, never()).update(org.mockito.ArgumentMatchers.eq(700L), any(UserUpdateDto.class));
        assertEquals("testprovider@tpa", captor.getValue().getUsername());
        assertEquals("second@tpa.local", captor.getValue().getEmail());
    }

    @Test
    void providerReimportWithMultipleUsersRejectsAmbiguousNewUsernameClearly() throws Exception {
        Provider existing = Provider.builder().id(100L).name("مستشفى الاختبار")
                .licenseNumber("HOS-100").providerType(Provider.ProviderType.HOSPITAL).active(false).build();
        when(providerRepository.findByName("مستشفى الاختبار")).thenReturn(Optional.of(existing));
        when(providerRepository.save(any(Provider.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(contractService.findByProvider(org.mockito.ArgumentMatchers.eq(100L), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        when(userService.findByProviderId(100L)).thenReturn(List.of(
                UserResponseDto.builder().id(700L).username("first").email("first@tpa.local").build(),
                UserResponseDto.builder().id(701L).username("second").email("second@tpa.local").build()));

        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> service.importFromExcel(buildExcelFile("", "")));

        assertTrue(error.getMessage().contains("مرتبط بأكثر من مستخدم"));
        assertTrue(error.getMessage().contains("testprovider@tpa"));
        verify(userService, never()).update(any(), any(UserUpdateDto.class));
    }

    @Test
    void providerReimportAtomicallyCreatesMissingContract() throws Exception {
        Provider existing = Provider.builder().id(100L).name("مستشفى الاختبار")
                .licenseNumber("HOS-100").providerType(Provider.ProviderType.HOSPITAL).active(true).build();
        when(providerRepository.findByName("مستشفى الاختبار")).thenReturn(Optional.of(existing));
        when(providerRepository.save(any(Provider.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(contractService.findByProvider(org.mockito.ArgumentMatchers.eq(100L), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        when(userService.findByProviderId(100L)).thenReturn(List.of(UserResponseDto.builder()
                .id(700L).username("provider@tpa").email("provider@tpa.local").build()));

        ExcelImportResult result = service.importFromExcel(buildExcelFile("", "provider@example.com"));

        assertTrue(result.isSuccess());
        ArgumentCaptor<ProviderContractCreateDto> contract = ArgumentCaptor.forClass(ProviderContractCreateDto.class);
        verify(contractService).create(contract.capture());
        assertEquals(100L, contract.getValue().getProviderId());
        assertEquals(0, contract.getValue().getDiscountPercent().compareTo(new java.math.BigDecimal("10.00")));
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

    private MockMultipartFile buildExcelFileWithContractTerms(double discount, String format, String timing) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Data");
            var header = sheet.createRow(0);
            String[] headers = {"provider_name", "provider_type", "discount", "discount_timing", "status",
                    "start_date", "duration_months"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            sheet.createRow(1).createCell(0).setCellValue("example");
            var row = sheet.createRow(2);
            row.createCell(0).setCellValue("مستشفى الاختبار");
            row.createCell(1).setCellValue("HOSPITAL");
            var discountCell = row.createCell(2);
            discountCell.setCellValue(discount);
            var style = workbook.createCellStyle();
            style.setDataFormat(workbook.createDataFormat().getFormat(format));
            discountCell.setCellStyle(style);
            row.createCell(3).setCellValue(timing);
            row.createCell(4).setCellValue("ACTIVE");
            row.createCell(5).setCellValue("01/01/2025");
            row.createCell(6).setCellValue(12);
            workbook.write(output);
            return new MockMultipartFile("file", "providers.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }

    private MockMultipartFile buildExcelFileWithFirstRealRow() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Data");
            var header = sheet.createRow(0);
            String[] headers = {"provider_name", "provider_type", "username", "initial_password",
                    "start_date", "duration_months"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("مستشفى الاختبار");
            row.createCell(1).setCellValue("HOSPITAL");
            row.createCell(2).setCellValue("first-row-provider");
            row.createCell(3).setCellValue("");
            row.createCell(4).setCellValue("2025-01-01");
            row.createCell(5).setCellValue("12");
            workbook.write(output);
            return new MockMultipartFile("file", "providers.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }
}
