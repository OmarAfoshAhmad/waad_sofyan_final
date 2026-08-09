package com.waad.tba.security;

import com.waad.tba.modules.rbac.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RoleService {

    public boolean isSuperAdmin(User user) {
        if (user == null || user.getUserType() == null) {
            return false;
        }
        return "SUPER_ADMIN".equals(user.getUserType());
    }

    public boolean isEmployerAdmin(User user) {
        if (user == null || user.getUserType() == null) {
            return false;
        }
        return "EMPLOYER_ADMIN".equals(user.getUserType());
    }

    public boolean isProvider(User user) {
        if (user == null || user.getUserType() == null) {
            return false;
        }
        return "PROVIDER_STAFF".equals(user.getUserType());
    }

    public boolean isReviewer(User user) {
        if (user == null || user.getUserType() == null) {
            return false;
        }
        return "MEDICAL_REVIEWER".equals(user.getUserType())
                || "MEDICAL_REVIEW_HEAD".equals(user.getUserType())
                || "INSURANCE_MANAGER".equals(user.getUserType());
    }

    public boolean isDataEntry(User user) {
        if (user == null || user.getUserType() == null) {
            return false;
        }
        return "DATA_ENTRY".equals(user.getUserType());
    }

    public boolean isFinancialUser(User user) {
        if (user == null || user.getUserType() == null) {
            return false;
        }
        return "ACCOUNTANT".equals(user.getUserType()) || "FINANCE_VIEWER".equals(user.getUserType());
    }

    public boolean canAccessInternalOperations(User user) {
        return isSuperAdmin(user) || isReviewer(user) || isDataEntry(user);
    }

    public boolean isInternalStaff(User user) {
        return canAccessInternalOperations(user) || isFinancialUser(user);
    }
}
