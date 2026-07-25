package com.waad.tba.modules.providercontract.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for SECTION_02 HIGH finding #7: pricing-item fields
 * that can originate from provider-portal submissions (service name, code,
 * notes, category) were written into exported Excel cells with no
 * sanitization, allowing formula injection (e.g. "=cmd|'/c calc'!A1") that
 * executes when SUPER_ADMIN/ACCOUNTANT later opens the export.
 * sanitizeForExcel() now prefixes a leading formula-trigger character
 * ('=', '+', '-', '@', tab, CR) with a single quote so spreadsheet
 * applications treat the cell as literal text.
 */
class PriceListExcelTemplateServiceFormulaInjectionTest {

    private static String sanitize(String value) throws Exception {
        Method m = PriceListExcelTemplateService.class
                .getDeclaredMethod("sanitizeForExcel", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, value);
    }

    @Test
    void prefixesLeadingEqualsSign() throws Exception {
        assertThat(sanitize("=cmd|'/c calc'!A1")).startsWith("'=");
    }

    @Test
    void prefixesLeadingPlusMinusAtSigns() throws Exception {
        assertThat(sanitize("+HYPERLINK(\"http://evil\")")).startsWith("'+");
        assertThat(sanitize("-1+1")).startsWith("'-");
        assertThat(sanitize("@SUM(1,1)")).startsWith("'@");
    }

    @Test
    void leavesOrdinaryTextUntouched() throws Exception {
        assertThat(sanitize("فحص شامل")).isEqualTo("فحص شامل");
        assertThat(sanitize("MC-001")).isEqualTo("MC-001");
    }

    @Test
    void handlesNullAndEmptySafely() throws Exception {
        assertThat(sanitize(null)).isNull();
        assertThat(sanitize("")).isEmpty();
    }
}
