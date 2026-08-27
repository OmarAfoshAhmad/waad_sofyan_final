package com.waad.tba.modules.member.service;

import java.security.MessageDigest;
import java.sql.Array;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;

import lombok.RequiredArgsConstructor;

/** Short-lived, single-use proof that execution is exactly what was previewed. */
@Service
@RequiredArgsConstructor
public class MemberImportPreviewTicketService {
    private static final int VALIDITY_MINUTES = 15;
    private final JdbcTemplate jdbc;
    private final AuthorizationService authorizationService;
    private final ObjectMapper objectMapper;

    @Transactional
    public String issue(MultipartFile file, Long employerId, Long policyId, Integer headerRow,
            Boolean clearOldMembers, Map<String, String> mappings, Set<Long> resolvedEmployers) throws Exception {
        User user = authorizationService.requireCurrentUser();
        UUID token = UUID.randomUUID();
        Long[] employers = resolvedEmployers.stream().sorted().toArray(Long[]::new);
        String fileHash = hash(file.getBytes());
        String mappingFingerprint = mappingsHash(mappings);
        jdbc.update(con -> {
            var ps = con.prepareStatement("""
                    insert into member_import_preview_tickets(token,user_id,file_hash,default_employer_id,
                      benefit_policy_id,header_row_number,clear_old_members,custom_mappings_hash,
                      resolved_employer_ids,expires_at) values (?,?,?,?,?,?,?,?,?,?)
                    """);
            ps.setObject(1, token); ps.setLong(2, user.getId()); ps.setString(3, fileHash);
            if (employerId == null) ps.setNull(4, java.sql.Types.BIGINT); else ps.setLong(4, employerId);
            if (policyId == null) ps.setNull(5, java.sql.Types.BIGINT); else ps.setLong(5, policyId);
            if (headerRow == null) ps.setNull(6, java.sql.Types.INTEGER); else ps.setInt(6, headerRow);
            ps.setBoolean(7, Boolean.TRUE.equals(clearOldMembers)); ps.setString(8, mappingFingerprint);
            ps.setArray(9, con.createArrayOf("bigint", employers));
            ps.setObject(10, LocalDateTime.now().plusMinutes(VALIDITY_MINUTES));
            return ps;
        });
        return token.toString();
    }

    @Transactional
    public void consume(String tokenText, MultipartFile file, Long employerId, Long policyId, Integer headerRow,
            Boolean clearOldMembers, Map<String, String> mappings, Set<Long> resolvedEmployers) throws Exception {
        if (tokenText == null || tokenText.isBlank()) throw new BusinessRuleException("يجب تنفيذ معاينة صالحة أولاً");
        UUID token;
        try { token = UUID.fromString(tokenText); }
        catch (IllegalArgumentException ex) { throw new BusinessRuleException("رمز المعاينة غير صالح"); }
        User user = authorizationService.requireCurrentUser();
        var rows = jdbc.query("""
                select user_id,file_hash,default_employer_id,benefit_policy_id,header_row_number,
                       clear_old_members,custom_mappings_hash,resolved_employer_ids,expires_at,consumed_at
                  from member_import_preview_tickets where token=? for update
                """, (rs, n) -> new Ticket(rs.getLong(1), rs.getString(2), (Long)rs.getObject(3),
                        (Long)rs.getObject(4), (Integer)rs.getObject(5), rs.getBoolean(6), rs.getString(7),
                        arrayToSet(rs.getArray(8)), rs.getObject(9, LocalDateTime.class),
                        rs.getObject(10, LocalDateTime.class)), token);
        if (rows.size() != 1) throw new BusinessRuleException("رمز المعاينة غير موجود");
        Ticket t = rows.get(0);
        boolean matches = t.userId == user.getId() && t.fileHash.equals(hash(file.getBytes()))
                && java.util.Objects.equals(t.employerId, employerId)
                && java.util.Objects.equals(t.policyId, policyId)
                && java.util.Objects.equals(t.headerRow, headerRow)
                && t.clearOld == Boolean.TRUE.equals(clearOldMembers)
                && t.mappingsHash.equals(mappingsHash(mappings))
                && t.employers.equals(Set.copyOf(resolvedEmployers));
        if (t.consumedAt != null) throw new BusinessRuleException("تم استخدام معاينة الاستيراد مسبقاً");
        if (!t.expiresAt.isAfter(LocalDateTime.now())) throw new BusinessRuleException("انتهت صلاحية معاينة الاستيراد");
        if (!matches) throw new BusinessRuleException("تغيّرت بيانات الاستيراد بعد المعاينة؛ أعد المعاينة");
        jdbc.update("update member_import_preview_tickets set consumed_at=current_timestamp where token=?", token);
    }

    private String mappingsHash(Map<String, String> mappings) throws Exception {
        return hash(objectMapper.writeValueAsBytes(new TreeMap<>(mappings == null ? Map.of() : mappings)));
    }
    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static Set<Long> arrayToSet(Array array) throws java.sql.SQLException {
        if (array == null) return Set.of();
        Long[] values = (Long[]) array.getArray();
        return Set.of(values);
    }
    private record Ticket(long userId, String fileHash, Long employerId, Long policyId, Integer headerRow,
            boolean clearOld, String mappingsHash, Set<Long> employers, LocalDateTime expiresAt,
            LocalDateTime consumedAt) {}
}
