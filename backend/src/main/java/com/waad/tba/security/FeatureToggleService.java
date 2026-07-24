package com.waad.tba.security;

import com.waad.tba.modules.rbac.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureToggleService {

    private final RoleService roleService;

    public boolean canEmployerViewMembers(User user) {
        if (user == null) {
            log.warn("⚠️ FeatureCheck: user=null feature=VIEW_MEMBERS result=DENIED (null user)");
            return false;
        }

        if (roleService.isSuperAdmin(user) || roleService.isInsuranceAdmin(user)) {
            log.debug("✅ FeatureCheck: user={} feature=VIEW_MEMBERS result=ALLOWED (admin role)", user.getUsername());
            return true;
        }

        if (!roleService.isEmployerAdmin(user)) {
            log.debug("✅ FeatureCheck: user={} feature=VIEW_MEMBERS result=ALLOWED (not EMPLOYER_ADMIN)",
                user.getUsername());
            return true;
        }

        if (user.getEmployerId() == null) {
            log.warn("❌ FeatureCheck: user={} feature=VIEW_MEMBERS result=DENIED (no employerId)",
                user.getUsername());
            return false;
        }

        Boolean canViewMembers = user.getCanViewMembers();
        boolean result = canViewMembers == null || canViewMembers;

        log.info("🔧 FeatureCheck: employerId={} user={} feature=VIEW_MEMBERS result={}",
            user.getEmployerId(), user.getUsername(), result ? "ALLOWED" : "DENIED");

        return result;
    }

    public boolean canEmployerViewBenefitPolicies(User user) {
        if (user == null) {
            log.warn("⚠️ FeatureCheck: user=null feature=VIEW_BENEFIT_POLICIES result=DENIED (null user)");
            return false;
        }

        if (roleService.isSuperAdmin(user) || roleService.isInsuranceAdmin(user)) {
            log.debug("✅ FeatureCheck: user={} feature=VIEW_BENEFIT_POLICIES result=ALLOWED (admin role)", user.getUsername());
            return true;
        }

        if (!roleService.isEmployerAdmin(user)) {
            log.debug("✅ FeatureCheck: user={} feature=VIEW_BENEFIT_POLICIES result=ALLOWED (not EMPLOYER_ADMIN)",
                user.getUsername());
            return true;
        }

        if (user.getEmployerId() == null) {
            log.warn("❌ FeatureCheck: user={} feature=VIEW_BENEFIT_POLICIES result=DENIED (no employerId)",
                user.getUsername());
            return false;
        }

        Boolean canViewBenefitPolicies = user.getCanViewBenefitPolicies();
        boolean result = canViewBenefitPolicies == null || canViewBenefitPolicies;

        log.info("🔧 FeatureCheck: employerId={} user={} feature=VIEW_BENEFIT_POLICIES result={}",
            user.getEmployerId(), user.getUsername(), result ? "ALLOWED" : "DENIED");

        return result;
    }
}
