package com.waad.tba.modules.monitoring.service;

import com.waad.tba.modules.maintenancehub.dto.MaintenanceHubDtos.IssueRegistration;
import com.waad.tba.modules.maintenancehub.entity.IssueType;
import com.waad.tba.modules.maintenancehub.service.IssueRegistry;
import com.waad.tba.modules.systembackup.service.BackupFailureNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Bridges the backup module's alert port to Telegram and the unified maintenance ledger.
 *
 * The backup module was built (phase 1) with no dependency on any concrete alerting
 * channel — {@link BackupFailureNotifier} is the extension point. This is the only
 * class that wires it up, so backup scheduling logic never has to change regardless
 * of which channel is added or swapped later.
 */
@Component
@RequiredArgsConstructor
public class TelegramBackupFailureNotifier implements BackupFailureNotifier {

    private final TelegramAlertService telegramAlertService;
    private final IssueRegistry issueRegistry;

    @Override
    public void notifyScheduledBackupFailed(String environment, String reason) {
        // Registered unconditionally, before the Telegram call: a disabled/misconfigured
        // Telegram integration must not also mean the failure goes unrecorded in the ledger.
        // One issue per calendar day (not per reason) — a backup can fail with a different
        // transient error each night, and that should read as one ongoing problem, not many.
        issueRegistry.register(new IssueRegistration(
                IssueType.BACKUP_FAILURE,
                "BACKUP_FAILURE:" + environment + ":" + LocalDate.now(),
                null,
                "SCHEDULED_BACKUP",
                environment,
                "فشل النسخة الاحتياطية التلقائية المجدولة",
                reason,
                "HIGH",
                "BackupSchedulerService",
                Map.of("environment", environment, "reason", String.valueOf(reason))
        ));

        telegramAlertService.sendMonitoringMessage(
                "🚨 WAAD نسخ احتياطي\n"
                        + "فشلت النسخة الاحتياطية التلقائية المجدولة.\n"
                        + "البيئة: " + environment + "\n"
                        + "السبب: " + reason + "\n"
                        + "الوقت: " + LocalDateTime.now());
    }
}
