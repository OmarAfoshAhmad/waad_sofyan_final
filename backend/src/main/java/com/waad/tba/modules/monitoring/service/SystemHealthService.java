package com.waad.tba.modules.monitoring.service;

import com.waad.tba.modules.monitoring.dto.MonitoringDtos.HealthCardDto;
import com.waad.tba.modules.monitoring.dto.MonitoringDtos.SystemHealthDto;
import com.waad.tba.modules.monitoring.entity.SystemMonitoringSettings;
import com.waad.tba.modules.systembackup.entity.BackupStatus;
import com.waad.tba.modules.systembackup.entity.SystemBackupJob;
import com.waad.tba.modules.systembackup.repository.SystemBackupJobRepository;
import com.waad.tba.modules.systembackup.service.BackupSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SystemHealthService {

    private final JdbcTemplate jdbcTemplate;
    private final BackupSettingsService backupSettingsService;
    private final SystemBackupJobRepository backupJobRepository;
    private final MonitoringSettingsService monitoringSettingsService;
    private final Environment environment;

    @Value("${file.storage.local.base-dir:./storage/uploads}")
    private String uploadPath;

    public SystemHealthDto fullHealth() {
        return fullHealth(monitoringSettingsService.getSchedulerSettings());
    }

    public SystemHealthDto fullHealth(SystemMonitoringSettings settings) {
        LocalDateTime now = LocalDateTime.now();
        List<HealthCardDto> cards = new ArrayList<>();
        cards.add(new HealthCardDto("backend", "Backend", "OK", "الخدمة تعمل وتستجيب للطلبات الداخلية.", null, now));
        cards.add(databaseCard(now));
        cards.add(diskCard(now, settings));
        cards.add(backupPathCard(now));
        cards.add(writablePathCard("uploadPath", "مسار المرفقات", Path.of(uploadPath).toAbsolutePath().normalize(), now, false));
        cards.add(lastBackupCard(now, settings));

        String overall = cards.stream()
                .map(HealthCardDto::status)
                .max(Comparator.comparingInt(SystemHealthService::severity))
                .orElse("UNKNOWN");

        return new SystemHealthDto(
                overall,
                activeProfile(),
                environment.getProperty("GIT_COMMIT", environment.getProperty("APP_GIT_COMMIT", "غير محدد")),
                now,
                cards
        );
    }

    private HealthCardDto databaseCard(LocalDateTime now) {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (result != null && result == 1) {
                return new HealthCardDto("database", "قاعدة البيانات", "OK", "الاتصال بقاعدة البيانات ناجح.", "SELECT 1", now);
            }
            return new HealthCardDto("database", "قاعدة البيانات", "CRITICAL", "فشل التحقق من قاعدة البيانات.", null, now);
        } catch (Exception e) {
            return new HealthCardDto("database", "قاعدة البيانات", "CRITICAL", "قاعدة البيانات لا تستجيب.", safe(e.getMessage()), now);
        }
    }

    private HealthCardDto diskCard(LocalDateTime now, SystemMonitoringSettings settings) {
        try {
            Path root = backupSettingsService.localBackupPath();
            Files.createDirectories(root);
            FileStore store = Files.getFileStore(root);
            long total = store.getTotalSpace();
            long usable = store.getUsableSpace();
            long usedPercent = total <= 0 ? 0 : Math.round(((double) (total - usable) / total) * 100);
            int warning = monitoringSettingsService.safeDiskWarningPercent(settings);
            int critical = monitoringSettingsService.safeDiskCriticalPercent(settings);
            String status = usedPercent >= critical ? "CRITICAL" : usedPercent >= warning ? "WARNING" : "OK";
            String description = usedPercent >= critical
                    ? "مساحة القرص حرجة وقد تمنع التشغيل أو النسخ الاحتياطي."
                    : usedPercent >= warning ? "مساحة القرص مرتفعة وتحتاج متابعة." : "مساحة القرص ضمن الحدود الآمنة.";
            return new HealthCardDto("disk", "مساحة القرص", status, description, "الاستخدام: " + usedPercent + "% / تحذير " + warning + "% / حرج " + critical + "%", now);
        } catch (Exception e) {
            return new HealthCardDto("disk", "مساحة القرص", "UNKNOWN", "تعذر قراءة مساحة القرص.", safe(e.getMessage()), now);
        }
    }

    private HealthCardDto writablePathCard(String key, String title, Path path, LocalDateTime now, boolean critical) {
        try {
            Files.createDirectories(path);
            Path probe = Files.createTempFile(path, ".waad-monitoring-probe", ".tmp");
            Files.deleteIfExists(probe);
            return new HealthCardDto(key, title, "OK", title + " قابل للكتابة.", path.toString(), now);
        } catch (Exception e) {
            return new HealthCardDto(key, title, critical ? "CRITICAL" : "WARNING", title + " غير قابل للكتابة.", safe(e.getMessage()), now);
        }
    }

    private HealthCardDto backupPathCard(LocalDateTime now) {
        String title = "مسار النسخ الاحتياطي";
        try {
            return writablePathCard("backupPath", title, backupSettingsService.localBackupPath(), now, true);
        } catch (Exception e) {
            return new HealthCardDto("backupPath", title, "UNKNOWN", "تعذر قراءة إعدادات مسار النسخ الاحتياطي.", safe(e.getMessage()), now);
        }
    }

    private HealthCardDto lastBackupCard(LocalDateTime now, SystemMonitoringSettings settings) {
        Optional<SystemBackupJob> latestSuccess;
        try {
            latestSuccess = backupJobRepository.findTopByStatusOrderByStartedAtDesc(BackupStatus.SUCCESS);
        } catch (Exception e) {
            return new HealthCardDto("lastBackup", "آخر نسخة احتياطية", "UNKNOWN", "تعذر قراءة سجل النسخ الاحتياطي.", safe(e.getMessage()), now);
        }
        if (latestSuccess.isEmpty()) {
            return new HealthCardDto("lastBackup", "آخر نسخة احتياطية", "UNKNOWN", "لا توجد نسخة احتياطية ناجحة مسجلة بعد.", null, now);
        }
        SystemBackupJob job = latestSuccess.get();
        LocalDateTime completedAt = job.getCompletedAt() == null ? job.getStartedAt() : job.getCompletedAt();
        long hours = Duration.between(completedAt, now).toHours();
        int maxAge = monitoringSettingsService.safeMaxBackupAgeHours(settings);
        String status = hours > maxAge ? "WARNING" : "OK";
        String description = hours > maxAge ? "آخر نسخة احتياطية ناجحة قديمة وتحتاج متابعة." : "آخر نسخة احتياطية ناجحة حديثة.";
        return new HealthCardDto("lastBackup", "آخر نسخة احتياطية", status, description, "منذ " + hours + " ساعة / الحد " + maxAge + " ساعة", now);
    }

    private String activeProfile() {
        String[] profiles = environment.getActiveProfiles();
        return profiles.length == 0 ? "local" : String.join(",", profiles);
    }

    private static int severity(String status) {
        return switch (status) {
            case "CRITICAL" -> 4;
            case "WARNING" -> 3;
            case "UNKNOWN" -> 2;
            case "OK" -> 1;
            default -> 0;
        };
    }

    private static String safe(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("(?i)(password|token|secret)=([^\\s&]+)", "$1=***");
        return cleaned.length() > 240 ? cleaned.substring(0, 240) : cleaned;
    }
}
