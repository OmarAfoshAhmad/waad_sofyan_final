package com.waad.tba.modules.benefitpolicy.service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.dto.BenefitOpeningBalanceImportDto;
import com.waad.tba.modules.benefitpolicy.dto.BenefitOpeningBalanceImportDto.Preview;
import com.waad.tba.modules.benefitpolicy.dto.BenefitOpeningBalanceImportDto.Result;
import com.waad.tba.modules.benefitpolicy.entity.*;
import com.waad.tba.modules.benefitpolicy.repository.*;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.security.AuthorizationService;

import lombok.RequiredArgsConstructor;

/** Imports non-financial opening usage into benefit buckets after full preview validation. */
@Service
@RequiredArgsConstructor
public class BenefitOpeningBalanceImportService {
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");
    private static final Set<String> SHEET_NAMES = Set.of("الارصدة الافتتاحية", "ارصدة افتتاحية", "opening balances");

    private final MemberRepository memberRepository;
    private final BenefitPolicyRepository policyRepository;
    private final BenefitLimitBucketRepository bucketRepository;
    private final BenefitBucketAdjustmentRepository adjustmentRepository;
    private final BenefitBucketUsageService usageService;
    private final AuthorizationService authorizationService;

    @Transactional(readOnly = true)
    public Preview preview(MultipartFile file, Long policyId, String requestedBatchId) throws Exception {
        String batchId = requestedBatchId == null || requestedBatchId.isBlank()
                ? UUID.randomUUID().toString() : requestedBatchId.trim();
        BenefitPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new BusinessRuleException("وثيقة المنافع غير موجودة"));
        List<BenefitOpeningBalanceImportDto.Row> rows = readAndValidate(file, policy);
        int valid = (int) rows.stream().filter(BenefitOpeningBalanceImportDto.Row::valid).count();
        int duplicate = (int) rows.stream().filter(BenefitOpeningBalanceImportDto.Row::alreadyImported).count();
        BigDecimal total = rows.stream().filter(BenefitOpeningBalanceImportDto.Row::valid).filter(r -> !r.alreadyImported())
                .map(BenefitOpeningBalanceImportDto.Row::usedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Preview(batchId, rows.size(), valid, rows.size() - valid, duplicate, total, rows);
    }

    @Transactional
    public Result execute(MultipartFile file, Long policyId, String batchId) throws Exception {
        Preview preview = preview(file, policyId, batchId);
        if (preview.invalidRows() > 0) {
            throw new BusinessRuleException("تعذر التنفيذ: توجد " + preview.invalidRows() + " صفوف غير صالحة");
        }
        BenefitPolicy policy = policyRepository.findById(policyId).orElseThrow();
        Long userId = authorizationService.getCurrentUser() == null
                ? null : authorizationService.getCurrentUser().getId();
        int imported = 0;
        int skipped = 0;
        BigDecimal importedAmount = BigDecimal.ZERO;
        for (BenefitOpeningBalanceImportDto.Row row : preview.rows()) {
            if (row.alreadyImported()) { skipped++; continue; }
            Member member = findMember(row.cardNumber());
            BenefitLimitBucket bucket = bucketRepository.findByPolicyIdAndCodeIgnoreCase(policyId, row.bucketCode())
                    .orElseThrow();
            String key = idempotencyKey(policyId, member.getId(), bucket.getId(), row.periodStart(), row.periodEnd());
            if (adjustmentRepository.existsByIdempotencyKey(key)) { skipped++; continue; }
            adjustmentRepository.save(BenefitBucketAdjustment.builder()
                    .memberId(member.getId()).policy(policy).bucket(bucket)
                    .periodStart(row.periodStart()).periodEnd(row.periodEnd())
                    .amountDelta(row.usedAmount()).timesDelta(row.usedTimes()).daysDelta(row.usedDays())
                    .adjustmentType(BenefitBucketAdjustment.AdjustmentType.OPENING_BALANCE)
                    .status(BenefitBucketAdjustment.Status.ACTIVE)
                    .sourceBatchId(preview.batchId()).sourceReference(row.sourceReference())
                    .reason("رصيد استهلاك افتتاحي مرحّل من النظام السابق")
                    .idempotencyKey(key).createdByUserId(userId).build());
            imported++;
            importedAmount = importedAmount.add(row.usedAmount());
        }
        return new Result(preview.batchId(), preview.totalRows(), imported, skipped, importedAmount);
    }

