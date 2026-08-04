package com.waad.tba.modules.systembackup.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.systembackup.dto.BackupDtos.BackupJobDto;
import com.waad.tba.modules.systembackup.dto.BackupDtos.BackupSettingsDto;
import com.waad.tba.modules.systembackup.dto.BackupDtos.BackupStatusDto;
import com.waad.tba.modules.systembackup.dto.BackupDtos.CreateBackupRequest;
import com.waad.tba.modules.systembackup.dto.BackupDtos.PurgeResultDto;
import com.waad.tba.modules.systembackup.dto.BackupDtos.RestoreRehearsalDto;
import com.waad.tba.modules.systembackup.dto.BackupDtos.RestoreVerificationDto;
import com.waad.tba.modules.systembackup.dto.BackupDtos.ValidationResultDto;
import com.waad.tba.modules.systembackup.service.BackupRetentionService;
import com.waad.tba.modules.systembackup.service.BackupService;
import com.waad.tba.modules.systembackup.service.BackupSettingsService;
import com.waad.tba.modules.systembackup.service.RestoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * System backup administration.
 *
 * Restricted to SUPER_ADMIN: a backup archive contains the entire database and the
 * uploads tree, so download access is equivalent to full data access.
 *
 * Only non-destructive operations are exposed here. Restoring a backup into the live
 * database is intentionally NOT available from this controller.
 */
@RestController
@RequestMapping("/api/v1/system/backups")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class BackupController {

    private final BackupService backupService;
    private final BackupSettingsService settingsService;
    private final BackupRetentionService retentionService;
    private final RestoreService restoreService;

    @GetMapping("/status")
    public ApiResponse<BackupStatusDto> status() {
        return ApiResponse.success(backupService.status());
    }

    @GetMapping
    public ApiResponse<List<BackupJobDto>> list() {
        return ApiResponse.success(backupService.list());
    }

    @PostMapping
    public ApiResponse<BackupJobDto> create(@RequestBody CreateBackupRequest request,
                                            Authentication authentication) {
        BackupJobDto created = backupService.create(request.type(), request.note(), usernameOf(authentication));
        return ApiResponse.success(created, "Backup job completed", "تم تنفيذ طلب النسخ الاحتياطي");
    }

    @GetMapping("/{id}")
    public ApiResponse<BackupJobDto> get(@PathVariable Long id) {
        return ApiResponse.success(backupService.get(id));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Resource resource = backupService.download(id);
        String fileName = backupService.downloadFileName(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }

    @PostMapping("/{id}/validate")
    public ApiResponse<ValidationResultDto> validate(@PathVariable Long id) {
        return ApiResponse.success(backupService.validate(id));
    }

    /** Read-only integrity check of a stored backup. */
    @PostMapping("/{id}/verify-restore")
    public ApiResponse<RestoreVerificationDto> verifyRestore(@PathVariable Long id) {
        return ApiResponse.success(restoreService.verify(id));
    }

    /** Restore rehearsal via {@code pg_restore --list}; never touches the live database. */
    @PostMapping("/{id}/rehearse")
    public ApiResponse<RestoreRehearsalDto> rehearse(@PathVariable Long id) {
        return ApiResponse.success(restoreService.rehearse(id),
                "Restore rehearsal completed", "تم اختبار الاستعادة");
    }

    /** Defaults to a dry run so an accidental call never deletes anything. */
    @PostMapping("/purge")
    public ApiResponse<PurgeResultDto> purge(@RequestParam(defaultValue = "true") boolean dryRun,
                                             Authentication authentication) {
        PurgeResultDto result = retentionService.purge(dryRun, usernameOf(authentication));
        return ApiResponse.success(result, "Retention purge executed", result.messageAr());
    }

    @GetMapping("/settings")
    public ApiResponse<BackupSettingsDto> settings() {
        return ApiResponse.success(settingsService.getSettings());
    }

    @PutMapping("/settings")
    public ApiResponse<BackupSettingsDto> updateSettings(@RequestBody BackupSettingsDto request,
                                                         Authentication authentication) {
        return ApiResponse.success(settingsService.update(request, usernameOf(authentication)),
                "Backup settings updated", "تم حفظ إعدادات النسخ الاحتياطي");
    }

    private static String usernameOf(Authentication authentication) {
        return authentication == null ? "SYSTEM" : authentication.getName();
    }
}
