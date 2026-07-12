package com.waad.tba.modules.notification.controller;

import com.waad.tba.modules.notification.service.NotificationSseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * واجهة SSE — يُتيح للواجهة الأمامية الاشتراك في الإشعارات الفورية.
 *
 * الاستخدام في الواجهة الأمامية (React):
 * <pre>
 *   const eventSource = new EventSource('/api/v1/notifications/stream', {
 *       withCredentials: true
 *   });
 *   eventSource.addEventListener('notification', (e) => {
 *       const data = JSON.parse(e.data);
 *       handleNotification(data);
 *   });
 * </pre>
 *
 * الحدث الأول المُرسَل هو "connected" للتأكيد.
 * بعدها تأتي أحداث "notification" عند وصول موافقات جديدة.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Notifications SSE", description = "إشعارات فورية للمراجعين عبر Server-Sent Events")
public class NotificationSseController {

    private final NotificationSseService sseService;

    /**
     * نقطة اشتراك SSE — يبقى الاتصال مفتوحاً لاستقبال الإشعارات.
     * يجب أن يكون المستخدم مسجّلاً (isAuthenticated).
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "الاشتراك في الإشعارات الفورية",
            description = "يُفتح اتصال SSE دائم. ترسل الخدمة حدث 'connected' فور الاتصال، " +
                    "ثم أحداث 'notification' عند وصول موافقات مسبقة جديدة أو تغيُّر حالتها."
    )
    public SseEmitter stream(@AuthenticationPrincipal UserDetails user) {
        log.info("[SSE] New connection request from user: {}", user.getUsername());
        return sseService.register(user.getUsername());
    }

    /**
     * API مراقبة — عدد المستخدمين المتصلين حالياً.
     * مفيد لـ DevOps والمراقبة.
     */
    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'INSURANCE_ADMIN')")
    @Operation(summary = "حالة اتصالات SSE", description = "يُرجع عدد المستخدمين المتصلين حالياً بنظام الإشعارات")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "connectedUsers", sseService.getConnectedUsersCount(),
                "status", "active"
        ));
    }
}