    private List<BenefitOpeningBalanceImportDto.Row> readAndValidate(MultipartFile file, BenefitPolicy policy) throws Exception {
        try (InputStream in = file.getInputStream(); Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = findSheet(workbook);
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) throw new BusinessRuleException("ورقة الأرصدة الافتتاحية بلا رؤوس أعمدة");
            Map<String, Integer> columns = columns(header);
            require(columns, "card_number", "bucket_code", "as_of_date", "amount_limit", "remaining_amount");
            List<BenefitOpeningBalanceImportDto.Row> result = new ArrayList<>();
            Set<String> keysInFile = new HashSet<>();
            for (int i = header.getRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                org.apache.poi.ss.usermodel.Row excelRow = sheet.getRow(i);
                if (excelRow == null || empty(excelRow)) continue;
                result.add(validateRow(excelRow, i + 1, columns, policy, keysInFile));
            }
            return result;
        }
    }

    private BenefitOpeningBalanceImportDto.Row validateRow(org.apache.poi.ss.usermodel.Row row, int rowNumber, Map<String, Integer> columns,
                            BenefitPolicy policy, Set<String> keysInFile) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String card = value(row, columns, "card_number");
        String bucketCode = value(row, columns, "bucket_code");
        LocalDate asOf = date(value(row, columns, "as_of_date"), errors, "تاريخ الرصيد");
        BigDecimal statedLimit = decimal(value(row, columns, "amount_limit"), errors, "السقف الأصلي");
        BigDecimal remaining = decimal(value(row, columns, "remaining_amount"), errors, "الرصيد المتبقي");
        BigDecimal explicitUsed = optionalDecimal(value(row, columns, "used_amount"), errors, "المبلغ المستخدم");
        Integer usedTimes = optionalInteger(value(row, columns, "used_times"), errors, "عدد المرات");
        Integer usedDays = optionalInteger(value(row, columns, "used_days"), errors, "عدد الأيام");
        String sourceReference = value(row, columns, "source_reference");
        if (card.isBlank()) errors.add("رقم البطاقة مطلوب");
        if (bucketCode.isBlank()) errors.add("رمز السقف مطلوب");

        Member member = null;
        BenefitLimitBucket bucket = null;
        if (!card.isBlank()) {
            try { member = findMember(card); }
            catch (BusinessRuleException ex) { errors.add(ex.getMessage()); }
        }
        if (!bucketCode.isBlank()) {
            bucket = bucketRepository.findByPolicyIdAndCodeIgnoreCase(policy.getId(), bucketCode).orElse(null);
            if (bucket == null) errors.add("رمز السقف غير موجود في الوثيقة: " + bucketCode);
            else if (!bucket.isActive()) errors.add("السقف غير نشط: " + bucketCode);
        }
        if (member != null && (member.getBenefitPolicy() == null
                || !policy.getId().equals(member.getBenefitPolicy().getId()))) {
            errors.add("المستفيد غير مرتبط بالوثيقة المختارة");
        }

        BigDecimal used = BigDecimal.ZERO;
        if (statedLimit != null && remaining != null) {
            used = statedLimit.subtract(remaining).setScale(2, RoundingMode.HALF_UP);
            if (used.signum() < 0) errors.add("الرصيد المتبقي أكبر من السقف الأصلي");
            if (explicitUsed != null && used.subtract(explicitUsed).abs().compareTo(TOLERANCE) > 0)
                errors.add("المبلغ المستخدم لا يساوي السقف الأصلي ناقص المتبقي");
        }
        LocalDate periodStart = null;
        LocalDate periodEnd = null;
        if (bucket != null && asOf != null) {
            BucketPeriodCalculator.Period period = BucketPeriodCalculator.resolve(bucket, policy, asOf);
            periodStart = period.start(); periodEnd = period.end();
            if (bucket.getAmountLimit() != null && statedLimit != null
                    && bucket.getAmountLimit().subtract(statedLimit).abs().compareTo(TOLERANCE) > 0)
                errors.add("السقف الأصلي في الملف لا يطابق سقف الوثيقة");
            if (bucket.getAmountLimit() != null && used.compareTo(bucket.getAmountLimit()) > 0)
                errors.add("الاستهلاك الافتتاحي يتجاوز سقف الوثيقة");
        }

        boolean alreadyImported = false;
        if (member != null && bucket != null && periodStart != null) {
            String key = idempotencyKey(policy.getId(), member.getId(), bucket.getId(), periodStart, periodEnd);
            if (!keysInFile.add(key)) errors.add("سجل مكرر داخل الملف لنفس المستفيد والسقف والفترة");
            alreadyImported = adjustmentRepository.existsByIdempotencyKey(key);
            if (alreadyImported) warnings.add("الرصيد الافتتاحي مستورد سابقًا وسيتم تخطيه");
            BenefitBucketUsageService.UsageTotals current = usageService.totals(
                    member.getId(), bucket.getId(), periodStart, periodEnd, null);
            if (!alreadyImported && bucket.getAmountLimit() != null
                    && current.amount().add(used).compareTo(bucket.getAmountLimit()) > 0)
                errors.add("الاستهلاك الحالي مع الرصيد الافتتاحي يتجاوز السقف؛ يحتمل تكرار ترحيل مطالبات قديمة");
        }
        return new BenefitOpeningBalanceImportDto.Row(rowNumber, card, member == null ? null : member.getFullName(), bucketCode,
                bucket == null ? null : bucket.getNameAr(), periodStart, periodEnd, statedLimit, used,
                remaining, usedTimes == null ? 0 : usedTimes, usedDays == null ? 0 : usedDays,
                sourceReference.isBlank() ? "Excel row " + rowNumber : sourceReference,
                alreadyImported, errors, warnings);
    }

    private Member findMember(String card) {
        return memberRepository.findByCardNumber(card.trim())
                .or(() -> memberRepository.findByCardNumberIgnoreHyphens(card.replace("-", "").trim()))
                .orElseThrow(() -> new BusinessRuleException("رقم البطاقة غير موجود: " + card));
    }

    private String idempotencyKey(Long policyId, Long memberId, Long bucketId, LocalDate start, LocalDate end) {
        return "OPENING:P" + policyId + ":M" + memberId + ":B" + bucketId + ":" + start + ":" + (end == null ? "OPEN" : end);
    }

    private Sheet findSheet(Workbook workbook) {
        for (Sheet sheet : workbook) {
            if (SHEET_NAMES.contains(normalize(sheet.getSheetName()))) return sheet;
        }
        throw new BusinessRuleException("ورقة الأرصدة الافتتاحية غير موجودة");
    }

    private Map<String, Integer> columns(org.apache.poi.ss.usermodel.Row header) {
        Map<String, Integer> result = new HashMap<>();
        for (Cell cell : header) {
            String raw = normalize(cell.toString());
            String key = switch (raw) {
                case "رقم البطاقة", "card number", "card_number" -> "card_number";
                case "رمز السقف", "bucket code", "bucket_code" -> "bucket_code";
                case "تاريخ الرصيد", "as of date", "as_of_date" -> "as_of_date";
                case "السقف الأصلي", "amount limit", "amount_limit" -> "amount_limit";
                case "المبلغ المستخدم", "used amount", "used_amount" -> "used_amount";
                case "الرصيد المتبقي", "remaining amount", "remaining_amount" -> "remaining_amount";
                case "عدد المرات", "used times", "used_times" -> "used_times";
                case "عدد الأيام", "used days", "used_days" -> "used_days";
                case "مرجع المصدر", "source reference", "source_reference" -> "source_reference";
                default -> null;
            };
            if (key != null) result.put(key, cell.getColumnIndex());
        }
        return result;
    }

    private void require(Map<String, Integer> columns, String... required) {
        for (String key : required) if (!columns.containsKey(key))
            throw new BusinessRuleException("عمود مطلوب غير موجود: " + key);
    }

    private String value(org.apache.poi.ss.usermodel.Row row, Map<String, Integer> columns, String key) {
        Integer index = columns.get(key); if (index == null) return "";
        Cell cell = row.getCell(index); if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell))
            return cell.getLocalDateTimeCellValue().toLocalDate().toString();
        if (cell.getCellType() == CellType.NUMERIC)
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        return cell.toString().trim();
    }

    private LocalDate date(String raw, List<String> errors, String label) {
        if (raw == null || raw.isBlank()) { errors.add(label + " مطلوب"); return null; }
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd-MM-yyyy"), DateTimeFormatter.ofPattern("dd/MM/yyyy"))) {
            try { return LocalDate.parse(raw, formatter); } catch (DateTimeParseException ignored) {}
        }
        errors.add(label + " غير صالح: " + raw); return null;
    }

    private BigDecimal decimal(String raw, List<String> errors, String label) {
        BigDecimal value = optionalDecimal(raw, errors, label);
        if (value == null) errors.add(label + " مطلوب");
        return value;
    }

    private BigDecimal optionalDecimal(String raw, List<String> errors, String label) {
        if (raw == null || raw.isBlank()) return null;
        try { BigDecimal value = new BigDecimal(raw.replace(",", ""));
            if (value.signum() < 0) errors.add(label + " لا يمكن أن يكون سالبًا"); return value;
        } catch (NumberFormatException ex) { errors.add(label + " غير صالح: " + raw); return null; }
    }

    private Integer optionalInteger(String raw, List<String> errors, String label) {
        if (raw == null || raw.isBlank()) return null;
        try { int value = new BigDecimal(raw).intValueExact();
            if (value < 0) errors.add(label + " لا يمكن أن يكون سالبًا"); return value;
        } catch (ArithmeticException ex) { errors.add(label + " غير صالح: " + raw); return null; }
    }

    private boolean empty(org.apache.poi.ss.usermodel.Row row) {
        for (Cell cell : row) if (!cell.toString().isBlank()) return false; return true;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase().replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا');
    }
}
