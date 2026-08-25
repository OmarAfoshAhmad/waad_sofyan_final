package com.waad.tba.modules.rbac.permission;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.rbac.SystemRole;

import lombok.RequiredArgsConstructor;

/**
 * The only calculator of effective permissions.
 * Effective = persisted role template + explicit grants - explicit revocations.
 */
@Service
@RequiredArgsConstructor
public class EffectivePermissionService {
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public Set<SystemPermission> resolve(User user) {
        if (user == null || user.getId() == null || !Boolean.TRUE.equals(user.getActive())) {
            return Set.of();
        }
        SystemRole role = SystemRole.fromString(user.getUserType());
        if (role == null) return Set.of();

        EnumSet<SystemPermission> effective = EnumSet.noneOf(SystemPermission.class);
        rolePermissionCodes(role.name()).stream().map(SystemPermission::parse).forEach(effective::add);
        overrideRows(user.getId()).forEach(row -> {
            SystemPermission permission = SystemPermission.parse(row.permissionCode());
            if (row.effect() == PermissionEffect.REVOKE) effective.remove(permission);
            else effective.add(permission);
        });
        return Set.copyOf(effective);
    }

    @Transactional(readOnly = true)
    public EffectivePermissionSnapshot snapshot(User user) {
        Set<SystemPermission> rolePermissions = EnumSet.noneOf(SystemPermission.class);
        SystemRole role = SystemRole.fromString(user.getUserType());
        if (role != null) rolePermissionCodes(role.name()).stream()
                .map(SystemPermission::parse).forEach(rolePermissions::add);
        List<PermissionOverrideRow> overrides = overrideRows(user.getId());
        Set<SystemPermission> effective = resolve(user);
        return new EffectivePermissionSnapshot(Set.copyOf(rolePermissions), overrides, effective);
    }

    private List<String> rolePermissionCodes(String role) {
        return jdbcTemplate.queryForList("""
                select rp.permission_code
                  from rbac_role_permissions rp
                  join rbac_permissions p on p.code = rp.permission_code and p.active = true
                 where rp.role_code = ?
                """, String.class, role);
    }

    private List<PermissionOverrideRow> overrideRows(Long userId) {
        return jdbcTemplate.query("""
                select permission_code, effect, reason
                  from rbac_user_permission_overrides
                 where user_id = ?
                """, (rs, row) -> new PermissionOverrideRow(
                        rs.getString("permission_code"),
                        PermissionEffect.valueOf(rs.getString("effect")),
                        rs.getString("reason")), userId);
    }

    public record PermissionOverrideRow(String permissionCode, PermissionEffect effect, String reason) {}
    public record EffectivePermissionSnapshot(Set<SystemPermission> rolePermissions,
            List<PermissionOverrideRow> overrides, Set<SystemPermission> effectivePermissions) {}
}
