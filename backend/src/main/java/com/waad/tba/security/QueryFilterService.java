package com.waad.tba.security;

import com.waad.tba.modules.rbac.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryFilterService {

    private final RoleService roleService;

    public Long getEmployerFilterForUser(User user) {
        if (user == null) {
            log.warn("⚠️ getEmployerFilterForUser: user is null, returning null filter");
            return null;
        }

        if (roleService.isSuperAdmin(user)) {
            log.debug("🔓 getEmployerFilterForUser: user={} is SUPER_ADMIN - NO FILTER", user.getUsername());
            return null;
        }

        if (roleService.isInsuranceAdmin(user)) {
            log.debug("🔓 getEmployerFilterForUser: user={} is INSURANCE_ADMIN - NO FILTER", user.getUsername());
            return null;
        }

        if (roleService.isEmployerAdmin(user)) {
            Long employerId = user.getEmployerId();
            if (employerId == null) {
                log.warn("⚠️ getEmployerFilterForUser: EMPLOYER_ADMIN user={} has no employerId!", user.getUsername());
            } else {
                log.debug("🔒 getEmployerFilterForUser: user={} filtered by employerId={}", user.getUsername(), employerId);
            }
            return employerId;
        }

        log.debug("🔓 getEmployerFilterForUser: user={} has other role - NO FILTER", user.getUsername());
        return null;
    }

    public Long getProviderFilterForUser(User user) {
        if (user == null) {
            log.warn("⚠️ getProviderFilterForUser: user is null, returning null filter");
            return null;
        }

        if (roleService.isSuperAdmin(user)) {
            log.debug("🔓 getProviderFilterForUser: user={} is SUPER_ADMIN - NO FILTER", user.getUsername());
            return null;
        }

        if (roleService.isInsuranceAdmin(user)) {
            log.debug("🔓 getProviderFilterForUser: user={} is INSURANCE_ADMIN - NO FILTER", user.getUsername());
            return null;
        }

        if (roleService.isProvider(user)) {
            Long providerId = user.getProviderId();
            if (providerId == null) {
                log.warn("⚠️ getProviderFilterForUser: PROVIDER user={} has no providerId!", user.getUsername());
            } else {
                log.debug("🔒 getProviderFilterForUser: user={} filtered by providerId={}", user.getUsername(), providerId);
            }
            return providerId;
        }

        log.debug("🔓 getProviderFilterForUser: user={} has other role - NO FILTER", user.getUsername());
        return null;
    }

    public Long resolveEmployerScope(User user, Long requestedEmployerId) {
        if (user != null && roleService.isEmployerAdmin(user)) {
            return user.getEmployerId();
        }
        return requestedEmployerId;
    }

    public Long resolveProviderScope(User user, Long requestedProviderId) {
        if (user != null && roleService.isProvider(user)) {
            return user.getProviderId();
        }
        return requestedProviderId;
    }
}
