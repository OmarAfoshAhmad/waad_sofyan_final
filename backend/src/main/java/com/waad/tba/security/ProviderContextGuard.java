package com.waad.tba.security;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.rbac.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * ════════════════════════════════════════════════════════════════════════════════════════
 * PROVIDER CONTEXT GUARD - إدارة سياق مقدم الخدمة
 * ════════════════════════════════════════════════════════════════════════════════════════
 * 
 * مكون أساسي لضمان عزل بيانات مقدمي الخدمة بشكل صارم.
 * 
 * القواعد الأساسية:
 * 1. مستخدم PROVIDER يجب أن يكون مرتبطاً بـ providerId واحد فقط
 * 2. الربط يتم عند إنشاء المستخدم ولا يتغير لاحقاً
 * 3. جميع العمليات تستخدم providerId من الـ Security Context فقط
 * 4. منع نهائي لأي providerId قادم من الـ request لمستخدم PROVIDER
 * 
 * ════════════════════════════════════════════════════════════════════════════════════════
 * @author TBA WAAD System
 * @version 1.0 - Provider Security Hardening (2026-01-16)
 * ════════════════════════════════════════════════════════════════════════════════════════
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProviderContextGuard {

    private final AuthorizationService authorizationService;

    /**
     * التحقق من أن مستخدم PROVIDER لديه providerId مرتبط.
     * يجب استدعاء هذه الدالة عند Login وعند كل عملية.
     * 
     * @param user المستخدم الحالي
     * @throws AccessDeniedException إذا كان المستخدم PROVIDER بدون providerId
     */
    public void validateProviderBinding(User user) {
        if (user == null) {
            throw new AccessDeniedException("لا يوجد مستخدم مسجل الدخول / No authenticated user");
        }

        if (authorizationService.isProvider(user)) {
            if (user.getProviderId() == null) {
                log.error("❌ PROVIDER_BINDING_ERROR: User {} has PROVIDER role but no providerId", 
                    user.getUsername());
                throw new BusinessRuleException(
                    "مستخدم مقدم الخدمة غير مرتبط بمقدم خدمة. يرجى التواصل مع مدير النظام. / " +
                    "Provider user is not linked to a provider. Please contact system administrator. " +
                    "[Error: PROVIDER_NOT_LINKED]"
                );
            }
            log.debug("✅ Provider binding validated: user={}, providerId={}", 
                user.getUsername(), user.getProviderId());
        }
    }

    /**
     * الحصول على providerId للمستخدم الحالي مع التحقق.
     * للاستخدام في الـ Services لضمان عزل البيانات.
     * 
     * @return providerId المرتبط بالمستخدم، أو null للمستخدمين غير PROVIDER
     * @throws AccessDeniedException إذا كان المستخدم PROVIDER بدون providerId
     */
    public Long getRequiredProviderId() {
        User currentUser = authorizationService.getCurrentUser();
        
        if (currentUser == null) {
            throw new AccessDeniedException("لا يوجد مستخدم مسجل الدخول / No authenticated user");
        }

        if (authorizationService.isProvider(currentUser)) {
            validateProviderBinding(currentUser);
            return currentUser.getProviderId();
        }

        // Non-provider users don't have provider binding
        return null;
    }

    /**
     * الحصول على providerId للمستخدم الحالي مع التحقق الصارم.
     * يُستخدم في العمليات التي تتطلب providerId إلزامي (مثل إنشاء زيارة).
     * 
     * @return providerId المرتبط بالمستخدم
     * @throws AccessDeniedException إذا لم يكن المستخدم PROVIDER أو لم يكن لديه providerId
     */
    public Long getRequiredProviderIdStrict() {
        User currentUser = authorizationService.getCurrentUser();
        
        if (currentUser == null) {
            throw new AccessDeniedException("لا يوجد مستخدم مسجل الدخول / No authenticated user");
        }

        if (!authorizationService.isProvider(currentUser)) {
            throw new AccessDeniedException(
                "هذه العملية متاحة لمقدمي الخدمة فقط / This operation is available for providers only"
            );
        }

        validateProviderBinding(currentUser);
        return currentUser.getProviderId();
    }

    /**
     * التحقق من أن providerId المطلوب يتطابق مع providerId المستخدم الحالي.
     * يُستخدم عند محاولة الوصول إلى بيانات مقدم خدمة محدد.
     * 
     * @param requestedProviderId الـ providerId المطلوب الوصول إليه
     * @throws AccessDeniedException إذا كان المستخدم PROVIDER ويحاول الوصول لـ provider آخر
     */
    public void validateProviderAccess(Long requestedProviderId) {
        User currentUser = authorizationService.getCurrentUser();
        
        if (currentUser == null) {
            throw new AccessDeniedException("لا يوجد مستخدم مسجل الدخول / No authenticated user");
        }

        // Non-provider users: Admin roles have full access
        if (!authorizationService.isProvider(currentUser)) {
            return; // Allow access
        }

        // Provider users: Must match their providerId
        validateProviderBinding(currentUser);
        Long userProviderId = currentUser.getProviderId();

        if (requestedProviderId != null && !requestedProviderId.equals(userProviderId)) {
            log.error("❌ PROVIDER_ACCESS_VIOLATION: User {} (providerId={}) attempted to access providerId={}", 
                currentUser.getUsername(), userProviderId, requestedProviderId);
            throw new AccessDeniedException(
                "لا يمكن الوصول إلى بيانات مقدم خدمة آخر / Cannot access another provider's data"
            );
        }
    }

    /**
     * فرض providerId على DTO بناءً على سياق المستخدم.
     * يُستخدم عند إنشاء زيارة أو مطالبة.
     * 
     * ARCHITECTURAL LAW:
     * - PROVIDER users: providerId يؤخذ من الـ Session فقط، يُتجاهل أي قيمة من الـ request
     * - ADMIN users: يمكن تحديد أي providerId (مطلوب)
     * 
     * @param requestedProviderId الـ providerId القادم من الـ request
     * @return providerId المناسب للاستخدام
     */
    public Long enforceProviderId(Long requestedProviderId) {
        User currentUser = authorizationService.getCurrentUser();
        
        if (currentUser == null) {
            throw new AccessDeniedException("لا يوجد مستخدم مسجل الدخول / No authenticated user");
        }

        // PROVIDER users: ALWAYS use their own providerId
        if (authorizationService.isProvider(currentUser)) {
            validateProviderBinding(currentUser);
            Long userProviderId = currentUser.getProviderId();
            
            // Log warning if request contained different providerId
            if (requestedProviderId != null && !requestedProviderId.equals(userProviderId)) {
                log.warn("⚠️ PROVIDER_ID_OVERRIDE: User {} requested providerId={} but enforced to {}", 
                    currentUser.getUsername(), requestedProviderId, userProviderId);
            }
            
            return userProviderId;
        }

        // Internal operations users: Must provide a providerId
        if (authorizationService.canAccessInternalOperations(currentUser)) {
            if (requestedProviderId == null) {
                throw new IllegalArgumentException(
                    "يجب تحديد مقدم الخدمة للمستخدمين الداخليين / Provider ID is required for internal users"
                );
            }
            return requestedProviderId;
        }

        // Other users: Use requested providerId or throw error
        if (requestedProviderId == null) {
            throw new IllegalArgumentException(
                "يجب تحديد مقدم الخدمة / Provider ID is required"
            );
        }
        return requestedProviderId;
    }

    /**
     * التحقق من أن المستخدم الحالي يمكنه إنشاء بيانات لمقدم خدمة محدد.
     * 
     * @param targetProviderId الـ providerId المراد إنشاء بيانات له
     * @return true إذا كان مسموحاً
     */
    public boolean canCreateForProvider(Long targetProviderId) {
        User currentUser = authorizationService.getCurrentUser();
        
        if (currentUser == null) {
            return false;
        }

        // Internal operations users can create for any provider
        if (authorizationService.canAccessInternalOperations(currentUser)) {
            return true;
        }

        // PROVIDER can only create for their own provider
        if (authorizationService.isProvider(currentUser)) {
            Long userProviderId = currentUser.getProviderId();
            return userProviderId != null && userProviderId.equals(targetProviderId);
        }

        return false;
    }

    /**
     * الحصول على فلتر providerId للاستعلامات.
     * 
     * @return providerId للفلترة، أو null إذا لم يكن هناك فلترة (للأدمن)
     */
    public Long getProviderFilter() {
        User currentUser = authorizationService.getCurrentUser();
        
        if (currentUser == null) {
            return null;
        }

        // Internal operations/financial users see all provider-scoped data when allowed by endpoint/service role checks.
        if (authorizationService.canAccessInternalOperations(currentUser) || authorizationService.isFinancialUser(currentUser)) {
            return null;
        }

        // PROVIDER users are filtered by their providerId
        if (authorizationService.isProvider(currentUser)) {
            validateProviderBinding(currentUser);
            return currentUser.getProviderId();
        }

        return null;
    }

    /**
     * التحقق السريع: هل المستخدم الحالي هو PROVIDER؟
     */
    public boolean isCurrentUserProvider() {
        User currentUser = authorizationService.getCurrentUser();
        return currentUser != null && authorizationService.isProvider(currentUser);
    }

    /**
     * التحقق السريع: هل المستخدم الحالي مستخدم داخلي تشغيلي؟
     */
    public boolean isCurrentUserAdmin() {
        User currentUser = authorizationService.getCurrentUser();
        return currentUser != null && authorizationService.canAccessInternalOperations(currentUser);
    }
}
