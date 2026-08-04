package com.waad.tba.modules.monitoring.service;

import com.waad.tba.modules.systembackup.service.BackupFailureNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Bridges the backup module's alert port to Telegram.
 *
 * The backup module was built (phase 1) with no dependency on any concrete alerting
 * channel — {@link BackupFailureNotifier} is the extension point. This is the only
 * class that wires them together, so backup scheduling logic never has to change
 * regardless of which channel is added or swapped later.
 */
@Component
@RequiredArgsConstructor
public class TelegramBackupFailureNotifier implements BackupFailureNotifier {

    private final TelegramAlertService telegramAlertService;

    @Override
    public void notifyScheduledBackupFailed(String environment, String reason) {
        telegramAlertService.sendMonitoringMessage(
                "🚨 WAAD نسخ احتياطي\n"
                        + "فشلت النسخة الاحتياطية التلقائية المجدولة.\n"
                        + "البيئة: " + environment + "\n"
                        + "السبب: " + reason + "\n"
                        + "الوقت: " + LocalDateTime.now());
    }
}
