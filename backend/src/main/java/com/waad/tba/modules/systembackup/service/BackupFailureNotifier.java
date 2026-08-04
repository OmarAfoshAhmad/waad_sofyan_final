package com.waad.tba.modules.systembackup.service;

/**
 * Notified when a scheduled backup fails.
 *
 * The backup module deliberately does not depend on any concrete alerting channel.
 * The reference implementation called a Telegram service directly from the scheduler,
 * which welded the two modules together; here the channel is a plug-in point, so the
 * monitoring/Telegram work can supply an implementation without the scheduler changing.
 *
 * Implementations must never throw — a broken alert channel must not break backups.
 */
public interface BackupFailureNotifier {

    void notifyScheduledBackupFailed(String environment, String reason);
}
