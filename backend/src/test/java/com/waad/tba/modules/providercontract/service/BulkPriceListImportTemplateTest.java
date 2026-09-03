package com.waad.tba.modules.providercontract.service;

import com.waad.tba.modules.claimcontext.service.ClaimContextSourceResolver;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.ByteArrayInputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BulkPriceListImportTemplateTest {
    @Test
    void generatedTemplateMatchesBulkImporterHeaders() throws Exception {
        BulkPriceListImportService service = new BulkPriceListImportService(
                mock(ProviderRepository.class),
                mock(ProviderContractRepository.class),
                mock(ProviderContractPricingItemRepository.class),
                mock(MedicalCategoryRepository.class),
                mock(ProviderContractTermsService.class),
                mock(ClaimContextSourceResolver.class),
                mock(PlatformTransactionManager.class));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(service.generateTemplate()))) {
            var data = workbook.getSheet("كل النتائج");
            assertThat(data).isNotNull();
            assertThat(data.getRow(0).getCell(0).getStringCellValue()).isEqualTo("المرفق");
            assertThat(data.getRow(0).getCell(2).getStringCellValue()).isEqualTo("الكود الأصلي");
            assertThat(data.getRow(0).getCell(3).getStringCellValue()).isEqualTo("اسم الخدمة عربي");
            assertThat(data.getRow(0).getCell(5).getStringCellValue()).isEqualTo("السعر");
            assertThat(data.getRow(0).getCell(8).getStringCellValue()).isEqualTo("كود CAT");
            assertThat(workbook.getSheet("تعليمات")).isNotNull();

            var detector = BulkPriceListImportService.class.getDeclaredMethod(
                    "detectColumns", org.apache.poi.ss.usermodel.Row.class);
            detector.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Integer> columns = (Map<String, Integer>) detector.invoke(service, data.getRow(0));
            assertThat(columns).containsEntry("provider", 0)
                    .containsEntry("code", 2)
                    .containsEntry("nameAr", 3)
                    .containsEntry("nameEn", 4)
                    .containsEntry("price", 5)
                    .containsEntry("mainCat", 6)
                    .containsEntry("subCat", 7)
                    .containsEntry("catCode", 8);
        }
    }
}
