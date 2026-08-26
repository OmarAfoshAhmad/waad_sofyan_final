package com.waad.tba.modules.rbac.permission;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.modules.auth.service.SessionManagementService;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.permission.dto.PermissionOverrideRequest;
import com.waad.tba.modules.rbac.permission.dto.PermissionOverrideRequest.OverrideMode;
import com.waad.tba.modules.rbac.permission.dto.RoleTemplateUpdateRequest;
import com.waad.tba.modules.rbac.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PermissionAdministrationService {
    private final UserRepository userRepository;
    private final EffectivePermissionService effectivePermissionService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final SessionManagementService sessionManagementService;

    @Transactional(readOnly = true)
    public List<PermissionDefinition> catalogue() {
        return Arrays.stream(SystemPermission.values())
                .map(p -> new PermissionDefinition(p.name(), p.category().name(),
                        p.displayNameAr(), p.sensitive()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoleTemplate> roleTemplates() {
        return Arrays.stream(com.waad.tba.security.rbac.SystemRole.values())
                .map(role -> new RoleTemplate(role.name(), role.getDisplayNameAr(),
                        jdbcTemplate.queryForList("""
                                select permission_code from rbac_role_permissions
                                 where role_code=? order by permission_code
                                """, String.class, role.name())))
                .toList();
    }

    @Transactional
    public RoleTemplate replaceRolePermissions(String rawRoleCode, RoleTemplateUpdateRequest request) {
        User actor = requireActor();
        var actorPermissions = effectivePermissionService.resolve(actor);
        if (!actorPermissions.contains(SystemPermission.ROLE_PERMISSION_MANAGE)) {
            throw new AccessDeniedException("لا تملك صلاحية إدارة الأدوار والصلاحيات");
        }

        com.waad.tba.security.rbac.SystemRole role;
        try {
            role = com.waad.tba.security.rbac.SystemRole.valueOf(rawRoleCode.trim().toUpperCase());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("الدور غير معروف: " + rawRoleCode);
        }
        if (role == com.waad.tba.security.rbac.SystemRole.SUPER_ADMIN) {
            throw new IllegalArgumentException("قالب مدير النظام ثابت للحماية من إغلاق الإدارة بالكامل");
        }

        Set<SystemPermission> requested = new HashSet<>();
        for (String code : request.permissionCodes()) {
            SystemPermission permission = SystemPermission.parse(code);
            if (!actorPermissions.contains(permission)) {
                throw new AccessDeniedException("لا يمكن إضافة صلاحية لا يملكها المفوّض: " + permission.name());
            }
            requested.add(permission);
        }

        Set<String> previous = new HashSet<>(jdbcTemplate.queryForList(
                "select permission_code from rbac_role_permissions where role_code=?",
                String.class, role.name()));
        Set<String> desired = requested.stream().map(Enum::name)
                .collect(java.util.stream.Collectors.toSet());

        for (String removed : previous.stream().filter(code -> !desired.contains(code)).sorted().toList()) {
            jdbcTemplate.update("delete from rbac_role_permissions where role_code=? and permission_code=?",
                    role.name(), removed);
            auditRoleChange(actor, role.name(), removed, "GRANT", null, request.reason().trim());
        }
        for (String added : desired.stream().filter(code -> !previous.contains(code)).sorted().toList()) {
            jdbcTemplate.update("""
                    insert into rbac_role_permissions(role_code, permission_code, granted_by)
                    values (?, ?, ?)
                    """, role.name(), added, actor.getUsername());
            auditRoleChange(actor, role.name(), added, null, "GRANT", request.reason().trim());
        }

        List<String> affectedUsers = jdbcTemplate.queryForList(
                "select username from users where user_type=?", String.class, role.name());
        jdbcTemplate.update("update users set authorization_version=authorization_version+1 where user_type=?",
                role.name());
        revokeSessionsAfterCommit(affectedUsers);
        return new RoleTemplate(role.name(), role.getDisplayNameAr(), desired.stream().sorted().toList());
    }

    @Transactional(readOnly = true)
    public EffectivePermissionService.EffectivePermissionSnapshot effectiveFor(Long userId) {
        return effectivePermissionService.snapshot(requireUser(userId));
    }

    /** Replaces only the named overrides atomically; INHERIT removes an override. */
    @Transactional
    public EffectivePermissionService.EffectivePermissionSnapshot applyOverrides(
            Long targetUserId, List<PermissionOverrideRequest> commands) {
        if (commands == null || commands.isEmpty()) {
            throw new IllegalArgumentException("يجب تحديد تغيير صلاحية واحد على الأقل");
        }
        User actor = requireActor();
        User target = requireUser(targetUserId);
        if (actor.getId().equals(target.getId())) {
            throw new IllegalArgumentException("لا يمكن للمستخدم تعديل صلاحياته بنفسه");
        }
        if (target.isSuperAdmin()) {
            throw new IllegalArgumentException("لا تُطبّق استثناءات شخصية على مدير النظام؛ عدّل قالب الدور بسياسة مستقلة");
        }

        var actorPermissions = effectivePermissionService.resolve(actor);
        if (!actorPermissions.contains(SystemPermission.ROLE_PERMISSION_MANAGE)) {
            throw new AccessDeniedException("لا تملك صلاحية إدارة الأدوار والصلاحيات");
        }

        for (PermissionOverrideRequest command : commands) {
            SystemPermission permission = SystemPermission.parse(command.permissionCode());
            if (command.mode() == OverrideMode.GRANT && !actorPermissions.contains(permission)) {
                throw new AccessDeniedException("لا يمكن منح صلاحية لا يملكها المفوّض: " + permission.name());
            }
            applyOne(actor, target, permission, command.mode(), command.reason().trim());
        }

        target.setAuthorizationVersion(target.getAuthorizationVersion() + 1);
        userRepository.saveAndFlush(target);
        revokeSessionsAfterCommit(target.getUsername());
        return effectivePermissionService.snapshot(target);
    }

    private void revokeSessionsAfterCommit(String username) {
        revokeSessionsAfterCommit(List.of(username));
    }

    private void revokeSessionsAfterCommit(List<String> usernames) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Permission changes require an active synchronized transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                usernames.forEach(sessionManagementService::revokeAll);
            }
        });
    }

    private void auditRoleChange(User actor, String roleCode, String permissionCode,
            String previousEffect, String newEffect, String reason) {
        jdbcTemplate.update("""
                insert into rbac_permission_change_audit
                    (target_type,target_role_code,permission_code,previous_effect,new_effect,reason,actor_user_id)
                values ('ROLE',?,?,?,?,?,?)
                """, roleCode, permissionCode, previousEffect, newEffect, reason, actor.getId());
    }

    private void applyOne(User actor, User target, SystemPermission permission,
            OverrideMode mode, String reason) {
        String previous = jdbcTemplate.query("""
                select effect from rbac_user_permission_overrides
                 where user_id=? and permission_code=?
                """, rs -> rs.next() ? rs.getString(1) : null, target.getId(), permission.name());

        if (mode == OverrideMode.INHERIT) {
            jdbcTemplate.update("delete from rbac_user_permission_overrides where user_id=? and permission_code=?",
                    target.getId(), permission.name());
        } else {
            jdbcTemplate.update("""
                    insert into rbac_user_permission_overrides
                        (user_id, permission_code, effect, reason, changed_by, changed_at)
                    values (?, ?, ?, ?, ?, current_timestamp)
                    on conflict (user_id, permission_code) do update set
                        effect=excluded.effect, reason=excluded.reason,
                        changed_by=excluded.changed_by, changed_at=current_timestamp
                    """, target.getId(), permission.name(), mode.name(), reason, actor.getId());
        }

        jdbcTemplate.update("""
                insert into rbac_permission_change_audit
                    (target_type,target_user_id,permission_code,previous_effect,new_effect,reason,actor_user_id)
                values ('USER',?,?,?,?,?,?)
                """, target.getId(), permission.name(), previous,
                mode == OverrideMode.INHERIT ? null : mode.name(), reason, actor.getId());
    }

    private User requireActor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new AccessDeniedException("Authentication required");
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user no longer exists"));
    }

    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    public record PermissionDefinition(String code, String category, String displayNameAr, boolean sensitive) {}
    public record RoleTemplate(String roleCode, String displayNameAr, List<String> permissionCodes) {}
}
