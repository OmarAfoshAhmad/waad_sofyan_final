package com.waad.tba.modules.benefitpolicy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class BenefitOpeningBalanceImportDto {
    private BenefitOpeningBalanceImportDto() {}

    public record Row(int rowNumber, String cardNumber, String memberName, String bucketCode,
                      String bucketName, LocalDate periodStart, LocalDate periodEnd,
                      BigDecimal amountLimit, BigDecimal usedAmount, BigDecimal remainingAmount,
                      Integer usedTimes, Integer usedDays, String sourceReference,
                      boolean alreadyImported, List<String> errors, List<String> warnings) {
        public boolean valid() { return errors == null || errors.isEmpty(); }
    }

    public record Preview(String batchId, int totalRows, int validRows, int invalidRows,
                          int alreadyImportedRows, BigDecimal totalOpeningUsage,
                          List<Row> rows) {}

    public record Result(String batchId, int totalRows, int importedRows, int skippedRows,
                         BigDecimal importedOpeningUsage) {}
}

