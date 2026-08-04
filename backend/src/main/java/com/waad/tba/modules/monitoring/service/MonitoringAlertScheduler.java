package com.waad.tba.modules.monitoring.service;

import com.waad.tba.modules.monitoring.dto.MonitoringDtos.HealthCardDto;
import com.waad.tba.modules.monitoring.dto.MonitoringDtos.SystemHealthDto;
import com.waad.tba.modules.monitoring.entity.MonitoringAlertStatus;
import com.waad.tba.modules.monitoring.entity.SystemMonitoringAlertState;
import com.waad.tba.modules.monitoring.entity.SystemMonitoringSettings;
import com.waad.tba.modules.monitoring.repository.SystemMonitoringAlertStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class MonitoringAlertScheduler {

    private final MonitoringSettingsService settingsService;
    private final SystemHealthService systemHealthService;
    private final TelegramAlertService telegramAlertService;
    private final SystemMonitoringAlertStateRepository stateRepository;
    private final ErrorRateMonitor errorRateMonitor;
    private final Map<String, SystemMonitoringAlertState> fallbackState = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${waad.monitoring.scheduler.fixed-delay-ms:30000}")
    public void scheduledCheck() {
        try {
            runAutomaticCheck(false);
        } catch (Exception e) {
            log.warn("[MON-1C] Monitoring scheduler failed safely: {}", e.getMessage());
        }
    }

    @Transactional
    public void runAutomaticCheck(boolean force) {
        SystemMonitoringSettings settings = settingsService.getSchedulerSettings();
        if (!Boolean.TRUE.equals(settings.getAutomaticMonitoringEnabled())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (!force && settings.getLastAutoCheckAt() != null) {
            long seconds = Duration.between(settings.getLastAutoCheckAt(), now).getSeconds();
            if (seconds < settingsService.safeCheckIntervalSeconds(settings)) {
                return;
            }
        }

        SystemHealthDto health = systemHealthService.fullHealth(settings);
        Map<String, HealthCardDto> cards = new HashMap<>();
        for (HealthCardDto card : health.cards()) {
            cards.put(card.key(), card);
        }

        List<RuleEvaluation> rules = new ArrayList<>();
        addCardRule(rules, "DATABASE", "قاعدة البيانات", cards.get("database"));
        addCardRule(rules, "DISK", "مساحة القرص", cards.get("disk"));
        addCardRule(rules, "BACKUP_PATH", "مسار النسخ الاحتياطي", cards.get("backupPath"));
        addCardRule(rules, "UPLOAD_PATH", "مسار المرفقات", cards.get("uploadPath"));
        addCardRule(rules, "LAST_BACKUP", "آخر نسخة احتياطية", cards.get("lastBackup"));
        rules.add(errorRateRule(settings));

        for (RuleEvaluation rule : rules) {
            try {
                processRule(settings, rule, now);
            } catch (Exception e) {
                log.warn("[MON-1C] Monitoring rule {} failed safely: {}", rule.key(), e.getMessage());
            }
        }

        try {
            errorRateMonitor.purgeOldEvents();
        } catch (Exception e) {
            log.warn("[MON-1C] Monitoring error cleanup failed safely: {}", e.getMessage());
        }
        settingsService.recordAutoCheck("SUCCESS", "تم تنفيذ فحص المراقبة التلقائي", now);
    }

    private void addCardRule(List<RuleEvaluation> rules, String key, String title, HealthCardDto card) {
        if (card == null) {
            rules.add(new RuleEvaluation(key, title, MonitoringAlertStatus.UNKNOWN, "تعذر قراءة حالة " + title, null));
            return;
        }
        rules.add(new RuleEvaluation(key, title, parseStatus(card.status()), card.descriptionAr(), card.details()));
    }

    private RuleEvaluation errorRateRule(SystemMonitoringSettings settings) {
        int windowMinutes = settingsService.safeRepeatedErrorWindowMinutes(settings);
        int threshold = settingsService.safeRepeatedErrorThreshold(settings);
        long count;
        try {
            count = errorRateMonitor.countRecentErrors(windowMinutes);
        } catch (Exception e) {
            return new RuleEvaluation(
                    "REPEATED_ERRORS",
                    "أخطاء النظام المتكررة",
                    MonitoringAlertStatus.UNKNOWN,
                    "تعذر قراءة سجل أخطاء النظام المتكررة.",
                    e.getMessage()
            );
        }
        if (count >= threshold) {
            return new RuleEvaluation(
                    "REPEATED_ERRORS",
                    "أخطاء النظام المتكررة",
                    MonitoringAlertStatus.CRITICAL,
                    "عدد أخطاء النظام تجاوز الحد المسموح خلال النافذة الزمنية.",
                    count + " خطأ خلال " + windowMinutes + " دقيقة"
            );
        }
        return new RuleEvaluation(
                "REPEATED_ERRORS",
                "أخطاء النظام المتكررة",
                MonitoringAlertStatus.HEALTHY,
                "معدل أخطاء النظام ضمن الحدود الآمنة.",
                count + " / " + threshold
        );
    }

    private void processRule(SystemMonitoringSettings settings, RuleEvaluation rule, LocalDateTime now) {
        SystemMonitoringAlertState state = loadState(rule.key(), now);

        MonitoringAlertStatus previous = state.getStatus();
        int previousSeverity = state.getSeverity() == null ? 0 : state.getSeverity();
        int currentSeverity = severity(rule.status());
        boolean unhealthy = currentSeverity > 0;
        boolean wasUnhealthy = previousSeverity > 0;

        if (unhealthy) {
            if (!wasUnhealthy || state.getFirstDetectedAt() == null) {
                state.setFirstDetectedAt(now);
            }
            state.setLastDetectedAt(now);
        }
        state.setStatus(rule.status());
        state.setSeverity(currentSeverity);
        state.setLastSummary(rule.summary());
        state.setUpdatedAt(now);

        boolean shouldAlert = unhealthy && (!wasUnhealthy || currentSeverity > previousSeverity || cooldownExpired(settings, state, now));
        boolean shouldRecover = !unhealthy && wasUnhealthy && Boolean.TRUE.equals(settings.getRecoveryEnabled());

        if (shouldAlert) {
            if (safeSend(settings, buildAlertMessage(settings, rule, now))) {
                state.setLastSentAt(now);
                state.setAlertCount((state.getAlertCount() == null ? 0 : state.getAlertCount()) + 1);
            }
        } else if (shouldRecover) {
            boolean sent = safeSend(settings, buildRecoveryMessage(settings, rule, state, now));
            state.setRecoveredAt(now);
            if (sent) {
                state.setLastSentAt(now);
                state.setAlertCount((state.getAlertCount() == null ? 0 : state.getAlertCount()) + 1);
            }
            state.setFirstDetectedAt(null);
        }

        saveState(state);
    }

    private SystemMonitoringAlertState loadState(String key, LocalDateTime now) {
        try {
            SystemMonitoringAlertState state = stateRepository.findById(key).orElse(null);
            if (state != null) {
                fallbackState.put(key, state);
                return state;
            }
        } catch (Exception e) {
            log.warn("[MON-1C] Alert state repository unavailable for {}. Using in-memory fallback.", key);
            SystemMonitoringAlertState fallback = fallbackState.get(key);
            if (fallback != null) {
                return fallback;
            }
        }
        return SystemMonitoringAlertState.builder()
                .alertKey(key)
                .status(MonitoringAlertStatus.HEALTHY)
                .severity(0)
                .updatedAt(now)
                .build();
    }

    private void saveState(SystemMonitoringAlertState state) {
        fallbackState.put(state.getAlertKey(), state);
        try {
            SystemMonitoringAlertState saved = stateRepository.save(state);
            fallbackState.put(saved.getAlertKey(), saved);
        } catch (Exception e) {
            log.warn("[MON-1C] Alert state repository save failed safely for {}. In-memory state retained.", state.getAlertKey());
        }
    }

    private boolean cooldownExpired(SystemMonitoringSettings settings, SystemMonitoringAlertState state, LocalDateTime now) {
        if (state.getLastSentAt() == null) {
            return true;
        }
        return Duration.between(state.getLastSentAt(), now).getSeconds() >= settingsService.safeAlertCooldownSeconds(settings);
    }

    private boolean safeSend(SystemMonitoringSettings settings, String message) {
        try {
            telegramAlertService.sendMonitoringMessage(settings, message);
            return true;
        } catch (Exception e) {
            log.warn("[MON-1C] Telegram alert failed safely: {}", e.getMessage());
            return false;
        }
    }

    private String buildAlertMessage(SystemMonitoringSettings settings, RuleEvaluation rule, LocalDateTime now) {
        String icon = rule.status() == MonitoringAlertStatus.CRITICAL ? "🚨" : "⚠️";
        return """
                %s WAAD تنبيه مراقبة
                القاعدة: %s
                الحالة: %s
                البيئة: %s
                الوقت: %s
                التفاصيل: %s
                %s
                """.formatted(icon, rule.title(), arabicStatus(rule.status()), settings.getAlertEnvironment(), now, rule.summary(), rule.details() == null ? "" : "معلومات آمنة: " + rule.details());
    }

    private String buildRecoveryMessage(SystemMonitoringSettings settings, RuleEvaluation rule, SystemMonitoringAlertState state, LocalDateTime now) {
        String duration = state.getFirstDetectedAt() == null ? "غير محددة" : Duration.between(state.getFirstDetectedAt(), now).toMinutes() + " دقيقة";
        return """
                ✅ WAAD تعافى
                القاعدة: %s
                الحالة: عادت إلى الوضع السليم
                البيئة: %s
                مدة المشكلة: %s
                الوقت: %s
                """.formatted(rule.title(), settings.getAlertEnvironment(), duration, now);
    }

    private static MonitoringAlertStatus parseStatus(String status) {
        if ("CRITICAL".equals(status)) {
            return MonitoringAlertStatus.CRITICAL;
        }
        if ("WARNING".equals(status)) {
            return MonitoringAlertStatus.WARNING;
        }
        if ("OK".equals(status)) {
            return MonitoringAlertStatus.HEALTHY;
        }
        return MonitoringAlertStatus.UNKNOWN;
    }

    private static int severity(MonitoringAlertStatus status) {
        return switch (status) {
            case CRITICAL -> 3;
            case WARNING -> 2;
            case UNKNOWN -> 1;
            case HEALTHY -> 0;
        };
    }

    private static String arabicStatus(MonitoringAlertStatus status) {
        return switch (status) {
            case CRITICAL -> "خطأ حرج";
            case WARNING -> "تحذير";
            case UNKNOWN -> "غير معروف";
            case HEALTHY -> "سليم";
        };
    }

    public record RuleEvaluation(String key, String title, MonitoringAlertStatus status, String summary, String details) {
    }
}
