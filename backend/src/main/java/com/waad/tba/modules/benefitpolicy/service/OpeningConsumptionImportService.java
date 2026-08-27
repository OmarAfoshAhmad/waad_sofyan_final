package com.waad.tba.modules.benefitpolicy.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.dto.OpeningConsumptionImportExecuteResultDto;
import com.waad.tba.modules.benefitpolicy.dto.OpeningConsumptionImportPreviewDto;
import com.waad.tba.modules.benefitpolicy.dto.OpeningConsumptionImportRowResult;
import com.waad.tba.modules.benefitpolicy.entity.BenefitBucketConsumption;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.member.service.MemberPolicyResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Preview -> confirmation -> execute for opening consumption, over the writer
 * V188 already shipped ({@link BenefitConsumptionEntryWriter#appendOpeningConsumption}).
 * Mirrors the member Excel import's contract (V186): execute may only run
 * exactly what preview showed, proven by a single-use ticket bound to the
 * file's own bytes.
 *
 * Scope: the POLICY_GENERAL ceiling only -- the ceiling phase 8 exists to fix.
 * A bucket-scoped opening import needs its own period question answered (which
 * bucket period does an import row belong to, with no service date to anchor
 * it) and is left for a later, explicitly scoped pass rather than guessed at
 * here.
 *
 * A row identifies its member by internal id, not national number: this is an
 * operator tool fed from an export of this same system (or one already
 * carrying the id), not an end-user-facing template.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpeningConsumptionImportService {

    private static final int HEADER_ROWS = 1;

    private final MemberRepository memberRepository;
    private final MemberPolicyResolver memberPolicyResolver;
    private final BenefitConsumptionEntryWriter entryWriter;
    private final OpeningConsumptionImportPreviewTicketService ticketService;
    private final JdbcTemplate jdbc;

    public OpeningConsumptionImportPreviewDto parseAndPreview(MultipartFile file, LocalDate referenceDate) {
        requireReferenceDate(referenceDate);
        List<OpeningConsumptionImportRowResult> rows = parseRows(file, referenceDate);
        int valid = (int) rows.stream().filter(OpeningConsumptionImportRowResult::isValid).count();

        String token;
        try {
            token = ticketService.issue(file, referenceDate);
        } catch (Exception e) {
            throw new BusinessRuleException("تعذّر إصدار رمز المعاينة: " + e.getMessage());
        }

        return OpeningConsumptionImportPreviewDto.builder()
                .previewToken(token)
                .totalRows(rows.size())
                .validRows(valid)
                .invalidRows(rows.size() - valid)
                .rows(rows)
                .build();
    }

    /**
     * One transaction for the whole file. Any row that fails re-validation
     * aborts the entire import -- a financial ledger batch has no meaningful
     * "partially posted" state, and preview already told the caller exactly
     * which rows were invalid before they confirmed.
     */
    @Transactional
    public OpeningConsumptionImportExecuteResultDto executeConfirmedImport(
            MultipartFile file, LocalDate referenceDate, String previewToken,
            String batchReference, String reason, String sourceReference) {

        requireReferenceDate(referenceDate);
        if (batchReference == null || batchReference.isBlank()) {
            throw new BusinessRuleException("مرجع الدفعة مطلوب");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("سبب الاستيراد مطلوب");
        }
        if (sourceReference == null || sourceReference.isBlank()) {
            throw new BusinessRuleException("مرجع المصدر مطلوب");
        }

        try {
            ticketService.consume(previewToken, file, referenceDate);
        } catch (Exception e) {
            throw new BusinessRuleException("فشل التحقق من رمز المعاينة: " + e.getMessage());
        }

        List<OpeningConsumptionImportRowResult> rows = parseRows(file, referenceDate);
        List<String> invalidRowNumbers = rows.stream().filter(r -> !r.isValid())
                .map(r -> String.valueOf(r.getRowNumber())).toList();
        if (!invalidRowNumbers.isEmpty()) {
            throw new BusinessRuleException(
                    "الملف يحتوي صفوفاً غير صالحة بعد إعادة الفحص (الصفوف: "
                            + String.join(", ", invalidRowNumbers) + "). أعد المعاينة والتنفيذ.");
        }
        if (rows.isEmpty()) {
            throw new BusinessRuleException("الملف لا يحتوي أي صف بيانات");
        }

        Long batchId = insertBatch(batchReference, reason, sourceReference);
        String fileHashPrefix = shortDigest(file);

        List<OpeningConsumptionImportRowResult> posted = new ArrayList<>();
        int year = referenceDate.getYear();
        LocalDate periodStart = LocalDate.of(year, 1, 1);
        LocalDate periodEnd = LocalDate.of(year, 12, 31);

        for (OpeningConsumptionImportRowResult row : rows) {
            Member member = memberRepository.findById(row.getMemberId())
                    .orElseThrow(() -> new IllegalStateException("MEMBER_VANISHED_MID_IMPORT: " + row.getMemberId()));
            BenefitPolicy policy = memberPolicyResolver.resolveFor(member, referenceDate)
                    .orElseThrow(() -> new IllegalStateException(
                            "MEMBER_POLICY_VANISHED_MID_IMPORT: " + row.getMemberId()));
            String idempotencyKey = "OPENING:" + fileHashPrefix + ":ROW:" + row.getRowNumber();
            entryWriter.appendOpeningConsumption(policy, member.getId(), batchId, null,
                    BenefitBucketConsumption.LimitScope.POLICY_GENERAL, periodStart, periodEnd,
                    row.getAmount(), row.getTimes() == null ? 0 : row.getTimes(), idempotencyKey);
            posted.add(row);
        }
        entryWriter.flush();

        log.info("Opening consumption import batch {} ({}): {} rows posted", batchReference, batchId, posted.size());

        return OpeningConsumptionImportExecuteResultDto.builder()
                .batchId(batchId)
                .batchReference(batchReference)
                .postedRows(posted.size())
                .rows(posted)
                .build();
    }

    private Long insertBatch(String batchReference, String reason, String sourceReference) {
        try {
            return jdbc.queryForObject("""
                    insert into member_opening_balance_batches (batch_reference, reason, performed_by, source_reference)
                    values (?, ?, ?, ?) returning id
                    """, Long.class, batchReference, reason, currentUsername(), sourceReference);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BusinessRuleException("مرجع الدفعة مستخدم مسبقاً: " + batchReference);
        }
    }

    private String currentUsername() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "system" : auth.getName();
    }

    private void requireReferenceDate(LocalDate referenceDate) {
        if (referenceDate == null) {
            throw new BusinessRuleException(
                    "تاريخ الرصيد الافتتاحي إلزامي: يحدد سنة السقف العام والوثيقة السارية للعضو");
        }
    }

    private List<OpeningConsumptionImportRowResult> parseRows(MultipartFile file, LocalDate referenceDate) {
        List<OpeningConsumptionImportRowResult> results = new ArrayList<>();
        try (InputStream is = new ByteArrayInputStream(file.getBytes());
                Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();
            for (int rowIndex = HEADER_ROWS; rowIndex <= lastRow; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row)) continue;
                results.add(parseRow(row, rowIndex + 1, referenceDate));
            }
        } catch (Exception e) {
            throw new BusinessRuleException("تعذّرت قراءة ملف الاستيراد: " + e.getMessage());
        }
        return results;
    }

    private OpeningConsumptionImportRowResult parseRow(Row row, int rowNumber, LocalDate referenceDate) {
        List<String> errors = new ArrayList<>();
        Long memberId = readLong(row.getCell(0));
        BigDecimal amount = readDecimal(row.getCell(1));
        Integer times = readInt(row.getCell(2));
        String note = readString(row.getCell(3));

        Member member = null;
        String memberName = null;
        if (memberId == null) {
            errors.add("معرف العضو مفقود");
        } else {
            member = memberRepository.findById(memberId).orElse(null);
            if (member == null) {
                errors.add("العضو غير موجود: " + memberId);
            } else {
                memberName = member.getFullName();
                if (memberPolicyResolver.resolveFor(member, referenceDate).isEmpty()) {
                    errors.add("لا توجد وثيقة سارية للعضو بتاريخ " + referenceDate);
                }
            }
        }

        if (amount == null || amount.signum() < 0) {
            errors.add("المبلغ مفقود أو سالب");
        }
        if (times != null && times < 0) {
            errors.add("عدد المرات لا يجوز أن يكون سالباً");
        }
        if ((amount == null || amount.signum() == 0) && (times == null || times == 0)) {
            errors.add("لا يوجد مبلغ ولا عدد مرات لهذا الصف");
        }

        return OpeningConsumptionImportRowResult.builder()
                .rowNumber(rowNumber)
                .memberId(memberId)
                .memberName(memberName)
                .amount(amount)
                .times(times)
                .sourceReference(note)
                .valid(errors.isEmpty())
                .errors(errors)
                .build();
    }

    private boolean isBlankRow(Row row) {
        return readLong(row.getCell(0)) == null && readDecimal(row.getCell(1)) == null;
    }

    private Long readLong(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                return (long) cell.getNumericCellValue();
            }
            String text = cell.toString().trim();
            return text.isEmpty() ? null : Long.parseLong(text);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal readDecimal(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            }
            String text = cell.toString().trim();
            return text.isEmpty() ? null : new BigDecimal(text);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer readInt(org.apache.poi.ss.usermodel.Cell cell) {
        BigDecimal decimal = readDecimal(cell);
        return decimal == null ? null : decimal.intValue();
    }

    private String readString(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return null;
        String text = cell.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private String shortDigest(MultipartFile file) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(file.getBytes());
            return java.util.HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (Exception e) {
            throw new BusinessRuleException("تعذّر حساب بصمة الملف");
        }
    }
}
