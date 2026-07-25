package com.waad.tba.common.file;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * خدمة تدقيق الوصول للملفات - File Access Audit Service
 * 
 * تسجل عمليات الوصول المسموحة والمرفوضة للمرفقات والمستندات
 * في سجل مركزي لأغراض الأمان والمراجعة.
 * 
 * العمليات المسجلة:
 * - تنزيل/معاينة مرفقات المطالبات
 * - تنزيل/معاينة مرفقات الزيارات
 * - تنزيل/معاينة مستندات مقدمي الخدمة
 * - محاولات الوصول المرفوضة (عبر Provider/Tenant مختلف)
 * - محاولات اختراق المسار (Path Traversal)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileAccessAuditService {

    /**
     * أنواع الموارد المحمية
     */
    public enum ResourceType {
        CLAIM_ATTACHMENT,
        VISIT_ATTACHMENT,
        PROVIDER_DOCUMENT,
        PREAUTH_ATTACHMENT
    }

    /**
     * العمليات على الملفات
     */
    public enum FileAction {
        DOWNLOAD,
        PREVIEW,
        DELETE,
        UPLOAD,
        LIST
    }

    /**
     * نتيجة عملية الوصول
     */
    public enum AccessOutcome {
        ALLOWED,
        DENIED_NO_AUTH,
        DENIED_WRONG_PROVIDER,
        DENIED_WRONG_TENANT,
        DENIED_WRONG_EMPLOYER,
        DENIED_INSUFFICIENT_ROLE,
        DENIED_RESOURCE_NOT_FOUND,
        DENIED_PATH_TRAVERSAL,
        ERROR
    }

    /**
     * تسجيل وصول مسموح للملف
     */
    public void logAllowedAccess(ResourceType resourceType, FileAction action,
                                  Long resourceId, Long parentEntityId) {
        String username = getCurrentUsername();
        log.info("📋 FILE_ACCESS_AUDIT | outcome=ALLOWED | user={} | resource={} | action={} | resourceId={} | parentId={}",
                username, resourceType, action, resourceId, parentEntityId);
    }

    /**
     * تسجيل وصول مرفوض للملف
     */
    public void logDeniedAccess(ResourceType resourceType, FileAction action,
                                 Long resourceId, Long parentEntityId,
                                 AccessOutcome reason) {
        String username = getCurrentUsername();
        log.warn("🚫 FILE_ACCESS_AUDIT | outcome={} | user={} | resource={} | action={} | resourceId={} | parentId={}",
                reason, username, resourceType, action, resourceId, parentEntityId);
    }

    /**
     * تسجيل وصول مرفوض مع تفاصيل إضافية (للمحاولات المشبوهة)
     */
    public void logSuspiciousAccess(ResourceType resourceType, FileAction action,
                                     Long resourceId, Long parentEntityId,
                                     AccessOutcome reason, String details) {
        String username = getCurrentUsername();
        log.error("🔴 FILE_ACCESS_AUDIT_SUSPICIOUS | outcome={} | user={} | resource={} | action={} | resourceId={} | parentId={} | details={}",
                reason, username, resourceType, action, resourceId, parentEntityId, details);
    }

    /**
     * تسجيل عملية حذف ملف (دائماً مهمة للتدقيق)
     */
    public void logFileDeletion(ResourceType resourceType, Long resourceId,
                                 Long parentEntityId, String fileName) {
        String username = getCurrentUsername();
        log.info("🗑️ FILE_DELETE_AUDIT | user={} | resource={} | resourceId={} | parentId={} | fileName={}",
                username, resourceType, resourceId, parentEntityId, fileName);
    }

    /**
     * تسجيل رفع ملف جديد
     */
    public void logFileUpload(ResourceType resourceType, Long parentEntityId,
                               String fileName, long fileSize) {
        String username = getCurrentUsername();
        log.info("📤 FILE_UPLOAD_AUDIT | user={} | resource={} | parentId={} | fileName={} | size={}",
                username, resourceType, parentEntityId, fileName, fileSize);
    }

    private String getCurrentUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                return auth.getName();
            }
        } catch (Exception e) {
            // تجاهل — سنسجل "anonymous"
        }
        return "anonymous";
    }
}
