package com.waad.tba.modules.provider.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Exports providers into the SAME workbook shape
 * {@link ProviderExcelTemplateService} generates and imports.
 *
 * The point of matching it exactly -- same sixteen columns, same order, same
 * Arabic headers, same accepted values -- is that an exported file can be
 * edited and fed straight back into the importer. An export whose columns drift
 * from the template it claims to match is worse than none: it looks importable
 * and fails halfway, or worse, imports the wrong column into the wrong field.
 *
 * <p><b>The password column is written empty, deliberately and always.</b> The
 * template carries {@code initial_password} because creating a provider can
 * create its portal user. Filling it on export would put credentials into a
 * spreadsheet that leaves the system, and the system does not hold the original
 * secret anyway -- only its hash. Re-importing a row with an empty password
 * leaves the existing user's password untouched, which is the correct behaviour
 * for a round trip.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderExcelExportService {

    private final ProviderRepository providerRepository;
    private final ProviderContractRepository contractRepository;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * The sheet the importer looks for by name: {@code ExcelParserService
     * .getDataSheet} takes the sheet called "Data" before anything else, and
     * reads its row 0 as the header. Naming the sheet anything else would make
     * this file import as whatever sheet happened to come first.
     */
    private static final String DATA_SHEET = "Data";

    /**
     * Headers in the template's own order, each carrying the Arabic label and
     * the machine name on two lines exactly as the generated template writes
     * them. {@code ExcelParserService.findColumnIndex} splits a header cell on
     * newlines and matches any part, so either line alone would resolve -- but
     * writing both keeps an exported file readable by a human and identical to
     * the template it must be interchangeable with.
     */
    private static final String[] HEADERS = {
            // The leading "*" marks a required column, exactly as the template
            // writes it. findColumnIndex strips it before matching, so it does not
            // affect importing -- but an export that drops it stops being a
            // like-for-like substitute for the template a user may compare against.
            "* اسم مقدم الخدمة\nprovider_name",
            "* نوع المقدم\nprovider_type",
            "المدينة\ncity",
            "الاسم بالإنجليزية\nname_english",
            "رقم الهاتف\nphone",
            "البريد الإلكتروني\nemail",
            "العنوان\naddress",
            "اسم المستخدم\nusername",
            "كلمة المرور الابتدائية\ninitial_password",
            "الشبكة\nnetwork",
            "شبكة عامة\nallow_all_employers",
            "تاريخ بداية العقد\nstart_date",
            "المدة بالأشهر\nduration_months",
            "نسبة الخصم\ndiscount",
            "آلية الخصم\ndiscount_timing",
            "الحالة\nstatus"
    };

    @Transactional(readOnly = true)
    public byte[] exportProviders() throws IOException {
        List<Provider> providers = providerRepository.findAll();
        log.info("[ProviderExport] Exporting {} providers", providers.size());

        // One query for every contract, then grouped in memory -- not one query
        // per provider. With 146 providers that difference is the same N+1 that
        // took the batches screen down.
        Map<Long, ProviderContract> latestByProvider = new HashMap<>();
        for (ProviderContract contract : contractRepository.findAll()) {
            if (contract.getProvider() == null || !Boolean.TRUE.equals(contract.getActive())) {
                continue;
            }
            Long providerId = contract.getProvider().getId();
            ProviderContract held = latestByProvider.get(providerId);
            // A provider can carry several contracts over time; the sheet has one
            // contract row, so it carries the most recently started one.
            if (held == null || startsAfter(contract, held)) {
                latestByProvider.put(providerId, contract);
            }
        }

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(DATA_SHEET);
            sheet.setRightToLeft(true);

            writeHeader(workbook, sheet);

            int rowIndex = 1;
            for (Provider provider : providers) {
                writeProvider(sheet.createRow(rowIndex++), provider, latestByProvider.get(provider.getId()));
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            sheet.createFreezePane(0, 1);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static boolean startsAfter(ProviderContract candidate, ProviderContract held) {
        if (candidate.getStartDate() == null) {
            return false;
        }
        return held.getStartDate() == null || candidate.getStartDate().isAfter(held.getStartDate());
    }

    private void writeHeader(Workbook workbook, Sheet sheet) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        // The header carries the Arabic label and machine name on two lines,
        // the way the template writes them; without wrapping only one shows.
        style.setWrapText(true);

        Row header = sheet.createRow(0);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(style);
        }
    }

    private void writeProvider(Row row, Provider provider, ProviderContract contract) {
        int c = 0;
        text(row, c++, provider.getName());
        text(row, c++, provider.getProviderType() == null ? null : provider.getProviderType().name());
        text(row, c++, provider.getCity());
        // The template's name_english has no column of its own on the entity;
        // leaving it blank is honest, and the importer treats it as optional.
        text(row, c++, null);
        text(row, c++, provider.getPhone());
        text(row, c++, provider.getEmail());
        text(row, c++, provider.getAddress());
        // username: the portal user is created by the importer, and this export
        // does not claim to know which user belongs to which provider.
        text(row, c++, null);
        // initial_password: ALWAYS empty. See the class comment.
        text(row, c++, null);
        text(row, c++, provider.getNetworkStatus() == Provider.NetworkTier.OUT_OF_NETWORK
                ? "خارج الشبكة" : "داخل الشبكة");
        text(row, c++, Boolean.TRUE.equals(provider.getAllowAllEmployers()) ? "نعم" : "لا");

        if (contract == null) {
            text(row, c++, null);
            text(row, c++, null);
            text(row, c++, null);
            text(row, c++, null);
            text(row, c, null);
            return;
        }

        text(row, c++, contract.getStartDate() == null ? null : contract.getStartDate().format(DATE));
        text(row, c++, durationMonths(contract));
        text(row, c++, contract.getDiscountPercent() == null ? null
                : contract.getDiscountPercent().stripTrailingZeros().toPlainString());
        text(row, c++, Boolean.FALSE.equals(contract.getDiscountBeforeRejection())
                ? "بعد المرفوض" : "قبل المرفوض");
        text(row, c, contract.getStatus() == null ? null : contract.getStatus().name());
    }

    /**
     * The sheet stores a duration; the contract stores two dates. Months between
     * them is the value that reproduces the same end date on re-import.
     */
    private static String durationMonths(ProviderContract contract) {
        LocalDate start = contract.getStartDate();
        LocalDate end = contract.getEndDate();
        if (start == null || end == null) {
            return null;
        }
        long months = ChronoUnit.MONTHS.between(start, end);
        return months <= 0 ? null : String.valueOf(months);
    }

    private static void text(Row row, int column, String value) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
    }
}
