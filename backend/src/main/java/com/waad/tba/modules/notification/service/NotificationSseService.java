package com.waad.tba.modules.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.waad.tba.modules.notification.dto.NotificationPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * خدمة Server-Sent Events لإرسال إشعارات فورية للمراجعين.
 *
 * المبدأ:
 *   - كل مراجع متصل يُسجَّل Emitter خاص به.
 *   - عند وصول موافقة مسبقة جديدة، يُرسَل إشعار لجميع المراجعين المعيّنين للمزود.
 *   - عند انتهاء الاتصال (close/timeout)، يُحذف الـ Emitter تلقائياً.
 *
 * الخيط الآمن (Thread-Safe):
 *   - ConcurrentHashMap لتخزين Emitters.
 *   - CopyOnWriteArraySet لقائمة مراجعي كل مزود.
 */
@Service
@Slf4j
public class NotificationSseService {

    /**
     * Map: username → SseEmitter
     * كل مستخدم له Emitter واحد (الاتصال الأحدث يستبدل القديم).
     */
    private final Map<String, SseEmitter> userEmitters = new ConcurrentHashMap<>();

    /**
     * Map: providerId → Set<username>
     * يُستخدم لإيجاد المراجعين المعيّنين لمزود معين بسرعة.
     */
    private final Map<Long, Set<String>> providerReviewers = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    // ═══════════════════════════════════════════════════════════════════════
    // تسجيل / إلغاء تسجيل المراجع
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * يُسجّل SseEmitter لمستخدم معين عند اتصاله.
     */
    public SseEmitter register(String username) {
        SseEmitter emitter = new SseEmitter(0L); // 0 = لا timeout

        // تنظيف عند انتهاء الاتصال
        emitter.onCompletion(() -> {
            log.debug("[SSE] Emitter completed for user: {}", username);
            userEmitters.remove(username);
        });
        emitter.onTimeout(() -> {
            log.debug("[SSE] Emitter timed out for user: {}", username);
            userEmitters.remove(username);
            emitter.complete();
        });
        emitter.onError(ex -> {
            log.warn("[SSE] Emitter error for user {}: {}", username, ex.getMessage());
            userEmitters.remove(username);
        });

        // إرسال حدث ترحيب للتأكيد على الاتصال
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"status\":\"connected\",\"user\":\"" + username + "\"}"));
        } catch (IOException e) {
            log.warn("[SSE] Could not send welcome event to {}", username);
        }

        userEmitters.put(username, emitter);
        log.info("[SSE] ✅ Registered emitter for user: {} (total connected: {})",
                username, userEmitters.size());

        return emitter;
    }

    /**
     * يُسجّل ارتباط مراجع بمزود خدمة (يُستدعى عند تعيين مراجع).
     */
    public void assignReviewerToProvider(String username, Long providerId) {
        providerReviewers
                .computeIfAbsent(providerId, k -> new CopyOnWriteArraySet<>())
                .add(username);
        log.debug("[SSE] Reviewer {} assigned to provider {}", username, providerId);
    }

    /**
     * يُزيل ارتباط مراجع بمزود.
     */
    public void removeReviewerFromProvider(String username, Long providerId) {
        Set<String> reviewers = providerReviewers.get(providerId);
        if (reviewers != null) {
            reviewers.remove(username);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // إرسال الإشعارات
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * يُرسل إشعاراً لمستخدم محدد بالاسم.
     */
    public void notifyUser(String username, NotificationPayload payload) {
        SseEmitter emitter = userEmitters.get(username);
        if (emitter == null) {
            log.debug("[SSE] User {} not connected, skipping notification", username);
            return;
        }

        try {
            String data = objectMapper.writeValueAsString(payload);
            emitter.send(SseEmitter.event()
                    .name("notification")
                    .data(data));
            log.debug("[SSE] ✅ Sent notification to user {}: type={}", username, payload.getType());
        } catch (IOException e) {
            log.warn("[SSE] Failed to send to {}: {}. Removing emitter.", username, e.getMessage());
            userEmitters.remove(username);
        }
    }

    /**
     * يُرسل إشعاراً لجميع المراجعين المعيّنين لمزود معين.
     * يُستدعى عند إرسال موافقة مسبقة جديدة من المزود.
     */
    public void notifyReviewersForProvider(Long providerId, NotificationPayload payload) {
        Set<String> reviewers = providerReviewers.get(providerId);

        if (reviewers == null || reviewers.isEmpty()) {
            log.debug("[SSE] No reviewers registered for provider {}. Using broadcast.", providerId);
            // إذا لم يُعيَّن مراجع محدد، أرسل لجميع المتصلين (fallback)
            broadcastToAll(payload);
            return;
        }

        log.info("[SSE] Notifying {} reviewer(s) for provider {}: type={}",
                reviewers.size(), providerId, payload.getType());

        reviewers.forEach(reviewer -> notifyUser(reviewer, payload));
    }

    /**
     * إرسال إشعار لجميع المستخدمين المتصلين حالياً.
     * يُستخدم كـ fallback عند عدم تعيين مراجع محدد.
     */
    public void broadcastToAll(NotificationPayload payload) {
        if (userEmitters.isEmpty()) {
            log.debug("[SSE] No connected users for broadcast.");
            return;
        }
        log.info("[SSE] Broadcasting to {} connected user(s): type={}", userEmitters.size(), payload.getType());
        userEmitters.keySet().forEach(username -> notifyUser(username, payload));
    }

    /**
     * عدد المستخدمين المتصلين حالياً (للمراقبة).
     */
    public int getConnectedUsersCount() {
        return userEmitters.size();
    }
}
