package com.waad.tba.modules.member.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.member.security.MemberCommandAccessPolicy;
import com.waad.tba.modules.member.security.MemberOperation;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.systemadmin.service.AuditLogService;
import com.waad.tba.security.AuthorizationService;

import lombok.RequiredArgsConstructor;

/** Atomic system-wide kinship maintenance with a durable audit record. */
@Service
@RequiredArgsConstructor
public class MemberKinshipAdminService {

    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;
    private final AuthorizationService authorizationService;
    private final MemberCommandAccessPolicy commandAccessPolicy;

    @Transactional
    public int resetVerification(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("إعادة تعيين التحقق من القرابة تتطلب سبباً صريحاً.");
        }
        // System-wide operations do not belong to one employer. The policy
        // treats the employer argument as irrelevant for RESET_KINSHIP and
        // requires SUPER_ADMIN explicitly.
        commandAccessPolicy.require(MemberOperation.RESET_KINSHIP, null);
        User actor = authorizationService.getCurrentUser();

        int updated = jdbcTemplate.update(
                "UPDATE members SET kinship_verified = false WHERE relationship IS NOT NULL");

        auditLogService.createAuditLog(
                "RESET_KINSHIP", "SYSTEM_SETTING", null,
                "إعادة تعيين التحقق من القرابة لعدد " + updated + " مستفيد. السبب: " + reason.trim(),
                actor == null ? null : actor.getId(),
                actor == null ? null : actor.getUsername(), null, null);
        return updated;
    }
}
