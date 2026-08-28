package com.waad.tba.modules.benefitpolicy.service;

import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;

import lombok.RequiredArgsConstructor;

/**
 * Short-lived, single-use proof that an opening-consumption import's execute
 * step is exactly what its preview showed -- same file, same reference date,
 * same user. Mirrors {@code MemberImportPreviewTicketService} (V186); a
 * separate table because this import's parameters are its own shape, not the
 * member import's employer/policy/clearOldMembers set.
 */
@Service
@RequiredArgsConstructor
public class OpeningConsumptionImportPreviewTicketService {
    private static final int VALIDITY_MINUTES = 15;
    private final JdbcTemplate jdbc;
    private final AuthorizationService authorizationService;

    @Transactional
    public String issue(MultipartFile file, LocalDate referenceDate) throws Exception {
        User user = authorizationService.requireCurrentUser();
        UUID token = UUID.randomUUID();
        String fileHash = hash(file.getBytes());
        jdbc.update("""
                insert into opening_consumption_import_preview_tickets
                    (token, user_id, file_hash, reference_date, expires_at)
                values (?, ?, ?, ?, ?)
                """,
                token, user.getId(), fileHash, referenceDate,
                LocalDateTime.now().plusMinutes(VALIDITY_MINUTES));
        return token.toString();
    }

    @Transactional
    public void consume(String tokenText, MultipartFile file, LocalDate referenceDate) throws Exception {
        if (tokenText == null || tokenText.isBlank()) {
            throw new BusinessRuleException("يجب تنفيذ معاينة صالحة أولاً");
        }
        UUID token;
        try {
            token = UUID.fromString(tokenText);
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("رمز المعاينة غير صالح");
        }
        User user = authorizationService.requireCurrentUser();
        var rows = jdbc.query("""
                select user_id, file_hash, reference_date, expires_at, consumed_at
                  from opening_consumption_import_preview_tickets where token = ? for update
                """, (rs, n) -> new Ticket(rs.getLong(1), rs.getString(2),
                        rs.getObject(3, LocalDate.class), rs.getObject(4, LocalDateTime.class),
                        rs.getObject(5, LocalDateTime.class)), token);
        if (rows.size() != 1) throw new BusinessRuleException("رمز المعاينة غير موجود");
        Ticket t = rows.get(0);
        boolean matches = t.userId == user.getId()
                && t.fileHash.equals(hash(file.getBytes()))
                && t.referenceDate.equals(referenceDate);
        if (t.consumedAt != null) throw new BusinessRuleException("تم استخدام معاينة الاستيراد مسبقاً");
        if (!t.expiresAt.isAfter(LocalDateTime.now())) throw new BusinessRuleException("انتهت صلاحية معاينة الاستيراد");
        if (!matches) throw new BusinessRuleException("تغيّرت بيانات الاستيراد بعد المعاينة؛ أعد المعاينة");
        jdbc.update("update opening_consumption_import_preview_tickets set consumed_at = current_timestamp where token = ?",
                token);
    }

    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private record Ticket(long userId, String fileHash, LocalDate referenceDate,
            LocalDateTime expiresAt, LocalDateTime consumedAt) {}
}
