package com.waad.tba.common.excel;

/**
 * Prevents Excel/CSV formula injection: a cell value starting with =, +, -, @,
 * tab or CR is interpreted as a formula by Excel/LibreOffice when the file is
 * opened, letting untrusted stored data (names, notes, etc.) execute as a
 * formula (e.g. DDE/command execution payloads). Prefixing with a single
 * quote forces the cell to be treated as literal text.
 */
public final class ExcelSanitizer {

    private ExcelSanitizer() {
    }

    public static String sanitize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
    }
}
